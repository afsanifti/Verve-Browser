package com.verve.browser.data.download

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import com.verve.browser.data.network.QuicNetworkManager
import com.verve.browser.presentation.downloads.DownloadItem
import com.verve.browser.presentation.downloads.DownloadStatus
import com.verve.browser.presentation.downloads.inferDownloadFileType
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo

internal class DownloadNetworkExecutor(
    private val onUpdateItem: (String, (DownloadItem) -> DownloadItem) -> Unit,
    private val onRemoveItem: (String) -> Unit,
    private val onRecordFailed: (fileName: String, sourceUrl: String, errorMessage: String, isPostRequest: Boolean) -> DownloadItem,
    private val onEnqueueFallback: (
        context: Context,
        url: String,
        userAgent: String?,
        refererUrl: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) -> DownloadItem
) {
    companion object {
        private const val BUFFER_SIZE_BYTES = 64 * 1024
        private const val UI_THROTTLE_MS = 150L
        private const val MAX_REDIRECTS = 10
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()
    private val activeRequests = ConcurrentHashMap<String, UrlRequest>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val canceledIds = ConcurrentHashMap.newKeySet<String>()

    fun pauseRequest(itemId: String) {
        pausedIds.add(itemId)
        activeRequests.remove(itemId)?.cancel()
    }

    fun cancelRequest(itemId: String) {
        canceledIds.add(itemId)
        pausedIds.remove(itemId)
        activeRequests.remove(itemId)?.cancel()
    }

    fun clearTransientFlags(itemId: String) {
        pausedIds.remove(itemId)
        canceledIds.remove(itemId)
    }

    fun fetchPdfForInlinePreview(
        appContext: Context,
        url: String,
        refererUrl: String?,
        suggestedFileName: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        onInlinePdfReady: (Uri, String) -> Unit,
        onFallbackToDownload: (DownloadItem) -> Unit
    ) {
        executor.execute {
            var responseError: String? = null
            var tempOutputFile: File? = null

            val cachedPdfResult = runCatching {
                val connection = openAuthenticatedConnection(
                    initialUrl = url,
                    userAgent = userAgent,
                    refererUrl = refererUrl,
                    existingBytes = 0L
                )
                val statusCode = connection.responseCode
                val responseMime = connection.contentType.orEmpty()
                val responseDisposition = connection.getHeaderField("Content-Disposition")
                if (statusCode !in 200..299) {
                    connection.disconnect()
                    responseError = "Server returned HTTP $statusCode"
                    return@runCatching null
                }

                val earlyMismatch = DownloadResponseValidator.validateExpectedTypeVsResponseMime(
                    expectedFileName = suggestedFileName,
                    expectedMimeType = mimeType ?: "application/pdf",
                    responseMimeType = responseMime
                )
                if (earlyMismatch != null) {
                    connection.disconnect()
                    responseError = earlyMismatch
                    return@runCatching null
                }

                val resolvedName = DownloadResponseValidator.resolveFinalFileName(
                    fallbackName = suggestedFileName,
                    finalUrl = connection.url?.toString() ?: url,
                    responseDisposition = responseDisposition,
                    responseMimeType = responseMime
                )

                val cacheDir = File(appContext.cacheDir, "inline_pdfs").apply { mkdirs() }
                val outFile = File(cacheDir, "${System.currentTimeMillis()}_$resolvedName")
                tempOutputFile = outFile

                val headerSample = ByteArrayOutputStream(HEADER_SAMPLE_LIMIT_BYTES)
                var totalWritten = 0L
                connection.inputStream.use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (headerSample.size() < HEADER_SAMPLE_LIMIT_BYTES) {
                                val toCapture = minOf(read, HEADER_SAMPLE_LIMIT_BYTES - headerSample.size())
                                headerSample.write(buffer, 0, toCapture)
                            }
                            output.write(buffer, 0, read)
                            totalWritten += read
                        }
                        output.flush()
                    }
                }
                connection.disconnect()

                val validation = DownloadResponseValidator.validateResponsePayload(
                    statusCode = statusCode,
                    expectedFileName = resolvedName,
                    responseMimeType = responseMime,
                    leadingBytes = headerSample.toByteArray(),
                    totalBytesWritten = totalWritten
                )
                if (validation != null) {
                    runCatching { outFile.delete() }
                    responseError = validation
                    return@runCatching null
                }

                Uri.fromFile(outFile) to resolvedName
            }.getOrElse {
                tempOutputFile?.let { file -> runCatching { file.delete() } }
                null
            }

            mainHandler.post {
                if (cachedPdfResult != null) {
                    onInlinePdfReady(cachedPdfResult.first, cachedPdfResult.second)
                } else if (responseError != null) {
                    val failedItem = onRecordFailed(
                        suggestedFileName,
                        url,
                        responseError!!,
                        false
                    )
                    onFallbackToDownload(failedItem)
                } else {
                    val item = onEnqueueFallback(
                        appContext,
                        url,
                        userAgent,
                        refererUrl,
                        contentDisposition,
                        mimeType,
                        contentLength
                    )
                    onFallbackToDownload(item)
                }
            }
        }
    }

    fun startOrResumeRequest(
        appContext: Context,
        item: DownloadItem,
        targetUrl: String,
        refererUrl: String?,
        userAgent: String?,
        redirectCount: Int
    ) {
        val contentUri = item.contentUri?.let(Uri::parse) ?: return
        val existingBytes = DownloadMediaStoreHelper.getCurrentDownloadedSize(appContext, contentUri)
        val cronetEngine = QuicNetworkManager.getCronetEngine(appContext)
        val cookieHeader = resolveCookiesForUrl(targetUrl)

        if (cronetEngine == null) {
            executor.execute {
                downloadWithFallbackConnection(
                    appContext = appContext,
                    item = item,
                    contentUri = contentUri,
                    existingBytes = existingBytes,
                    userAgent = userAgent,
                    refererUrl = refererUrl
                )
            }
            return
        }

        val callback = CronetFileDownloadCallback(
            appContext = appContext,
            item = item,
            currentHopUrl = targetUrl,
            currentCookieHeader = cookieHeader,
            refererUrl = refererUrl,
            userAgent = userAgent,
            redirectCount = redirectCount,
            contentUri = contentUri,
            initialOffsetBytes = existingBytes
        )

        val requestBuilder = cronetEngine.newUrlRequestBuilder(targetUrl, callback, executor)
            .setHttpMethod("GET")
            .allowDirectExecutor()

        if (!userAgent.isNullOrBlank()) {
            requestBuilder.addHeader("User-Agent", userAgent)
        }
        if (!cookieHeader.isNullOrBlank()) {
            requestBuilder.addHeader("Cookie", cookieHeader)
        }
        if (!refererUrl.isNullOrBlank()) {
            requestBuilder.addHeader("Referer", refererUrl)
        }
        if (existingBytes > 0L) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        val request = requestBuilder.build()
        activeRequests[item.id] = request
        request.start()
    }

    private fun resolveCookiesForUrl(url: String): String? {
        return runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
            cookieManager.getCookie(url)
        }.getOrNull()
    }

    private inner class CronetFileDownloadCallback(
        private val appContext: Context,
        private val item: DownloadItem,
        private val currentHopUrl: String,
        private val currentCookieHeader: String?,
        private val refererUrl: String?,
        private val userAgent: String?,
        private val redirectCount: Int,
        private val contentUri: Uri,
        private val initialOffsetBytes: Long
    ) : UrlRequest.Callback() {

        private val itemId = item.id
        private var writableSink: WritableDownloadSink? = null
        private var downloadedBytes: Long = initialOffsetBytes
        private var totalBytes: Long = 0L
        private var negotiatedProtocol: String? = null
        private var isRestartingForRedirect = false
        private var abortedEarly = false
        private var responseStatusCode: Int = 200
        private var responseMimeType: String? = null
        private var resolvedFileName: String = item.fileName
        private val headerSample = ByteArrayOutputStream(HEADER_SAMPLE_LIMIT_BYTES)

        private var lastUiUpdateMs = 0L
        private var speedWindowStartMs = SystemClock.elapsedRealtime()
        private var speedWindowStartBytes = initialOffsetBytes

        override fun onRedirectReceived(
            request: UrlRequest,
            info: UrlResponseInfo,
            newLocationUrl: String
        ) {
            if (canceledIds.contains(itemId) || pausedIds.contains(itemId)) {
                request.cancel()
                return
            }

            if (redirectCount >= MAX_REDIRECTS) {
                abortedEarly = true
                request.cancel()
                DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                onUpdateItem(itemId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        contentUri = null,
                        errorMessage = "Too many redirects",
                        remainingSeconds = null
                    )
                }
                return
            }

            val resolvedNextUrl = runCatching {
                URL(URL(currentHopUrl), newLocationUrl).toString()
            }.getOrDefault(newLocationUrl)
            val nextHopCookies = resolveCookiesForUrl(resolvedNextUrl)

            if (nextHopCookies != currentCookieHeader || resolvedNextUrl != newLocationUrl) {
                isRestartingForRedirect = true
                request.cancel()
                startOrResumeRequest(
                    appContext = appContext,
                    item = item,
                    targetUrl = resolvedNextUrl,
                    refererUrl = currentHopUrl,
                    userAgent = userAgent,
                    redirectCount = redirectCount + 1
                )
            } else {
                request.followRedirect()
            }
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            if (canceledIds.contains(itemId) || pausedIds.contains(itemId)) {
                request.cancel()
                return
            }

            responseStatusCode = info.httpStatusCode
            if (responseStatusCode !in 200..299) {
                abortedEarly = true
                request.cancel()
                DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                onUpdateItem(itemId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        contentUri = null,
                        downloadedBytes = 0L,
                        remainingSeconds = null,
                        errorMessage = "Server returned HTTP $responseStatusCode"
                    )
                }
                return
            }

            responseMimeType = info.allHeaders["Content-Type"]?.firstOrNull()
            val earlyMismatchError = DownloadResponseValidator.validateExpectedTypeVsResponseMime(
                expectedFileName = item.fileName,
                expectedMimeType = item.mimeType,
                responseMimeType = responseMimeType
            )
            if (earlyMismatchError != null) {
                abortedEarly = true
                request.cancel()
                DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                onUpdateItem(itemId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        contentUri = null,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        remainingSeconds = null,
                        errorMessage = earlyMismatchError
                    )
                }
                return
            }

            negotiatedProtocol = info.negotiatedProtocol
            val isPartialContent = responseStatusCode == 206 && initialOffsetBytes > 0L
            if (!isPartialContent) {
                downloadedBytes = 0L
                speedWindowStartBytes = 0L
                headerSample.reset()
            }

            val responseDisposition = info.allHeaders["Content-Disposition"]?.firstOrNull()
            val updatedName = DownloadResponseValidator.resolveFinalFileName(
                fallbackName = item.fileName,
                finalUrl = info.url ?: currentHopUrl,
                responseDisposition = responseDisposition,
                responseMimeType = responseMimeType
            )
            val updatedMime = DownloadResponseValidator.resolveMimeType(
                updatedName,
                responseMimeType ?: item.mimeType
            )
            resolvedFileName = DownloadMediaStoreHelper.updateMetadata(
                context = appContext,
                contentUri = contentUri,
                newDisplayName = updatedName,
                newMimeType = updatedMime
            )

            val contentLength = info.allHeaders["Content-Length"]
                ?.firstOrNull()
                ?.toLongOrNull()
                ?: -1L

            totalBytes = when {
                isPartialContent && contentLength > 0L -> initialOffsetBytes + contentLength
                contentLength > 0L -> contentLength
                else -> 0L
            }

            writableSink = DownloadMediaStoreHelper.openWritableSink(
                context = appContext,
                contentUri = contentUri,
                append = isPartialContent,
                offsetBytes = initialOffsetBytes
            )

            onUpdateItem(itemId) {
                it.copy(
                    fileName = resolvedFileName,
                    mimeType = updatedMime,
                    fileType = inferDownloadFileType(resolvedFileName, updatedMime),
                    status = DownloadStatus.DOWNLOADING,
                    downloadedBytes = downloadedBytes,
                    totalBytes = if (totalBytes > 0L) totalBytes else it.totalBytes,
                    negotiatedProtocol = negotiatedProtocol
                )
            }

            request.read(ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES))
        }

        override fun onReadCompleted(
            request: UrlRequest,
            info: UrlResponseInfo,
            byteBuffer: ByteBuffer
        ) {
            if (canceledIds.contains(itemId) || pausedIds.contains(itemId)) {
                request.cancel()
                return
            }

            byteBuffer.flip()
            val bytesWritten = byteBuffer.remaining()
            if (headerSample.size() < HEADER_SAMPLE_LIMIT_BYTES && bytesWritten > 0) {
                val duplicate = byteBuffer.duplicate()
                val toCapture = minOf(bytesWritten, HEADER_SAMPLE_LIMIT_BYTES - headerSample.size())
                val temp = ByteArray(toCapture)
                duplicate.get(temp)
                headerSample.write(temp)
            }

            while (byteBuffer.hasRemaining()) {
                writableSink?.channel?.write(byteBuffer)
            }
            downloadedBytes += bytesWritten

            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastUiUpdateMs >= UI_THROTTLE_MS) {
                lastUiUpdateMs = nowMs
                val etaSeconds = computeRemainingSeconds(nowMs)
                onUpdateItem(itemId) {
                    it.copy(
                        downloadedBytes = downloadedBytes,
                        totalBytes = if (totalBytes > 0L) totalBytes else it.totalBytes,
                        remainingSeconds = etaSeconds,
                        negotiatedProtocol = negotiatedProtocol
                    )
                }
            }

            byteBuffer.clear()
            request.read(byteBuffer)
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            closeStream()
            activeRequests.remove(itemId)

            val finalSize = DownloadMediaStoreHelper.getCurrentDownloadedSize(appContext, contentUri)
                .coerceAtLeast(downloadedBytes)

            val validationError = DownloadResponseValidator.validateResponsePayload(
                statusCode = responseStatusCode,
                expectedFileName = resolvedFileName,
                responseMimeType = responseMimeType,
                leadingBytes = headerSample.toByteArray(),
                totalBytesWritten = finalSize
            )
            if (validationError != null) {
                DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                onUpdateItem(itemId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        contentUri = null,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        remainingSeconds = null,
                        errorMessage = validationError
                    )
                }
                return
            }

            DownloadMediaStoreHelper.finalizeDownload(
                context = appContext,
                contentUri = contentUri,
                finalSizeBytes = finalSize
            )

            onUpdateItem(itemId) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    remainingSeconds = null,
                    errorMessage = null,
                    negotiatedProtocol = info.negotiatedProtocol ?: negotiatedProtocol
                )
            }
        }

        override fun onFailed(
            request: UrlRequest,
            info: UrlResponseInfo?,
            error: CronetException
        ) {
            closeStream()
            activeRequests.remove(itemId)
            if (isRestartingForRedirect || abortedEarly || pausedIds.contains(itemId) || canceledIds.contains(itemId)) {
                return
            }

            onUpdateItem(itemId) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    downloadedBytes = downloadedBytes,
                    remainingSeconds = null,
                    errorMessage = error.localizedMessage ?: "Network error"
                )
            }
        }

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            closeStream()
            if (isRestartingForRedirect || abortedEarly) return
            activeRequests.remove(itemId)

            if (canceledIds.remove(itemId)) {
                DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                onRemoveItem(itemId)
            } else if (pausedIds.contains(itemId)) {
                onUpdateItem(itemId) {
                    it.copy(
                        status = DownloadStatus.PAUSED,
                        downloadedBytes = downloadedBytes,
                        remainingSeconds = null
                    )
                }
            }
        }

        private fun computeRemainingSeconds(nowMs: Long): Int? {
            if (totalBytes <= downloadedBytes || totalBytes <= 0L) return null
            val elapsedMs = nowMs - speedWindowStartMs
            if (elapsedMs < 500L) return null

            val bytesDelta = downloadedBytes - speedWindowStartBytes
            if (bytesDelta <= 0L) return null

            val bytesPerSecond = (bytesDelta * 1000.0) / elapsedMs
            if (bytesPerSecond <= 1.0) return null

            if (elapsedMs > 4_000L) {
                speedWindowStartMs = nowMs
                speedWindowStartBytes = downloadedBytes
            }

            val remainingBytes = totalBytes - downloadedBytes
            return (remainingBytes / bytesPerSecond).toInt().coerceAtLeast(1)
        }

        private fun closeStream() {
            writableSink?.close()
            writableSink = null
        }
    }

    private fun openAuthenticatedConnection(
        initialUrl: String,
        userAgent: String?,
        refererUrl: String?,
        existingBytes: Long
    ): HttpURLConnection {
        var currentUrl = initialUrl
        var currentReferer = refererUrl
        var redirects = 0

        while (true) {
            val cookies = resolveCookiesForUrl(currentUrl)
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                if (!cookies.isNullOrBlank()) setRequestProperty("Cookie", cookies)
                if (!currentReferer.isNullOrBlank()) setRequestProperty("Referer", currentReferer)
                if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
            }
            connection.connect()

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || redirects >= MAX_REDIRECTS) {
                    error("Invalid or excessive redirects ($code)")
                }
                currentReferer = currentUrl
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
                continue
            }
            return connection
        }
    }

    private fun downloadWithFallbackConnection(
        appContext: Context,
        item: DownloadItem,
        contentUri: Uri,
        existingBytes: Long,
        userAgent: String?,
        refererUrl: String?
    ) {
        var downloaded = existingBytes
        var sink: WritableDownloadSink? = null
        val headerSample = ByteArrayOutputStream(HEADER_SAMPLE_LIMIT_BYTES)
        try {
            val connection = openAuthenticatedConnection(
                initialUrl = item.url,
                userAgent = userAgent,
                refererUrl = refererUrl,
                existingBytes = existingBytes
            )

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                connection.disconnect()
                DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                onUpdateItem(item.id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        contentUri = null,
                        downloadedBytes = 0L,
                        remainingSeconds = null,
                        errorMessage = "Server returned HTTP $statusCode"
                    )
                }
                return
            }

            val responseMimeType = connection.contentType
            val earlyMismatchError = DownloadResponseValidator.validateExpectedTypeVsResponseMime(
                expectedFileName = item.fileName,
                expectedMimeType = item.mimeType,
                responseMimeType = responseMimeType
            )
            if (earlyMismatchError != null) {
                connection.disconnect()
                DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                onUpdateItem(item.id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        contentUri = null,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        remainingSeconds = null,
                        errorMessage = earlyMismatchError
                    )
                }
                return
            }

            val isPartial = statusCode == 206 && existingBytes > 0L
            if (!isPartial) downloaded = 0L

            val responseDisposition = connection.getHeaderField("Content-Disposition")
            val updatedName = DownloadResponseValidator.resolveFinalFileName(
                fallbackName = item.fileName,
                finalUrl = connection.url?.toString() ?: item.url,
                responseDisposition = responseDisposition,
                responseMimeType = responseMimeType
            )
            val updatedMime = DownloadResponseValidator.resolveMimeType(
                updatedName,
                responseMimeType ?: item.mimeType
            )
            val resolvedFileName = DownloadMediaStoreHelper.updateMetadata(
                context = appContext,
                contentUri = contentUri,
                newDisplayName = updatedName,
                newMimeType = updatedMime
            )

            val length = connection.contentLengthLong
            val total = when {
                isPartial && length > 0L -> existingBytes + length
                length > 0L -> length
                else -> 0L
            }

            onUpdateItem(item.id) {
                it.copy(
                    fileName = resolvedFileName,
                    mimeType = updatedMime,
                    fileType = inferDownloadFileType(resolvedFileName, updatedMime),
                    totalBytes = if (total > 0L) total else it.totalBytes
                )
            }

            sink = DownloadMediaStoreHelper.openWritableSink(
                context = appContext,
                contentUri = contentUri,
                append = isPartial,
                offsetBytes = existingBytes
            )

            connection.inputStream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE_BYTES)
                var lastUiMs = 0L
                while (true) {
                    if (pausedIds.contains(item.id) || canceledIds.contains(item.id)) {
                        break
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (headerSample.size() < HEADER_SAMPLE_LIMIT_BYTES) {
                        val toCapture = minOf(read, HEADER_SAMPLE_LIMIT_BYTES - headerSample.size())
                        headerSample.write(buffer, 0, toCapture)
                    }

                    val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                    while (byteBuffer.hasRemaining()) {
                        sink.channel.write(byteBuffer)
                    }
                    downloaded += read

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUiMs >= UI_THROTTLE_MS) {
                        lastUiMs = now
                        onUpdateItem(item.id) {
                            it.copy(
                                downloadedBytes = downloaded,
                                totalBytes = if (total > 0L) total else it.totalBytes
                            )
                        }
                    }
                }
            }

            sink.close()
            sink = null
            connection.disconnect()

            when {
                canceledIds.remove(item.id) -> {
                    DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                    onRemoveItem(item.id)
                }
                pausedIds.contains(item.id) -> {
                    onUpdateItem(item.id) {
                        it.copy(status = DownloadStatus.PAUSED, downloadedBytes = downloaded)
                    }
                }
                else -> {
                    val finalSize = DownloadMediaStoreHelper.getCurrentDownloadedSize(appContext, contentUri)
                        .coerceAtLeast(downloaded)
                    val validationError = DownloadResponseValidator.validateResponsePayload(
                        statusCode = statusCode,
                        expectedFileName = resolvedFileName,
                        responseMimeType = responseMimeType,
                        leadingBytes = headerSample.toByteArray(),
                        totalBytesWritten = finalSize
                    )
                    if (validationError != null) {
                        DownloadMediaStoreHelper.deleteEntry(appContext, contentUri)
                        onUpdateItem(item.id) {
                            it.copy(
                                status = DownloadStatus.FAILED,
                                contentUri = null,
                                downloadedBytes = 0L,
                                totalBytes = 0L,
                                remainingSeconds = null,
                                errorMessage = validationError
                            )
                        }
                        return
                    }

                    DownloadMediaStoreHelper.finalizeDownload(
                        context = appContext,
                        contentUri = contentUri,
                        finalSizeBytes = finalSize
                    )
                    onUpdateItem(item.id) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            downloadedBytes = finalSize,
                            totalBytes = finalSize,
                            remainingSeconds = null,
                            errorMessage = null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            sink?.close()
            if (!pausedIds.contains(item.id) && !canceledIds.contains(item.id)) {
                onUpdateItem(item.id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        remainingSeconds = null,
                        errorMessage = e.localizedMessage ?: "Download failed"
                    )
                }
            }
        }
    }
}