package com.verve.browser.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import com.verve.browser.data.network.QuicNetworkManager
import com.verve.browser.presentation.downloads.DownloadItem
import com.verve.browser.presentation.downloads.DownloadStatus
import com.verve.browser.presentation.downloads.inferDownloadFileType
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
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
import androidx.core.net.toUri

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
     * automatically routes through Cronet (HTTP/3 / QUIC) into Internal storage/Download.
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

        val guessedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val resolvedMime = resolveMimeType(guessedFileName, mimeType)
        val domain = runCatching { url.toUri().host.orEmpty() }
            .getOrDefault("")
            .removePrefix("www.")
            .ifBlank { "web" }

        val entry = insertMediaStoreDownload(
            context = appContext,
            requestedName = guessedFileName,
            mimeType = resolvedMime
        )

        val id = UUID.randomUUID().toString()
        val initialItem = DownloadItem(
            id = id,
            fileName = entry.displayName,
            domain = domain,
            url = url,
            downloadedBytes = 0L,
            totalBytes = contentLength.coerceAtLeast(0L),
            status = if (entry.contentUri != null) DownloadStatus.DOWNLOADING else DownloadStatus.FAILED,
            fileType = inferDownloadFileType(entry.displayName, resolvedMime),
            dateGroup = "Today",
            mimeType = resolvedMime,
            contentUri = entry.contentUri?.toString()
        )

        _downloads.update { current -> listOf(initialItem) + current }
        _downloadStartedEvents.tryEmit(initialItem)

        if (entry.contentUri != null) {
            startOrResumeRequest(
                appContext = appContext,
                item = initialItem,
                userAgent = userAgent
            )
        }
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
                val appContext = context.applicationContext
                val existingUri = item.contentUri?.let(Uri::parse)
                val targetUri = existingUri ?: insertMediaStoreDownload(
                    context = appContext,
                    requestedName = item.fileName,
                    mimeType = item.mimeType
                ).contentUri ?: return

                pausedIds.remove(itemId)
                canceledIds.remove(itemId)
                val updatedItem = item.copy(
                    status = DownloadStatus.DOWNLOADING,
                    contentUri = targetUri.toString()
                )
                updateItem(itemId) { updatedItem }

                startOrResumeRequest(
                    appContext = appContext,
                    item = updatedItem,
                    userAgent = null
                )
            }
            DownloadStatus.COMPLETED -> Unit
        }
    }

    fun cancelDownload(context: Context, itemId: String) {
        canceledIds.add(itemId)
        pausedIds.remove(itemId)
        activeRequests.remove(itemId)?.cancel()

        val removed = _downloads.value.find { it.id == itemId }
        _downloads.update { list -> list.filterNot { it.id == itemId } }
        removed?.contentUri?.let { uriString ->
            deleteMediaStoreEntry(context.applicationContext, uriString.toUri())
        }
    }

    fun deleteDownload(context: Context, itemId: String) {
        cancelDownload(context, itemId)
    }

    fun clearCompleted(context: Context) {
        val appContext = context.applicationContext
        val completed = _downloads.value.filterNot { it.isInProgress }
        _downloads.update { list -> list.filter { it.isInProgress } }
        completed.forEach { item ->
            item.contentUri?.let { uriString ->
                deleteMediaStoreEntry(appContext, uriString.toUri())
            }
        }
    }

    private fun startOrResumeRequest(
        appContext: Context,
        item: DownloadItem,
        userAgent: String?
    ) {
        val contentUri = item.contentUri?.let(Uri::parse) ?: return
        val existingBytes = getCurrentDownloadedSize(appContext, contentUri)
        val cronetEngine = QuicNetworkManager.getCronetEngine(appContext)

        if (cronetEngine == null) {
            executor.execute {
                downloadWithFallbackConnection(
                    appContext = appContext,
                    item = item,
                    contentUri = contentUri,
                    existingBytes = existingBytes,
                    userAgent = userAgent
                )
            }
            return
        }

        val callback = CronetFileDownloadCallback(
            appContext = appContext,
            itemId = item.id,
            contentUri = contentUri,
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
        private val appContext: Context,
        private val itemId: String,
        private val contentUri: Uri,
        private val initialOffsetBytes: Long
    ) : UrlRequest.Callback() {

        private var writableSink: WritableDownloadSink? = null
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

            writableSink = openWritableDownloadSink(
                context = appContext,
                contentUri = contentUri,
                append = isPartialContent,
                offsetBytes = initialOffsetBytes
            )

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
                writableSink?.channel?.write(byteBuffer)
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

            finalizeMediaStoreDownload(
                context = appContext,
                contentUri = contentUri,
                finalSizeBytes = downloadedBytes
            )

            val finalSize = getCurrentDownloadedSize(appContext, contentUri)
                .coerceAtLeast(downloadedBytes)

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
                deleteMediaStoreEntry(appContext, contentUri)
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
            writableSink?.close()
            writableSink = null
        }
    }

    private fun downloadWithFallbackConnection(
        appContext: Context,
        item: DownloadItem,
        contentUri: Uri,
        existingBytes: Long,
        userAgent: String?
    ) {
        var downloaded = existingBytes
        var sink: WritableDownloadSink? = null
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

            sink = openWritableDownloadSink(
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
                    val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                    while (byteBuffer.hasRemaining()) {
                        sink.channel.write(byteBuffer)
                    }
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

            sink.close()
            sink = null

            when {
                canceledIds.remove(item.id) -> {
                    deleteMediaStoreEntry(appContext, contentUri)
                    _downloads.update { list -> list.filterNot { it.id == item.id } }
                }
                pausedIds.contains(item.id) -> {
                    updateItem(item.id) {
                        it.copy(status = DownloadStatus.PAUSED, downloadedBytes = downloaded)
                    }
                }
                else -> {
                    finalizeMediaStoreDownload(
                        context = appContext,
                        contentUri = contentUri,
                        finalSizeBytes = downloaded
                    )
                    val finalSize = getCurrentDownloadedSize(appContext, contentUri)
                        .coerceAtLeast(downloaded)
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
            sink?.close()
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

    private data class MediaStoreDownloadEntry(
        val displayName: String,
        val contentUri: Uri?
    )

    private class WritableDownloadSink(
        val channel: FileChannel,
        private val outputStream: FileOutputStream,
        private val parcelFileDescriptor: ParcelFileDescriptor
    ) {
        fun close() {
            runCatching { channel.force(false) }
            runCatching { channel.close() }
            runCatching { outputStream.flush() }
            runCatching { outputStream.close() }
            runCatching { parcelFileDescriptor.close() }
        }
    }

    private fun insertMediaStoreDownload(
        context: Context,
        requestedName: String,
        mimeType: String
    ): MediaStoreDownloadEntry {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, requestedName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return MediaStoreDownloadEntry(displayName = requestedName, contentUri = null)

        val actualDisplayName = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: requestedName

        return MediaStoreDownloadEntry(
            displayName = actualDisplayName,
            contentUri = uri
        )
    }

    private fun openWritableDownloadSink(
        context: Context,
        contentUri: Uri,
        append: Boolean,
        offsetBytes: Long
    ): WritableDownloadSink {
        val pfd = context.contentResolver.openFileDescriptor(contentUri, "rw")
            ?: error("Unable to open MediaStore file descriptor for $contentUri")
        val fos = FileOutputStream(pfd.fileDescriptor)
        val channel = fos.channel
        if (append && offsetBytes > 0L) {
            channel.position(offsetBytes)
        } else {
            channel.truncate(0L)
            channel.position(0L)
        }
        return WritableDownloadSink(
            channel = channel,
            outputStream = fos,
            parcelFileDescriptor = pfd
        )
    }

    private fun finalizeMediaStoreDownload(
        context: Context,
        contentUri: Uri,
        finalSizeBytes: Long
    ) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
                if (finalSizeBytes > 0L) {
                    put(MediaStore.Downloads.SIZE, finalSizeBytes)
                }
            }
            context.contentResolver.update(contentUri, values, null, null)
        }
    }

    private fun getCurrentDownloadedSize(context: Context, contentUri: Uri): Long {
        return runCatching {
            context.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                pfd.statSize.coerceAtLeast(0L)
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun deleteMediaStoreEntry(context: Context, contentUri: Uri) {
        runCatching { context.contentResolver.delete(contentUri, null, null) }
    }

    private fun resolveMimeType(fileName: String, serverMimeType: String?): String {
        val cleanedServerMime = serverMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

        val isGenericMime = cleanedServerMime.isBlank() ||
                cleanedServerMime == "*/*" ||
                cleanedServerMime == "application/octet-stream" ||
                cleanedServerMime == "binary/octet-stream"

        return if (isGenericMime) {
            guessMimeTypeFromName(fileName)
        } else {
            cleanedServerMime
        }
    }

    private fun guessMimeTypeFromName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "apk") return "application/vnd.android.package-archive"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}