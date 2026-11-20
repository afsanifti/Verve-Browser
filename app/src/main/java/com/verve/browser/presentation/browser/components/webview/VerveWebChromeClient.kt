package com.verve.browser.presentation.browser.components.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.verve.browser.presentation.browser.BrowserViewModel

internal class VerveWebChromeClient(
    private val tabId: String,
    private val viewModel: BrowserViewModel,
    private val edgeProbeScheduler: EdgeProbeScheduler,
    private val scheduleThumbnailCapture: (Long) -> Unit,
    private val onShowCustomViewCallback: (View?, CustomViewCallback?) -> Unit = { _, _ -> },
    private val onHideCustomViewCallback: () -> Unit = {}
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        viewModel.onProgressChanged(tabId, newProgress)
        if (newProgress == 100) {
            edgeProbeScheduler.scheduleDebounced(80L)
            scheduleThumbnailCapture(260L)
        }
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrBlank()) {
            viewModel.onPageTitleUpdated(tabId, title)
        }
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        onShowCustomViewCallback(view, callback)
    }

    override fun onHideCustomView() {
        onHideCustomViewCallback()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val hostView = view ?: return false

        // Fast path for standard <a target="_blank" href="..."> links
        val hitResult = hostView.hitTestResult
        if (hitResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            val href = hitResult.extra
            if (!href.isNullOrBlank() && href != "about:blank") {
                viewModel.openBlankTargetUrl(url = href, sourceTabId = tabId)
                return true
            }
        }

        // Fallback for JS window.open(...) and wrapped image anchors
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        var handled = false
        val resolverWebView = WebView(hostView.context)
        var timeoutRunnable: Runnable? = null

        fun completeAndDestroy(targetUrl: String?) {
            if (handled) return
            handled = true
            timeoutRunnable?.let(hostView::removeCallbacks)
            if (!targetUrl.isNullOrBlank() && targetUrl != "about:blank") {
                viewModel.openBlankTargetUrl(url = targetUrl, sourceTabId = tabId)
            }
            runCatching {
                resolverWebView.stopLoading()
                resolverWebView.destroy()
            }
        }

        resolverWebView.apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString()
                    if (!targetUrl.isNullOrBlank() && targetUrl != "about:blank") {
                        completeAndDestroy(targetUrl)
                    }
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (!url.isNullOrBlank() && url != "about:blank") {
                        completeAndDestroy(url)
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    completeAndDestroy(null)
                    return true
                }
            }
        }

        // Safety cleanup in case window.open() never navigates
        timeoutRunnable = Runnable { completeAndDestroy(null) }.also {
            hostView.postDelayed(it, 4000L)
        }

        transport.webView = resolverWebView
        resultMsg.sendToTarget()
        return true
    }
}