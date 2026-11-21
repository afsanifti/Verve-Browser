package com.verve.browser.presentation.browser.components.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.TabPageColorCache
import com.verve.browser.presentation.browser.util.TabWebViewPool
import com.verve.browser.presentation.browser.util.WebSnapshotColorExtractor
import java.io.ByteArrayInputStream
import androidx.core.net.toUri
import androidx.core.text.htmlEncode

internal class VerveWebViewClient(
    private val tabId: String,
    private val viewModel: BrowserViewModel,
    private val tabWebViewPool: TabWebViewPool,
    private val pageColorCache: TabPageColorCache,
    private val edgeProbeScheduler: EdgeProbeScheduler,
    private val bottomCoordinator: DocumentBottomScrollCoordinator,
    private val scheduleThumbnailCapture: (Long) -> Unit,
    private val scheduleSnapshotColorExtract: (Long) -> Unit,
    private val scheduleSilentSnapshotColorExtract: (Long) -> Unit,
    private val onNavigatedUrl: (String) -> Unit,
    private val onWebViewDestroyed: () -> Unit
) : WebViewClient() {

    @Volatile
    private var currentPageUrl: String = ""
    private var lastColorExtractedUrl: String = ""
    private var isMainFrameLoading: Boolean = false
    private val attemptedFallbackUrls = HashSet<String>()
    private var pendingFallbackUrl: String? = null
    private var pendingErrorPageUrl: String? = null
    private var isShowingErrorPage: Boolean = false
    private var consecutiveRenderCrashes: Int = 0

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase().orEmpty()

        if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "javascript") {
            return false
        }

        val context = view?.context ?: return true
        return handleExternalUri(context, uri, view)
    }

    private fun handleExternalUri(context: Context, uri: Uri, webView: WebView): Boolean {
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "intent") {
            val parsedIntent = runCatching {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            }.getOrNull() ?: return true

            // Prevent webpages from targeting internal app components or selectors
            parsedIntent.addCategory(Intent.CATEGORY_BROWSABLE)
            parsedIntent.component = null
            parsedIntent.selector = null
            parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val launched = runCatching {
                context.startActivity(parsedIntent)
                true
            }.getOrDefault(false)

            if (!launched) {
                val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                val fallbackScheme = fallbackUrl?.let { it.toUri().scheme?.lowercase() }
                if (!fallbackUrl.isNullOrBlank() && (fallbackScheme == "http" || fallbackScheme == "https")) {
                    webView.loadUrl(fallbackUrl)
                } else {
                    val targetPackage = parsedIntent.`package`
                    if (!targetPackage.isNullOrBlank()) {
                        runCatching {
                            val marketIntent = Intent(
                                Intent.ACTION_VIEW,
                                "market://details?id=$targetPackage".toUri()
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(marketIntent)
                        }
                    }
                }
            }
            return true
        }

        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank() && url != "about:blank" && !url.startsWith("data:")) {
            // Ignore the synthetic onPageStarted fired by renderLoadErrorPage's loadDataWithBaseURL
            if (pendingErrorPageUrl != null && url == pendingErrorPageUrl) {
                pendingErrorPageUrl = null
                isShowingErrorPage = true
                return
            }

            view?.evaluateJavascript(PdfInterceptorScript.SCRIPT, null)

            isMainFrameLoading = true
            if (pendingFallbackUrl == null || url == pendingFallbackUrl) {
                pendingFallbackUrl = null
                isShowingErrorPage = false
            }

            viewModel.onPageLoadStarted(tabId)

            val normalizedUrl = WebSnapshotColorExtractor.normalizeUrl(url)
            val cached = pageColorCache.get(normalizedUrl)
            if (cached != null) {
                WebSnapshotColorExtractor.cancel(view)
                lastColorExtractedUrl = normalizedUrl
                viewModel.onSnapshotColorReady(tabId, cached.color)
            } else if (WebSnapshotColorExtractor.normalizeUrl(currentPageUrl) != normalizedUrl) {
                lastColorExtractedUrl = ""
                WebSnapshotColorExtractor.cancel(view)
            }

            currentPageUrl = url
            onNavigatedUrl(url)
            if (viewModel.uiState.value.activeTabId == tabId) {
                viewModel.setToolbarCollapsed(false)
            }
            bottomCoordinator.resetForNavigation()
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        if (!url.isNullOrBlank() && url != "about:blank" && !isShowingErrorPage) {
            view?.evaluateJavascript(PdfInterceptorScript.SCRIPT, null)
            consecutiveRenderCrashes = 0
            attemptedFallbackUrls.clear()
            edgeProbeScheduler.scheduleDebounced(40L)
            scheduleThumbnailCapture(180L)

            val normalizedUrl = WebSnapshotColorExtractor.normalizeUrl(url)
            val cached = pageColorCache.get(normalizedUrl)
            if (cached != null) {
                lastColorExtractedUrl = normalizedUrl
                viewModel.onSnapshotColorReady(tabId, cached.color)
            } else if (lastColorExtractedUrl != normalizedUrl) {
                lastColorExtractedUrl = normalizedUrl
                scheduleSnapshotColorExtract(60L)
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        isMainFrameLoading = false
        viewModel.onPageLoadFinished(tabId)

        if (!url.isNullOrBlank() && url != "about:blank" && !isShowingErrorPage) {
            view?.evaluateJavascript(PdfInterceptorScript.SCRIPT, null)
            edgeProbeScheduler.scheduleDebounced(120L)
            edgeProbeScheduler.attachDomMutationObserver()
            scheduleThumbnailCapture(280L)

            val normalizedUrl = WebSnapshotColorExtractor.normalizeUrl(url)
            val cached = pageColorCache.get(normalizedUrl)
            if (cached != null && lastColorExtractedUrl != normalizedUrl) {
                lastColorExtractedUrl = normalizedUrl
                viewModel.onSnapshotColorReady(tabId, cached.color)
            } else {
                lastColorExtractedUrl = normalizedUrl
                scheduleSnapshotColorExtract(60L)
                // Silent follow-up pass for pages whose hero images/CSS hydrate shortly after load
                scheduleSilentSnapshotColorExtract(520L)
            }
        } else {
            viewModel.onRevealTimeout()
        }

        view?.let {
            viewModel.updateNavigationState(
                tabId = tabId,
                canGoBack = it.canGoBack(),
                canGoForward = it.canGoForward()
            )
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!url.isNullOrBlank() && url != "about:blank" && !url.startsWith("data:")) {
            val previousFullUrl = WebSnapshotColorExtractor.normalizeUrl(currentPageUrl)
            val newFullUrl = WebSnapshotColorExtractor.normalizeUrl(url)
            currentPageUrl = url
            onNavigatedUrl(url)

            if (isReload) {
                pageColorCache.remove(newFullUrl)
                lastColorExtractedUrl = ""
                if (!isShowingErrorPage && !isMainFrameLoading) {
                    lastColorExtractedUrl = newFullUrl
                    scheduleSilentSnapshotColorExtract(60L)
                }
            } else if (!isShowingErrorPage && !isMainFrameLoading && previousFullUrl != newFullUrl) {
                val cached = pageColorCache.get(newFullUrl)
                if (cached != null) {
                    WebSnapshotColorExtractor.cancel(view)
                    lastColorExtractedUrl = newFullUrl
                    viewModel.onSnapshotColorReady(tabId, cached.color)
                } else if (lastColorExtractedUrl != newFullUrl) {
                    lastColorExtractedUrl = newFullUrl
                    edgeProbeScheduler.scheduleDebounced(140L)
                    scheduleThumbnailCapture(280L)
                    scheduleSilentSnapshotColorExtract(160L)
                }
            }
        }
        view?.let {
            viewModel.updateNavigationState(
                tabId = tabId,
                canGoBack = it.canGoBack(),
                canGoForward = it.canGoForward()
            )
        }
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
        val failedUrl = error?.url?.takeIf { it.isNotBlank() } ?: currentPageUrl
        if (view != null && tryFallbackLoad(view, failedUrl, isDnsError = false)) {
            return
        }
        if (pendingFallbackUrl != null) return

        isMainFrameLoading = false
        viewModel.onPageLoadFinished(tabId)
        WebSnapshotColorExtractor.cancel(view)
        viewModel.onRevealTimeout()
        view?.let {
            renderLoadErrorPage(
                webView = it,
                failedUrl = failedUrl,
                title = "Connection is not secure",
                description = "The security certificate for this website could not be verified."
            )
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame != true) return

        val failedUrl = request.url?.toString().orEmpty()
        if (pendingFallbackUrl != null && failedUrl != pendingFallbackUrl) {
            return
        }

        val errorCode = error?.errorCode ?: ERROR_UNKNOWN
        if (errorCode == ERROR_UNSUPPORTED_SCHEME) return

        val isDnsError = errorCode == ERROR_HOST_LOOKUP
        if (view != null && shouldAttemptFallback(errorCode) && tryFallbackLoad(view, failedUrl, isDnsError)) {
            return
        }

        pendingFallbackUrl = null
        isMainFrameLoading = false
        viewModel.onPageLoadFinished(tabId)
        WebSnapshotColorExtractor.cancel(view)
        viewModel.onRevealTimeout()
        view?.let {
            val reason = error?.description?.toString().orEmpty().ifBlank { "ERR_CONNECTION_FAILED" }
            renderLoadErrorPage(
                webView = it,
                failedUrl = failedUrl,
                title = "This site can’t be reached",
                description = "Could not load $failedUrl ($reason)."
            )
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        isMainFrameLoading = false
        viewModel.onPageLoadFinished(tabId)
        WebSnapshotColorExtractor.cancel(view)
        onWebViewDestroyed()
        val deadView = view ?: return true

        val crashed = detail?.didCrash() == true
        consecutiveRenderCrashes = if (crashed) consecutiveRenderCrashes + 1 else 0
        val shouldReload = consecutiveRenderCrashes < 2

        viewModel.onRevealTimeout()
        tabWebViewPool.handleRenderProcessGone(
            tabId = tabId,
            deadWebView = deadView,
            shouldReload = shouldReload
        )
        return true
    }

    private fun shouldAttemptFallback(errorCode: Int): Boolean {
        return errorCode == ERROR_FAILED_SSL_HANDSHAKE ||
                errorCode == ERROR_CONNECT ||
                errorCode == ERROR_TIMEOUT ||
                errorCode == ERROR_IO ||
                errorCode == ERROR_HOST_LOOKUP ||
                errorCode == ERROR_UNKNOWN
    }

    private fun tryFallbackLoad(
        webView: WebView,
        failedUrl: String,
        isDnsError: Boolean
    ): Boolean {
        if (failedUrl.isBlank()) return false
        val parsed = runCatching { failedUrl.toUri() }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase().orEmpty()
        val host = parsed.host?.lowercase().orEmpty()
        if (host.isEmpty()) return false

        val candidateUrl: String? = when {
            isDnsError && !host.startsWith("www.") -> {
                parsed.buildUpon().authority("www.$host").build().toString()
            }
            scheme == "https" -> {
                parsed.buildUpon().scheme("http").build().toString()
            }
            !host.startsWith("www.") -> {
                parsed.buildUpon().scheme("http").authority("www.$host").build().toString()
            }
            else -> null
        }

        if (candidateUrl.isNullOrBlank() ||
            candidateUrl == failedUrl ||
            !attemptedFallbackUrls.add(candidateUrl)
        ) {
            return false
        }

        pendingFallbackUrl = candidateUrl
        currentPageUrl = candidateUrl
        onNavigatedUrl(candidateUrl)
        webView.post {
            webView.loadUrl(candidateUrl)
        }
        return true
    }

    private fun renderLoadErrorPage(
        webView: WebView,
        failedUrl: String,
        title: String,
        description: String
    ) {
        isShowingErrorPage = true
        pendingErrorPageUrl = failedUrl.takeIf { it.isNotBlank() }
        lastColorExtractedUrl = ""
        attemptedFallbackUrls.clear()
        val escapedUrl = escapeHtml(failedUrl)
        val escapedTitle = escapeHtml(title)
        val escapedDesc = escapeHtml(description)
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    :root { color-scheme: light dark; }
                    body {
                        font-family: system-ui, -apple-system, sans-serif;
                        margin: 0;
                        padding: 32px 24px;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        min-height: 75vh;
                        background: Canvas;
                        color: CanvasText;
                    }
                    h2 { font-size: 20px; margin: 0 0 10px; font-weight: 600; }
                    p { font-size: 14px; line-height: 1.5; opacity: 0.78; margin: 0 0 24px; word-break: break-word; }
                    button {
                        align-self: flex-start;
                        padding: 10px 22px;
                        border-radius: 999px;
                        border: none;
                        font-size: 14px;
                        font-weight: 600;
                        background: #3B5BA9;
                        color: #FFFFFF;
                    }
                </style>
            </head>
            <body>
                <h2>$escapedTitle</h2>
                <p>$escapedDesc</p>
                <button onclick="location.href='$escapedUrl'">Try again</button>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(
            failedUrl.ifBlank { "about:blank" },
            html,
            "text/html",
            "UTF-8",
            failedUrl.ifBlank { null }
        )
    }

    private fun escapeHtml(raw: String): String = raw.htmlEncode()

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val webResourceRequest = request ?: return super.shouldInterceptRequest(view, request as WebResourceRequest?)
        val reqUrl = webResourceRequest.url?.toString() ?: return super.shouldInterceptRequest(view, webResourceRequest)

        if (!reqUrl.startsWith("http://") && !reqUrl.startsWith("https://")) {
            return super.shouldInterceptRequest(view, webResourceRequest)
        }

        val pageUrl = if (webResourceRequest.isForMainFrame) {
            reqUrl
        } else {
            webResourceRequest.requestHeaders?.get("Referer") ?: currentPageUrl
        }

        val resourceType = determineResourceType(webResourceRequest)
        val isBlocked = viewModel.checkResourceBlocking(reqUrl, pageUrl, resourceType)

        if (isBlocked) {
            if (webResourceRequest.isForMainFrame) {
                viewModel.onPageLoadFinished(tabId)
                viewModel.onRevealTimeout()
                val blockedHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <meta name="color-scheme" content="light dark">
                    </head>
                    <body style="font-family:system-ui;padding:32px 24px;background:Canvas;color:CanvasText;">
                        <h3>Page blocked by Verve Shield</h3>
                        <p style="opacity:0.75;word-break:break-all;">${escapeHtml(reqUrl)}</p>
                    </body>
                    </html>
                """.trimIndent()
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    200,
                    "OK",
                    null,
                    ByteArrayInputStream(blockedHtml.toByteArray(Charsets.UTF_8))
                )
            }

            val (mimeType, bodyBytes) = when (resourceType) {
                "script" -> "application/javascript" to ByteArray(0)
                "stylesheet" -> "text/css" to ByteArray(0)
                "xmlhttprequest" -> "application/json" to "{}".toByteArray(Charsets.UTF_8)
                else -> "text/plain" to ByteArray(0)
            }

            return WebResourceResponse(
                mimeType,
                "UTF-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(bodyBytes)
            )
        }

        return super.shouldInterceptRequest(view, webResourceRequest)
    }

    @Suppress("SpellCheckingInspection")
    private fun determineResourceType(request: WebResourceRequest): String {
        if (request.isForMainFrame) return "main_frame"

        val acceptHeader = request.requestHeaders?.get("Accept") ?: ""
        val path = request.url?.path?.lowercase() ?: ""

        return when {
            acceptHeader.contains("text/css") || path.endsWith(".css") -> "stylesheet"
            acceptHeader.contains("image/") || path.matches(Regex(".*\\.(png|jpg|jpeg|gif|webp|svg|ico).*")) -> "image"
            acceptHeader.contains("javascript") || path.endsWith(".js") -> "script"
            acceptHeader.contains("font/") || path.matches(Regex(".*\\.(woff|woff2|ttf|otf).*")) -> "font"
            acceptHeader.contains("application/json") || request.requestHeaders?.containsKey("X-Requested-With") == true -> "xmlhttprequest"
            else -> "other"
        }
    }
}