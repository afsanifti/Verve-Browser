package com.verve.browser.presentation.browser.components.webview

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.WebSnapshotColorExtractor
import java.io.ByteArrayInputStream

internal class VerveWebViewClient(
    private val tabId: String,
    private val viewModel: BrowserViewModel,
    private val edgeProbeScheduler: EdgeProbeScheduler,
    private val bottomCoordinator: DocumentBottomScrollCoordinator,
    private val scheduleThumbnailCapture: (Long) -> Unit,
    private val onNavigatedUrl: (String) -> Unit
) : WebViewClient() {

    @Volatile
    private var currentPageUrl: String = ""

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank() && url != "about:blank") {
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
        if (!url.isNullOrBlank() && url != "about:blank") {
            edgeProbeScheduler.scheduleDebounced(30L)
            scheduleThumbnailCapture(120L)
            WebSnapshotColorExtractor.extractFromWebView(view, delayMs = 80L) { extractedColor ->
                viewModel.onSnapshotColorReady(tabId, extractedColor)
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrBlank() && url != "about:blank") {
            edgeProbeScheduler.scheduleDebounced(140L)
            edgeProbeScheduler.attachDomMutationObserver()
            scheduleThumbnailCapture(280L)
            WebSnapshotColorExtractor.extractFromWebView(view, delayMs = 350L) { extractedColor ->
                viewModel.onSnapshotColorReady(tabId, extractedColor)
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

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!url.isNullOrBlank() && url != "about:blank") {
            onNavigatedUrl(url)
            edgeProbeScheduler.scheduleDebounced(160L)
            scheduleThumbnailCapture(240L)
        }
        if (!isReload) {
            WebSnapshotColorExtractor.extractFromWebView(view, delayMs = 400L) { extractedColor ->
                if (extractedColor != null) {
                    viewModel.updateSiteSeedColor(tabId, extractedColor)
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
            currentPageUrl = reqUrl
            reqUrl
        } else {
            webResourceRequest.requestHeaders?.get("Referer") ?: currentPageUrl
        }

        val resourceType = determineResourceType(webResourceRequest)
        val isBlocked = viewModel.checkResourceBlocking(reqUrl, pageUrl, resourceType)

        if (isBlocked) {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                200,
                "OK",
                null,
                ByteArrayInputStream(ByteArray(0))
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