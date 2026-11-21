package com.verve.browser.data.download

import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.core.net.toUri
import com.verve.browser.data.network.QuicNetworkManager
import com.verve.browser.presentation.downloads.DownloadItem
import com.verve.browser.presentation.downloads.DownloadStatus
import com.verve.browser.presentation.downloads.inferDownloadFileType
import java.util.UUID
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object VerveDownloadManager {

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _downloadStartedEvents = MutableSharedFlow<DownloadItem>(extraBufferCapacity = 8)
    val downloadStartedEvents: SharedFlow<DownloadItem> = _downloadStartedEvents.asSharedFlow()

    private val inlinePdfHandlers = WeakHashMap<WebView, (Uri, String) -> Unit>()

    private val networkExecutor = DownloadNetworkExecutor(
        onUpdateItem = ::updateItem,
        onRemoveItem = { itemId ->
            _downloads.update { list -> list.filterNot { it.id == itemId } }
        },
        onRecordFailed = ::recordFailedDownload,
        onEnqueueFallback = ::enqueueDownload
    )

    fun registerInlinePdfHandler(
        webView: WebView,
        onInlinePdfReady: (Uri, String) -> Unit
    ) {
        inlinePdfHandlers[webView] = onInlinePdfReady
    }

    fun attachToWebView(
        context: Context,
        webView: WebView,
        onDownloadStarted: (DownloadItem) -> Unit = {},
        onInlinePdfReady: ((Uri, String) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        if (onInlinePdfReady != null) {
            inlinePdfHandlers[webView] = onInlinePdfReady
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val refererUrl = webView.url
            val guessedFileName = DownloadResponseValidator.sanitizeFileName(
                URLUtil.guessFileName(url, contentDisposition, mimeType)
            )
            val resolvedMime = DownloadResponseValidator.resolveMimeType(guessedFileName, mimeType)
            val isPdf = resolvedMime.equals("application/pdf", ignoreCase = true) ||
                    guessedFileName.endsWith(".pdf", ignoreCase = true)
            val resolvedInlinePdfHandler = onInlinePdfReady ?: inlinePdfHandlers[webView]

            if (isPdf && resolvedInlinePdfHandler != null &&
                (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))
            ) {
                networkExecutor.fetchPdfForInlinePreview(
                    appContext = appContext,
                    url = url,
                    refererUrl = refererUrl,
                    suggestedFileName = guessedFileName,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    contentLength = contentLength,
                    onInlinePdfReady = resolvedInlinePdfHandler,
                    onFallbackToDownload = onDownloadStarted
                )
                return@setDownloadListener
            }

            val item = enqueueDownload(
                context = appContext,
                url = url,
                userAgent = userAgent,
                refererUrl = refererUrl,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                contentLength = contentLength
            )
            onDownloadStarted(item)
        }
    }

    fun saveBridgedFileToDownloads(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String?,
        sourceUrl: String,
        isPostRequest: Boolean = true
    ): DownloadItem? {
        val appContext = context.applicationContext
        val safeName = DownloadResponseValidator.sanitizeFileName(fileName)
        val resolvedMime = DownloadResponseValidator.resolveMimeType(safeName, mimeType)

        val validation = DownloadResponseValidator.validateResponsePayload(
            statusCode = 200,
            expectedFileName = safeName,
            responseMimeType = resolvedMime,
            leadingBytes = bytes.take(HEADER_SAMPLE_LIMIT_BYTES).toByteArray(),
            totalBytesWritten = bytes.size.toLong()
        )
        if (validation != null) {
            return recordFailedDownload(
                fileName = safeName,
                sourceUrl = sourceUrl,
                errorMessage = validation,
                isPostRequest = isPostRequest
            )
        }

        val entry = DownloadMediaStoreHelper.insertDownload(
            context = appContext,
            requestedName = safeName,
            mimeType = resolvedMime
        )
        val targetUri = entry.contentUri ?: return null

        return runCatching {
            appContext.contentResolver.openOutputStream(targetUri)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: return null

            val totalSize = bytes.size.toLong()
            DownloadMediaStoreHelper.finalizeDownload(
                context = appContext,
                contentUri = targetUri,
                finalSizeBytes = totalSize
            )

            val item = DownloadItem(
                id = UUID.randomUUID().toString(),
                fileName = entry.displayName,
                domain = extractDomain(sourceUrl),
                url = sourceUrl,
                downloadedBytes = totalSize,
                totalBytes = totalSize,
                status = DownloadStatus.COMPLETED,
                fileType = inferDownloadFileType(entry.displayName, resolvedMime),
                dateGroup = "Today",
                mimeType = resolvedMime,
                contentUri = targetUri.toString(),
                isPostRequest = isPostRequest
            )
            _downloads.update { current -> listOf(item) + current }
            _downloadStartedEvents.tryEmit(item)
            item
        }.getOrNull()
    }

    fun saveCachedPdfToDownloads(
        context: Context,
        sourceUri: Uri,
        fileName: String
    ): DownloadItem? {
        val appContext = context.applicationContext
        val safeName = DownloadResponseValidator.sanitizeFileName(fileName)
        val entry = DownloadMediaStoreHelper.insertDownload(
            context = appContext,
            requestedName = safeName,
            mimeType = "application/pdf"
        )
        val targetUri = entry.contentUri ?: return null

        return runCatching {
            var copiedBytes = 0L
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                appContext.contentResolver.openOutputStream(targetUri)?.use { output ->
                    copiedBytes = input.copyTo(output)
                }
            } ?: return null

            DownloadMediaStoreHelper.finalizeDownload(
                context = appContext,
                contentUri = targetUri,
                finalSizeBytes = copiedBytes
            )

            val item = DownloadItem(
                id = UUID.randomUUID().toString(),
                fileName = entry.displayName,
                domain = "local",
                url = sourceUri.toString(),
                downloadedBytes = copiedBytes,
                totalBytes = copiedBytes,
                status = DownloadStatus.COMPLETED,
                fileType = inferDownloadFileType(entry.displayName, "application/pdf"),
                dateGroup = "Today",
                mimeType = "application/pdf",
                contentUri = targetUri.toString()
            )
            _downloads.update { current -> listOf(item) + current }
            _downloadStartedEvents.tryEmit(item)
            item
        }.getOrNull()
    }

    fun recordFailedDownload(
        fileName: String,
        sourceUrl: String,
        errorMessage: String,
        isPostRequest: Boolean = false
    ): DownloadItem {
        val cleanName = DownloadResponseValidator.sanitizeFileName(fileName)
        val resolvedMime = DownloadResponseValidator.resolveMimeType(cleanName, null)
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            fileName = cleanName,
            domain = extractDomain(sourceUrl),
            url = sourceUrl,
            downloadedBytes = 0L,
            totalBytes = 0L,
            status = DownloadStatus.FAILED,
            fileType = inferDownloadFileType(cleanName, resolvedMime),
            dateGroup = "Today",
            mimeType = resolvedMime,
            isPostRequest = isPostRequest,
            errorMessage = errorMessage
        )
        _downloads.update { current -> listOf(item) + current }
        _downloadStartedEvents.tryEmit(item)
        return item
    }

    fun enqueueDownload(
        context: Context,
        url: String,
        userAgent: String? = null,
        refererUrl: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        contentLength: Long = -1L
    ): DownloadItem {
        val appContext = context.applicationContext
        QuicNetworkManager.initialize(appContext)

        val guessedFileName = DownloadResponseValidator.sanitizeFileName(
            URLUtil.guessFileName(url, contentDisposition, mimeType)
        )
        val resolvedMime = DownloadResponseValidator.resolveMimeType(guessedFileName, mimeType)
        val entry = DownloadMediaStoreHelper.insertDownload(
            context = appContext,
            requestedName = guessedFileName,
            mimeType = resolvedMime
        )

        val initialItem = DownloadItem(
            id = UUID.randomUUID().toString(),
            fileName = entry.displayName,
            domain = extractDomain(url),
            url = url,
            downloadedBytes = 0L,
            totalBytes = contentLength.coerceAtLeast(0L),
            status = if (entry.contentUri != null) DownloadStatus.DOWNLOADING else DownloadStatus.FAILED,
            fileType = inferDownloadFileType(entry.displayName, resolvedMime),
            dateGroup = "Today",
            mimeType = resolvedMime,
            contentUri = entry.contentUri?.toString(),
            refererUrl = refererUrl,
            userAgent = userAgent,
            isPostRequest = false,
            errorMessage = if (entry.contentUri == null) "Unable to create file in Downloads" else null
        )

        _downloads.update { current -> listOf(initialItem) + current }
        _downloadStartedEvents.tryEmit(initialItem)

        if (entry.contentUri != null) {
            networkExecutor.startOrResumeRequest(
                appContext = appContext,
                item = initialItem,
                targetUrl = url,
                refererUrl = refererUrl,
                userAgent = userAgent,
                redirectCount = 0
            )
        }
        return initialItem
    }

    fun togglePauseResume(context: Context, itemId: String) {
        val item = _downloads.value.find { it.id == itemId } ?: return
        when (item.status) {
            DownloadStatus.DOWNLOADING -> {
                networkExecutor.pauseRequest(itemId)
                updateItem(itemId) {
                    it.copy(status = DownloadStatus.PAUSED, remainingSeconds = null)
                }
            }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                if (item.isPostRequest && item.status == DownloadStatus.FAILED) return

                val appContext = context.applicationContext
                val existingUri = item.contentUri?.let(Uri::parse)
                val targetUri = existingUri ?: DownloadMediaStoreHelper.insertDownload(
                    context = appContext,
                    requestedName = item.fileName,
                    mimeType = item.mimeType
                ).contentUri ?: return

                networkExecutor.clearTransientFlags(itemId)
                val updatedItem = item.copy(
                    status = DownloadStatus.DOWNLOADING,
                    contentUri = targetUri.toString(),
                    errorMessage = null
                )
                updateItem(itemId) { updatedItem }

                networkExecutor.startOrResumeRequest(
                    appContext = appContext,
                    item = updatedItem,
                    targetUrl = item.url,
                    refererUrl = item.refererUrl,
                    userAgent = item.userAgent,
                    redirectCount = 0
                )
            }
            DownloadStatus.COMPLETED -> Unit
        }
    }

    fun cancelDownload(context: Context, itemId: String) {
        networkExecutor.cancelRequest(itemId)
        val removed = _downloads.value.find { it.id == itemId }
        _downloads.update { list -> list.filterNot { it.id == itemId } }
        removed?.contentUri?.let { uriString ->
            DownloadMediaStoreHelper.deleteEntry(context.applicationContext, uriString.toUri())
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
                DownloadMediaStoreHelper.deleteEntry(appContext, uriString.toUri())
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

    private fun extractDomain(url: String): String {
        return runCatching { url.toUri().host.orEmpty() }
            .getOrDefault("")
            .removePrefix("www.")
            .ifBlank { "web" }
    }
}