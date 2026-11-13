package com.verve.browser.presentation.browser.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.WebEdgeColorExtractor
import com.verve.browser.presentation.browser.util.WebScrollMetrics
import com.verve.browser.presentation.browser.util.WebSnapshotColorExtractor
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerveWebView(
    url: String,
    viewModel: BrowserViewModel,
    floorHeightPx: Int,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastLoadedUrl by remember { mutableStateOf("") }
    var scrollCoordinatorRef by remember { mutableStateOf<DocumentBottomScrollCoordinator?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // Prevent WebView from applying system statusBar/cutout insets a second time
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

                val bottomCoordinator = DocumentBottomScrollCoordinator(this, viewModel)
                scrollCoordinatorRef = bottomCoordinator
                val edgeProbeScheduler = EdgeProbeScheduler(this, viewModel, bottomCoordinator)

                var accumulatedScrollDy = 0
                val scrollThresholdPx = (24 * context.resources.displayMetrics.density).toInt()
                var isCompensatingFloorScroll = false

                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    if (isCompensatingFloorScroll) return@setOnScrollChangeListener

                    val dy = scrollY - oldScrollY
                    bottomCoordinator.onScrollChanged(scrollY = scrollY, dy = dy)
                    edgeProbeScheduler.onScroll()

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

                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val oldWidth = oldRight - oldLeft
                    val newWidth = right - left
                    val oldHeight = oldBottom - oldTop
                    val newHeight = bottom - top

                    if (oldWidth > 0 && oldHeight > 0 && oldWidth == newWidth && oldHeight != newHeight) {
                        val heightDelta = oldHeight - newHeight
                        val pendingCompensation = bottomCoordinator.consumePendingFloorDelta()
                        if (pendingCompensation != 0 && abs(heightDelta - pendingCompensation) <= 4) {
                            isCompensatingFloorScroll = true
                            try {
                                scrollBy(0, heightDelta)
                            } finally {
                                isCompensatingFloorScroll = false
                            }
                        }
                    }

                    if (newWidth != oldWidth || newHeight != oldHeight) {
                        bottomCoordinator.onViewportChanged()
                        edgeProbeScheduler.scheduleDebounced(100L)
                    }
                }

                webViewClient = CustomWebViewClient(
                    viewModel = viewModel,
                    edgeProbeScheduler = edgeProbeScheduler,
                    bottomCoordinator = bottomCoordinator,
                    onNavigatedUrl = { navigatedUrl ->
                        lastLoadedUrl = navigatedUrl
                        viewModel.onPageUrlUpdated(navigatedUrl)
                    }
                )

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        viewModel.onProgressChanged(newProgress)
                        if (newProgress == 100) {
                            edgeProbeScheduler.scheduleDebounced(100L)
                        }
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
            scrollCoordinatorRef?.updateCurrentFloorPx(floorHeightPx)

            if (url.isNotEmpty() && url != lastLoadedUrl) {
                lastLoadedUrl = url
                scrollCoordinatorRef?.resetForNavigation()
                webView.loadUrl(url)
            }
        }
    )
}

/**
 * Coordinates document-bottom detection using normalized WebView view-pixel coordinates
 * with hysteresis, frame-locked scroll compensation, and dynamic content handling.
 */
private class DocumentBottomScrollCoordinator(
    private val webView: WebView,
    private val viewModel: BrowserViewModel
) {
    private val density = webView.resources.displayMetrics.density
    private val bottomTriggerThresholdPx = (4f * density).roundToInt()
    private val upwardExitHysteresisPx = (8f * density).roundToInt()
    private val reentryClearancePx = (16f * density).roundToInt()
    private val minScrollableRangePx = (16f * density).roundToInt()

    private var currentFloorPx: Int = 0
    private var previousFloorPx: Int = 0
    private var pendingFloorDeltaPx: Int = 0
    private var isAtBottomState: Boolean = false
    private var lastJsScrollMetrics: WebScrollMetrics? = null
    private var lastObservedContentHeightPx: Int = 0
    private var suppressReentryUntilUpwardClear: Boolean = false

    fun updateCurrentFloorPx(floorPx: Int) {
        val clamped = floorPx.coerceAtLeast(0)
        val delta = clamped - previousFloorPx
        if (delta != 0) {
            pendingFloorDeltaPx += delta
            previousFloorPx = clamped
        }
        currentFloorPx = clamped
    }

    fun consumePendingFloorDelta(): Int {
        val delta = pendingFloorDeltaPx
        pendingFloorDeltaPx = 0
        return delta
    }

    fun resetForNavigation() {
        currentFloorPx = 0
        previousFloorPx = 0
        pendingFloorDeltaPx = 0
        isAtBottomState = false
        lastJsScrollMetrics = null
        lastObservedContentHeightPx = 0
        suppressReentryUntilUpwardClear = false
        viewModel.setAtPageBottom(false)
    }

    fun onJsScrollMetricsUpdated(metrics: WebScrollMetrics?) {
        if (metrics == null) return
        val prevContentHeight = lastObservedContentHeightPx
        lastJsScrollMetrics = metrics

        // Retract curtain automatically if infinite scroll / dynamic DOM expands the page
        if (isAtBottomState && prevContentHeight > 0 && metrics.contentHeightPx > prevContentHeight + reentryClearancePx) {
            val fullViewportPx = webView.height + currentFloorPx
            val remainingToUnflooredBottom = metrics.contentHeightPx - (metrics.scrollOffsetPx + fullViewportPx)
            if (remainingToUnflooredBottom > reentryClearancePx) {
                setBottomState(false)
            }
        }
        lastObservedContentHeightPx = metrics.contentHeightPx
        evaluateScrollPosition(scrollY = max(webView.scrollY, metrics.scrollOffsetPx), dy = 0)
    }

    fun onViewportChanged() {
        evaluateScrollPosition(scrollY = webView.scrollY, dy = 0)
    }

    fun onScrollChanged(scrollY: Int, dy: Int) {
        evaluateScrollPosition(scrollY = scrollY, dy = dy)
    }

    private fun evaluateScrollPosition(scrollY: Int, dy: Int) {
        val currentViewHeight = webView.height
        if (currentViewHeight <= 0) return

        val fullViewportHeightPx = currentViewHeight + currentFloorPx
        val androidContentHeightPx = computeAndroidContentHeightPx()
        val jsContentHeightPx = lastJsScrollMetrics?.contentHeightPx ?: 0
        val totalContentHeightPx = max(androidContentHeightPx, jsContentHeightPx)
        lastObservedContentHeightPx = totalContentHeightPx

        val isScrollable = totalContentHeightPx > fullViewportHeightPx + minScrollableRangePx
        if (!isScrollable) {
            if (isAtBottomState) {
                setBottomState(false)
            }
            return
        }

        if (!isAtBottomState) {
            val distanceToUnflooredBottomPx = totalContentHeightPx - (scrollY + fullViewportHeightPx)
            if (suppressReentryUntilUpwardClear) {
                if (distanceToUnflooredBottomPx > reentryClearancePx || dy > 0) {
                    suppressReentryUntilUpwardClear = false
                } else {
                    return
                }
            }

            if (distanceToUnflooredBottomPx <= bottomTriggerThresholdPx && dy >= 0) {
                setBottomState(true)
            }
        } else {
            val flooredMaxScrollPx = (totalContentHeightPx - currentViewHeight).coerceAtLeast(0)
            val upwardDistanceFromFlooredBottomPx = flooredMaxScrollPx - scrollY

            if (dy < 0 && upwardDistanceFromFlooredBottomPx > upwardExitHysteresisPx) {
                suppressReentryUntilUpwardClear = true
                setBottomState(false)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun computeAndroidContentHeightPx(): Int {
        val cssScaleHeight = (webView.contentHeight * webView.scale).roundToInt()
        return max(cssScaleHeight, webView.scrollY + webView.height)
    }

    private fun setBottomState(atBottom: Boolean) {
        if (isAtBottomState == atBottom) return
        isAtBottomState = atBottom
        viewModel.setAtPageBottom(atBottom)
    }
}

private class EdgeProbeScheduler(
    private val webView: WebView,
    private val viewModel: BrowserViewModel,
    private val bottomCoordinator: DocumentBottomScrollCoordinator
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastProbeTimestampMs = 0L
    private var activePort: WebMessagePort? = null

    private val probeRunnable = Runnable {
        lastProbeTimestampMs = SystemClock.uptimeMillis()
        WebEdgeColorExtractor.extractEdgeProbe(webView) { probeResult ->
            viewModel.updateTopRegionColor(probeResult.topColor)
            viewModel.updateBottomRegionColor(probeResult.bottomColor)
            bottomCoordinator.onJsScrollMetricsUpdated(probeResult.scrollMetrics)
        }
    }

    fun scheduleDebounced(delayMs: Long) {
        handler.removeCallbacks(probeRunnable)
        handler.postDelayed(probeRunnable, delayMs)
    }

    fun onScroll() {
        val now = SystemClock.uptimeMillis()
        if (now - lastProbeTimestampMs >= 140L) {
            handler.removeCallbacks(probeRunnable)
            probeRunnable.run()
        }
        handler.removeCallbacks(probeRunnable)
        handler.postDelayed(probeRunnable, 120L)
    }

    fun attachDomMutationObserver() {
        if (!webView.isAttachedToWindow) return
        runCatching {
            activePort?.close()
            val channel = webView.createWebMessageChannel()
            val nativePort = channel[0]
            val jsPort = channel[1]
            activePort = nativePort

            nativePort.setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                override fun onMessage(port: WebMessagePort?, message: WebMessage?) {
                    scheduleDebounced(200L)
                }
            }, handler)

            val initObserverJs = """
                (() => {
                    if (window.__verveEdgePort) {
                        try { window.__verveEdgePort.close(); } catch (_) {}
                    }
                    window.addEventListener('message', function onInit(e) {
                        if (e.data === '__verve_edge_init__' && e.ports && e.ports[0]) {
                            window.removeEventListener('message', onInit);
                            const port = e.ports[0];
                            window.__verveEdgePort = port;
                            if (!window.__verveEdgeObsInstalled && document.documentElement) {
                                window.__verveEdgeObsInstalled = true;
                                let pending = false;
                                const obs = new MutationObserver(() => {
                                    if (pending) return;
                                    pending = true;
                                    requestAnimationFrame(() => {
                                        pending = false;
                                        try { window.__verveEdgePort && window.__verveEdgePort.postMessage('1'); } catch (_) {}
                                    });
                                });
                                obs.observe(document.documentElement, {
                                    childList: true,
                                    subtree: true,
                                    attributes: true,
                                    attributeFilter: ['style', 'class']
                                });
                            }
                        }
                    });
                })();
            """.trimIndent()

            webView.evaluateJavascript(initObserverJs) {
                if (webView.isAttachedToWindow) {
                    runCatching {
                        webView.postWebMessage(
                            WebMessage("__verve_edge_init__", arrayOf(jsPort)),
                            Uri.EMPTY
                        )
                    }
                }
            }
        }
    }
}

private class CustomWebViewClient(
    private val viewModel: BrowserViewModel,
    private val edgeProbeScheduler: EdgeProbeScheduler,
    private val bottomCoordinator: DocumentBottomScrollCoordinator,
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
            bottomCoordinator.resetForNavigation()
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        if (!url.isNullOrBlank() && url != "about:blank") {
            edgeProbeScheduler.scheduleDebounced(40L)
            WebSnapshotColorExtractor.extractFromWebView(view, delayMs = 80L) { extractedColor ->
                viewModel.onSnapshotColorReady(extractedColor)
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrBlank() && url != "about:blank") {
            edgeProbeScheduler.scheduleDebounced(180L)
            edgeProbeScheduler.attachDomMutationObserver()
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
            edgeProbeScheduler.scheduleDebounced(200L)
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