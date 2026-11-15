package com.verve.browser.presentation.browser.components.webview

import android.webkit.WebView
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.WebScrollMetrics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal class DocumentBottomScrollCoordinator(
    private val webView: WebView,
    private val tabId: String,
    private val viewModel: BrowserViewModel
) {
    private val density = webView.resources.displayMetrics.density
    private val approachZonePx = (96f * density).coerceAtLeast(1f)
    private val lookaheadZonePx = (220f * density).roundToInt()
    private val minScrollableRangePx = (16f * density).roundToInt()

    private var lastReportedProgress: Float = 0f
    private var lastJsScrollMetrics: WebScrollMetrics? = null
    @Volatile
    private var inBottomLookahead: Boolean = false

    fun isInBottomLookaheadZone(): Boolean = inBottomLookahead

    fun resetForNavigation() {
        lastReportedProgress = 0f
        lastJsScrollMetrics = null
        inBottomLookahead = false
        if (viewModel.uiState.value.activeTabId == tabId) {
            viewModel.updateBottomCurtainProgress(0f)
            viewModel.updateHasFixedBottomBar(false)
        }
    }

    fun onJsScrollMetricsUpdated(metrics: WebScrollMetrics?) {
        if (metrics == null) return
        lastJsScrollMetrics = metrics
        evaluateScrollPosition(verticalScrollOffset = max(webView.scrollY, metrics.scrollOffsetPx))
    }

    fun onViewportChanged() {
        evaluateScrollPosition(verticalScrollOffset = webView.scrollY)
    }

    fun onScrollChanged(verticalScrollOffset: Int) {
        evaluateScrollPosition(verticalScrollOffset = verticalScrollOffset)
    }

    private fun evaluateScrollPosition(verticalScrollOffset: Int) {
        if (viewModel.uiState.value.activeTabId != tabId) return
        val viewportHeightPx = webView.height
        if (viewportHeightPx <= 0) return

        val androidContentHeightPx = computeAndroidContentHeightPx()
        val jsContentHeightPx = lastJsScrollMetrics?.contentHeightPx ?: 0
        val totalContentHeightPx = max(androidContentHeightPx, jsContentHeightPx)

        val maxScrollPx = totalContentHeightPx - viewportHeightPx
        if (maxScrollPx <= minScrollableRangePx) {
            inBottomLookahead = false
            reportProgress(0f)
            return
        }

        val clampedScrollOffset = verticalScrollOffset.coerceIn(0, maxScrollPx)
        val remainingToBottomPx = (maxScrollPx - clampedScrollOffset).coerceAtLeast(0)
        inBottomLookahead = remainingToBottomPx <= lookaheadZonePx

        val progress = if (remainingToBottomPx >= approachZonePx) {
            0f
        } else {
            (1f - (remainingToBottomPx.toFloat() / approachZonePx)).coerceIn(0f, 1f)
        }

        reportProgress(progress)
    }

    @Suppress("DEPRECATION")
    private fun computeAndroidContentHeightPx(): Int {
        val cssScaleHeight = (webView.contentHeight * webView.scale).roundToInt()
        return max(cssScaleHeight, webView.scrollY + webView.height)
    }

    private fun reportProgress(progress: Float) {
        val normalized = when {
            progress < 0.005f -> 0f
            progress > 0.995f -> 1f
            else -> progress
        }
        if (abs(lastReportedProgress - normalized) < 0.002f) return
        lastReportedProgress = normalized
        viewModel.updateBottomCurtainProgress(normalized)
    }
}