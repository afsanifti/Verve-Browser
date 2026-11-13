package com.verve.browser.presentation.browser.components.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool

@SuppressLint("SetJavaScriptEnabled")
internal fun createTabWebView(
    context: Context,
    tabId: String,
    viewModel: BrowserViewModel,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache
): WebView {
    return WebView(context).apply {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        val bottomCoordinator = DocumentBottomScrollCoordinator(this, tabId, viewModel)
        val edgeProbeScheduler = EdgeProbeScheduler(this, tabId, viewModel, bottomCoordinator)
        val thumbnailHandler = Handler(Looper.getMainLooper())
        val captureThumbnailRunnable = Runnable {
            thumbnailCache.captureFromWebView(tabId, this)
        }
        val scheduleThumbnailCapture: (Long) -> Unit = { delayMs ->
            thumbnailHandler.removeCallbacks(captureThumbnailRunnable)
            thumbnailHandler.postDelayed(captureThumbnailRunnable, delayMs)
        }

        var accumulatedScrollDy = 0
        val scrollThresholdPx = (24 * context.resources.displayMetrics.density).toInt()

        setOnScrollChangeListener { _, _, verticalScrollOffset, _, oldVerticalScrollOffset ->
            val dy = verticalScrollOffset - oldVerticalScrollOffset
            bottomCoordinator.onScrollChanged(verticalScrollOffset = verticalScrollOffset)
            edgeProbeScheduler.onScroll()

            if (verticalScrollOffset <= 0) {
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

        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = (right - left) != (oldRight - oldLeft)
            val heightChanged = (bottom - top) != (oldBottom - oldTop)
            if (widthChanged || heightChanged) {
                bottomCoordinator.onViewportChanged()
                edgeProbeScheduler.scheduleDebounced(80L)
            }
        }

        webViewClient = VerveWebViewClient(
            tabId = tabId,
            viewModel = viewModel,
            edgeProbeScheduler = edgeProbeScheduler,
            bottomCoordinator = bottomCoordinator,
            scheduleThumbnailCapture = scheduleThumbnailCapture,
            onNavigatedUrl = { navigatedUrl ->
                tabWebViewPool.setLastRequestedUrl(tabId, navigatedUrl)
                viewModel.onPageUrlUpdated(tabId, navigatedUrl)
            }
        )

        webChromeClient = VerveWebChromeClient(
            tabId = tabId,
            viewModel = viewModel,
            edgeProbeScheduler = edgeProbeScheduler,
            scheduleThumbnailCapture = scheduleThumbnailCapture
        )
    }
}