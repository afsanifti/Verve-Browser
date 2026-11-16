package com.verve.browser.presentation.browser.components.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.TabPageColorCache
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool
import com.verve.browser.presentation.browser.util.WebSnapshotColorExtractor

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
internal fun createTabWebView(
    context: Context,
    tabId: String,
    viewModel: BrowserViewModel,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache
): WebView {
    return WebView(context).apply {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isNestedScrollingEnabled = true
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString.replace("; wv)", ")")
        }

        val mainHandler = Handler(Looper.getMainLooper())
        val pageColorCache = TabPageColorCache(maxEntries = 10)
        var isDestroyed = false
        var isTouchActive = false
        var isFlingScrolling = false
        var isColorExtractionInFlight = false
        var pendingThumbnailAfterScroll = false
        var pendingSnapshotColorAfterScroll = false
        var pendingEdgeProbeAfterScroll = false
        var lastRequestedCollapsed: Boolean? = null
        var lastDomSnapshotTimestampMs = 0L

        val isScrollBusy: () -> Boolean = { isTouchActive || isFlingScrolling }
        val canSafelyAccessWebView: () -> Boolean = {
            !isDestroyed && isAttachedToWindow && windowToken != null
        }

        val captureThumbnailRunnable = Runnable {
            if (!canSafelyAccessWebView()) return@Runnable
            if (isScrollBusy()) {
                pendingThumbnailAfterScroll = true
                return@Runnable
            }
            thumbnailCache.captureFromWebView(tabId, this@apply)
        }

        val scheduleThumbnailCapture: (Long) -> Unit = { delayMs ->
            if (!isDestroyed) {
                mainHandler.removeCallbacks(captureThumbnailRunnable)
                if (isScrollBusy()) {
                    pendingThumbnailAfterScroll = true
                } else {
                    mainHandler.postDelayed(captureThumbnailRunnable, delayMs)
                }
            }
        }

        val extractSnapshotColorRunnable = Runnable {
            if (!canSafelyAccessWebView()) {
                viewModel.onSnapshotColorReady(tabId, null)
                return@Runnable
            }
            if (isScrollBusy()) {
                pendingSnapshotColorAfterScroll = true
                viewModel.onSnapshotColorReady(tabId, null)
                return@Runnable
            }
            val targetUrl = this@apply.url.orEmpty()
            isColorExtractionInFlight = true
            WebSnapshotColorExtractor.extractFromWebView(this@apply, delayMs = 0L) { extractedColor ->
                isColorExtractionInFlight = false
                if (!isDestroyed) {
                    val resolvedUrl = targetUrl.ifBlank { this@apply.url.orEmpty() }
                    if (resolvedUrl.isNotBlank() && extractedColor != null) {
                        pageColorCache.put(resolvedUrl, extractedColor)
                    }
                    viewModel.onSnapshotColorReady(tabId, extractedColor)
                }
            }
        }

        val silentSnapshotColorRunnable = Runnable {
            if (!canSafelyAccessWebView()) return@Runnable
            if (isScrollBusy()) {
                pendingSnapshotColorAfterScroll = true
                return@Runnable
            }
            val targetUrl = this@apply.url.orEmpty()
            isColorExtractionInFlight = true
            WebSnapshotColorExtractor.extractFromWebView(this@apply, delayMs = 0L) { extractedColor ->
                isColorExtractionInFlight = false
                if (!isDestroyed && extractedColor != null) {
                    val resolvedUrl = targetUrl.ifBlank { this@apply.url.orEmpty() }
                    if (resolvedUrl.isNotBlank()) {
                        pageColorCache.put(resolvedUrl, extractedColor)
                    }
                    viewModel.onSnapshotColorReady(tabId, extractedColor)
                }
            }
        }

        val scheduleSnapshotColorExtract: (Long) -> Unit = { delayMs ->
            if (!isDestroyed) {
                mainHandler.removeCallbacks(extractSnapshotColorRunnable)
                if (isScrollBusy()) {
                    pendingSnapshotColorAfterScroll = true
                    viewModel.onSnapshotColorReady(tabId, null)
                } else {
                    mainHandler.postDelayed(extractSnapshotColorRunnable, minOf(delayMs, 60L))
                }
            }
        }

        val scheduleSilentSnapshotColorExtract: (Long) -> Unit = { delayMs ->
            if (!isDestroyed) {
                mainHandler.removeCallbacks(silentSnapshotColorRunnable)
                if (isScrollBusy()) {
                    pendingSnapshotColorAfterScroll = true
                } else {
                    mainHandler.postDelayed(silentSnapshotColorRunnable, delayMs)
                }
            }
        }

        val domMutationSnapshotRunnable = Runnable {
            if (!canSafelyAccessWebView() || isScrollBusy()) return@Runnable
            val now = SystemClock.uptimeMillis()
            if (now - lastDomSnapshotTimestampMs < 1100L) return@Runnable
            lastDomSnapshotTimestampMs = now

            val currentUrl = this@apply.url.orEmpty()
            if (currentUrl.isNotBlank()) {
                pageColorCache.remove(currentUrl)
            }
            scheduleSilentSnapshotColorExtract(0L)
        }

        val onDomVisualMutation: () -> Unit = {
            if (!isDestroyed && !isScrollBusy()) {
                mainHandler.removeCallbacks(domMutationSnapshotRunnable)
                mainHandler.postDelayed(domMutationSnapshotRunnable, 650L)
            }
        }

        val bottomCoordinator = DocumentBottomScrollCoordinator(this, tabId, viewModel)
        val edgeProbeScheduler = EdgeProbeScheduler(
            webView = this,
            tabId = tabId,
            viewModel = viewModel,
            bottomCoordinator = bottomCoordinator,
            isActivelyScrolling = isScrollBusy,
            canRunProbe = canSafelyAccessWebView,
            onDomVisualMutation = onDomVisualMutation
        )

        val scrollIdleRunnable = Runnable {
            isFlingScrolling = false
            if (isTouchActive || !canSafelyAccessWebView()) return@Runnable

            if (pendingEdgeProbeAfterScroll) {
                pendingEdgeProbeAfterScroll = false
                edgeProbeScheduler.scheduleDebounced(60L)
            }
            if (pendingThumbnailAfterScroll) {
                pendingThumbnailAfterScroll = false
                scheduleThumbnailCapture(140L)
            }
            if (pendingSnapshotColorAfterScroll) {
                pendingSnapshotColorAfterScroll = false
                scheduleSilentSnapshotColorExtract(140L)
            }
        }

        val bumpScrollIdleTimer: (Long) -> Unit = { delayMs ->
            mainHandler.removeCallbacks(scrollIdleRunnable)
            mainHandler.postDelayed(scrollIdleRunnable, delayMs)
        }

        val cancelSnapshotsForScroll: () -> Unit = {
            mainHandler.removeCallbacks(captureThumbnailRunnable)
            mainHandler.removeCallbacks(extractSnapshotColorRunnable)
            mainHandler.removeCallbacks(silentSnapshotColorRunnable)
            mainHandler.removeCallbacks(domMutationSnapshotRunnable)
            if (isColorExtractionInFlight) {
                isColorExtractionInFlight = false
                pendingSnapshotColorAfterScroll = true
                viewModel.onSnapshotColorReady(tabId, null)
            }
            WebSnapshotColorExtractor.cancel(this@apply)
        }

        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (!isTouchActive) {
                        isTouchActive = true
                        cancelSnapshotsForScroll()
                    }
                    mainHandler.removeCallbacks(scrollIdleRunnable)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTouchActive = false
                    bumpScrollIdleTimer(220L)
                }
            }
            false
        }

        val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop
        val collapseThresholdPx = maxOf(touchSlopPx, (24 * context.resources.displayMetrics.density).toInt())
        var accumulatedScrollDy = 0

        val updateToolbarCollapsedIfChanged: (Boolean) -> Unit = { collapsed ->
            if (lastRequestedCollapsed != collapsed ||
                viewModel.uiState.value.isToolbarCollapsed != collapsed
            ) {
                lastRequestedCollapsed = collapsed
                viewModel.setToolbarCollapsed(collapsed)
            }
        }

        setOnScrollChangeListener { _, _, verticalScrollOffset, _, oldVerticalScrollOffset ->
            val dy = verticalScrollOffset - oldVerticalScrollOffset
            if (dy == 0) return@setOnScrollChangeListener

            isFlingScrolling = true
            cancelSnapshotsForScroll()
            bumpScrollIdleTimer(240L)

            bottomCoordinator.onScrollChanged(verticalScrollOffset = verticalScrollOffset)
            edgeProbeScheduler.onScroll()

            if (verticalScrollOffset <= 0) {
                accumulatedScrollDy = 0
                updateToolbarCollapsedIfChanged(false)
                return@setOnScrollChangeListener
            }

            if ((dy > 0 && accumulatedScrollDy < 0) || (dy < 0 && accumulatedScrollDy > 0)) {
                accumulatedScrollDy = 0
            }
            accumulatedScrollDy += dy

            if (accumulatedScrollDy > collapseThresholdPx) {
                updateToolbarCollapsedIfChanged(true)
                accumulatedScrollDy = 0
            } else if (accumulatedScrollDy < -collapseThresholdPx) {
                updateToolbarCollapsedIfChanged(false)
                accumulatedScrollDy = 0
            }
        }

        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (isDestroyed) return@addOnLayoutChangeListener
            val widthChanged = (right - left) != (oldRight - oldLeft)
            val heightChanged = (bottom - top) != (oldBottom - oldTop)
            if (widthChanged || heightChanged) {
                bottomCoordinator.onViewportChanged()
                if (isScrollBusy()) {
                    pendingEdgeProbeAfterScroll = true
                } else {
                    edgeProbeScheduler.scheduleDebounced(140L)
                }
            }
        }

        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                isTouchActive = false
                isFlingScrolling = false
                isColorExtractionInFlight = false
                mainHandler.removeCallbacksAndMessages(null)
                edgeProbeScheduler.cancelPending()
                WebSnapshotColorExtractor.cancel(this@apply)
            }
        })

        webViewClient = VerveWebViewClient(
            tabId = tabId,
            viewModel = viewModel,
            tabWebViewPool = tabWebViewPool,
            pageColorCache = pageColorCache,
            edgeProbeScheduler = edgeProbeScheduler,
            bottomCoordinator = bottomCoordinator,
            scheduleThumbnailCapture = scheduleThumbnailCapture,
            scheduleSnapshotColorExtract = scheduleSnapshotColorExtract,
            scheduleSilentSnapshotColorExtract = scheduleSilentSnapshotColorExtract,
            onNavigatedUrl = { navigatedUrl ->
                lastRequestedCollapsed = false
                accumulatedScrollDy = 0
                tabWebViewPool.setLastRequestedUrl(tabId, navigatedUrl)
                viewModel.onPageUrlUpdated(tabId, navigatedUrl)
            },
            onWebViewDestroyed = {
                isDestroyed = true
                isTouchActive = false
                isFlingScrolling = false
                isColorExtractionInFlight = false
                mainHandler.removeCallbacksAndMessages(null)
                edgeProbeScheduler.dispose()
                WebSnapshotColorExtractor.cancel(this@apply)
                pageColorCache.clear()
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