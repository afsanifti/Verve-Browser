package com.verve.browser.data.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import com.verve.browser.data.network.QuicNetworkManager
import com.verve.browser.presentation.downloads.DownloadItem
import com.verve.browser.presentation.downloads.DownloadStatus
import com.verve.browser.presentation.downloads.inferDownloadFileType
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo

object VerveDownloadManager {

    private const val BUFFER_SIZE_BYTES = 64 * 1024
    private const val UI_THROTTLE_MS = 150L

    private val executor = Executors.newCachedThreadPool()
    private val activeRequests = ConcurrentHashMap<String, UrlRequest>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val canceledIds = ConcurrentHashMap.newKeySet<String>()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _downloadStartedEvents = MutableSharedFlow<DownloadItem>(extraBufferCapacity = 8)
    val downloadStartedEvents: SharedFlow<DownloadItem> = _downloadStartedEvents.asSharedFlow()

    /**
     * Attaches a WebView DownloadListener so any file download triggered on a web page
     * automatically routes through Cronet (HTTP/3 / QUIC).
     */
    fun attachToWebView(
        context: Context,
        webView: WebView,
        onDownloadStarted: (DownloadItem) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val item = enqueueDownload(
                context = appContext,
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                contentLength = contentLength
            )
            onDownloadStarted(item)
        }
    }

    fun enqueueDownload(
        context: Context,
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        contentLength: Long = -1L
    ): DownloadItem {
        val appContext = context.applicationContext
        QuicNetworkManager.initialize(appContext)

        val resolvedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val targetFile = resolveUniqueTargetFile(appContext, resolvedFileName)
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() }
            ?: guessMimeTypeFromName(targetFile.name)
        val domain = runCatching { Uri.parse(url).host.orEmpty() }
            .getOrDefault("")
            .removePrefix("www.")
            .ifBlank { "web" }

        val id = UUID.randomUUID().toString()
        val initialItem = DownloadItem(
            id = id,
            fileName = targetFile.name,
            domain = domain,
            url = url,
            downloadedBytes = 0L,
            totalBytes = contentLength.coerceAtLeast(0L),
            status = DownloadStatus.DOWNLOADING,
            fileType = inferDownloadFileType(targetFile.name, resolvedMime),
            dateGroup = "Today",
            localFilePath = targetFile.absolutePath,
            mimeType = resolvedMime
        )

        _downloads.update { current -> listOf(initialItem) + current }
        _downloadStartedEvents.tryEmit(initialItem)

        startOrResumeRequest(
            appContext = appContext,
            item = initialItem,
            userAgent = userAgent
        )
        return initialItem
    }

    fun togglePauseResume(context: Context, itemId: String) {
        val item = _downloads.value.find { it.id == itemId } ?: return
        when (item.status) {
            DownloadStatus.DOWNLOADING -> {
                pausedIds.add(itemId)
                activeRequests.remove(itemId)?.cancel()
                updateItem(itemId) {
                    it.copy(status = DownloadStatus.PAUSED, remainingSeconds = null)
                }
            }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                pausedIds.remove(itemId)
                canceledIds.remove(itemId)
                updateItem(itemId) {
                    it.copy(status = DownloadStatus.DOWNLOADING)
                }
                startOrResumeRequest(
                    appContext = context.applicationContext,
                    item = item.copy(status = DownloadStatus.DOWNLOADING),
                    userAgent = null
                )
            }
            DownloadStatus.COMPLETED -> Unit
        }
    }

    fun cancelDownload(itemId: String) {
        canceledIds.add(itemId)
        pausedIds.remove(itemId)
        activeRequests.remove(itemId)?.cancel()

        val removed = _downloads.value.find { it.id == itemId }
        _downloads.update { list -> list.filterNot { it.id == itemId } }
        removed?.localFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    fun deleteDownload(itemId: String) {
        cancelDownload(itemId)
    }

    fun clearCompleted() {
        val completed = _downloads.value.filterNot { it.isInProgress }
        _downloads.update { list -> list.filter { it.isInProgress } }
        completed.forEach { item ->
            item.localFilePath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
    }

    private fun startOrResumeRequest(
        appContext: Context,
        item: DownloadItem,
        userAgent: String?
    ) {
        val targetFile = item.localFilePath?.let { File(it) }
            ?: resolveUniqueTargetFile(appContext, item.fileName)
        targetFile.parentFile?.mkdirs()

        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L
        val cronetEngine = QuicNetworkManager.getCronetEngine(appContext)

        if (cronetEngine == null) {
            executor.execute {
                downloadWithFallbackConnection(item, targetFile, existingBytes, userAgent)
            }
            return
        }

        val callback = CronetFileDownloadCallback(
            itemId = item.id,
            targetFile = targetFile,
            initialOffsetBytes = existingBytes
        )

        val requestBuilder = cronetEngine.newUrlRequestBuilder(item.url, callback, executor)
            .setHttpMethod("GET")
            .allowDirectExecutor()

        if (!userAgent.isNullOrBlank()) {
            requestBuilder.addHeader("User-Agent", userAgent)
        }
        if (existingBytes > 0L) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        val request = requestBuilder.build()
        activeRequests[item.id] = request
        request.start()
    }

    private class CronetFileDownloadCallback(
        private val itemId: String,
        private val targetFile: File,
        private val initialOffsetBytes: Long
    ) : UrlRequest.Callback() {

        private var outputStream: FileOutputStream? = null
        private var channel: WritableByteChannel? = null
        private var downloadedBytes: Long = initialOffsetBytes
        private var totalBytes: Long = 0L
        private var negotiatedProtocol: String? = null

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
            } else {
                request.followRedirect()
            }
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            if (canceledIds.contains(itemId) || pausedIds.contains(itemId)) {
                request.cancel()
                return
            }

            negotiatedProtocol = info.negotiatedProtocol
            val isPartialContent = info.httpStatusCode == 206 && initialOffsetBytes > 0L
            if (!isPartialContent) {
                downloadedBytes = 0L
                speedWindowStartBytes = 0L
            }

            val contentLength = info.allHeaders["Content-Length"]
                ?.firstOrNull()
                ?.toLongOrNull()
                ?: -1L

            totalBytes = when {
                isPartialContent && contentLength > 0L -> initialOffsetBytes + contentLength
                contentLength > 0L -> contentLength
                else -> 0L
            }

            outputStream = FileOutputStream(targetFile, isPartialContent)
            channel = Channels.newChannel(outputStream)

            updateItem(itemId) {
                it.copy(
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
            while (byteBuffer.hasRemaining()) {
                channel?.write(byteBuffer)
            }
            downloadedBytes += bytesWritten

            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastUiUpdateMs >= UI_THROTTLE_MS) {
                lastUiUpdateMs = nowMs
                val etaSeconds = computeRemainingSeconds(nowMs)
                updateItem(itemId) {
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
            val finalSize = targetFile.length().coerceAtLeast(downloadedBytes)

            updateItem(itemId) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    remainingSeconds = null,
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
            if (pausedIds.contains(itemId) || canceledIds.contains(itemId)) return

            updateItem(itemId) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    downloadedBytes = downloadedBytes,
                    remainingSeconds = null
                )
            }
        }

        override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
            closeStream()
            activeRequests.remove(itemId)

            if (canceledIds.remove(itemId)) {
                runCatching { targetFile.delete() }
                _downloads.update { list -> list.filterNot { it.id == itemId } }
            } else if (pausedIds.contains(itemId)) {
                updateItem(itemId) {
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
            runCatching { channel?.close() }
            runCatching { outputStream?.flush() }
            runCatching { outputStream?.close() }
            channel = null
            outputStream = null
        }
    }

    private fun downloadWithFallbackConnection(
        item: DownloadItem,
        targetFile: File,
        existingBytes: Long,
        userAgent: String?
    ) {
        var downloaded = existingBytes
        try {
            val connection = (URL(item.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
            }
            connection.connect()

            val isPartial = connection.responseCode == 206 && existingBytes > 0L
            if (!isPartial) downloaded = 0L

            val length = connection.contentLengthLong
            val total = when {
                isPartial && length > 0L -> existingBytes + length
                length > 0L -> length
                else -> 0L
            }

            connection.inputStream.use { input ->
                FileOutputStream(targetFile, isPartial).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    var lastUiMs = 0L
                    while (true) {
                        if (pausedIds.contains(item.id) || canceledIds.contains(item.id)) {
                            break
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUiMs >= UI_THROTTLE_MS) {
                            lastUiMs = now
                            updateItem(item.id) {
                                it.copy(
                                    downloadedBytes = downloaded,
                                    totalBytes = if (total > 0L) total else it.totalBytes
                                )
                            }
                        }
                    }
                }
            }

            when {
                canceledIds.remove(item.id) -> {
                    runCatching { targetFile.delete() }
                    _downloads.update { list -> list.filterNot { it.id == item.id } }
                }
                pausedIds.contains(item.id) -> {
                    updateItem(item.id) {
                        it.copy(status = DownloadStatus.PAUSED, downloadedBytes = downloaded)
                    }
                }
                else -> {
                    val finalSize = targetFile.length().coerceAtLeast(downloaded)
                    updateItem(item.id) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            downloadedBytes = finalSize,
                            totalBytes = finalSize,
                            remainingSeconds = null
                        )
                    }
                }
            }
        } catch (_: Exception) {
            if (!pausedIds.contains(item.id) && !canceledIds.contains(item.id)) {
                updateItem(item.id) {
                    it.copy(status = DownloadStatus.FAILED, remainingSeconds = null)
                }
            }
        }
    }

    private fun updateItem(itemId: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { currentList ->
            currentList.map { existing ->
                if (existing.id == itemId) transform(existing) else existing
            }
        }
    }

    private fun resolveUniqueTargetFile(context: Context, requestedName: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        dir.mkdirs()

        val baseName = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "")
        var candidate = File(dir, requestedName)
        var counter = 1
        while (candidate.exists()) {
            val numberedName = if (extension.isNotEmpty()) {
                "$baseName ($counter).$extension"
            } else {
                "$baseName ($counter)"
            }
            candidate = File(dir, numberedName)
            counter++
        }
        return candidate
    }

    private fun guessMimeTypeFromName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}