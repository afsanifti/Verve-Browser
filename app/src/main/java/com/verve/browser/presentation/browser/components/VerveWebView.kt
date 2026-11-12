package com.verve.browser.presentation.browser.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.WebSnapshotColorExtractor
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerveWebView(
    url: String,
    viewModel: BrowserViewModel,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastLoadedUrl by remember { mutableStateOf("") }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                var accumulatedScrollDy = 0
                val scrollThresholdPx = (24 * context.resources.displayMetrics.density).toInt()

                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val dy = scrollY - oldScrollY
                    if (scrollY <= 0) {
                        accumulatedScrollDy = 0
                        viewModel.setToolbarCollapsed(false)
                        return@setOnScrollChangeListener
                    }

                    if ((dy > 0 && accumulatedScrollDy < 0) || (dy < 0 && accumulatedScrollDy > 0)) {
                        accumulatedScrollDy = 0
                    }
                    accumulatedScrollDy += dy

                    if (accumulatedScrollDy > scrollThresholdPx) {
                        viewModel.setToolbarCollapsed(true)
                        accumulatedScrollDy = 0
                    } else if (accumulatedScrollDy < -scrollThresholdPx) {
                        viewModel.setToolbarCollapsed(false)
                        accumulatedScrollDy = 0
                    }
                }

                webViewClient = CustomWebViewClient(
                    viewModel = viewModel,
                    onNavigatedUrl = { navigatedUrl ->
                        lastLoadedUrl = navigatedUrl
                        viewModel.onPageUrlUpdated(navigatedUrl)
                    }
                )

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        viewModel.onProgressChanged(newProgress)
                    }
                }

                if (url.isNotEmpty()) {
                    lastLoadedUrl = url
                    loadUrl(url)
                }

                onWebViewCreated(this)
            }
        },
        update = { webView ->
            if (url.isNotEmpty() && url != lastLoadedUrl) {
                lastLoadedUrl = url
                webView.loadUrl(url)
            }
        }
    )
}

private class CustomWebViewClient(
    private val viewModel: BrowserViewModel,
    private val onNavigatedUrl: (String) -> Unit
) : WebViewClient() {

    @Volatile
    private var currentPageUrl: String = ""

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!url.isNullOrBlank() && url != "about:blank") {
            currentPageUrl = url
            onNavigatedUrl(url)
            viewModel.setToolbarCollapsed(false)
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        if (!url.isNullOrBlank() && url != "about:blank") {
            WebSnapshotColorExtractor.extractFromWebView(view, delayMs = 80L) { extractedColor ->
                viewModel.onSnapshotColorReady(extractedColor)
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrBlank() && url != "about:blank") {
            WebSnapshotColorExtractor.extractFromWebView(view, delayMs = 350L) { extractedColor ->
                viewModel.onSnapshotColorReady(extractedColor)
            }
        }

        view?.let {
            viewModel.updateNavigationState(
                canGoBack = it.canGoBack(),
                canGoForward = it.canGoForward()
            )
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!url.isNullOrBlank() && url != "about:blank") {
            onNavigatedUrl(url)
        }
        if (!isReload) {
            WebSnapshotColorExtractor.extractFromWebView(view, delayMs = 400L) { extractedColor ->
                if (extractedColor != null) {
                    viewModel.updateSiteSeedColor(extractedColor)
                }
            }
        }
        view?.let {
            viewModel.updateNavigationState(
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