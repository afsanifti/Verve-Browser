package com.verve.browser.presentation.browser.components.webview

import android.webkit.WebChromeClient
import android.webkit.WebView
import com.verve.browser.presentation.browser.BrowserViewModel

internal class VerveWebChromeClient(
    private val tabId: String,
    private val viewModel: BrowserViewModel,
    private val edgeProbeScheduler: EdgeProbeScheduler,
    private val scheduleThumbnailCapture: (Long) -> Unit
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
}