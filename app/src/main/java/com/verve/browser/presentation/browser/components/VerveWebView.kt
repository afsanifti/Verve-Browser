package com.verve.browser.presentation.browser.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool
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
    tabId: String,
    url: String,
    viewModel: BrowserViewModel,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    var attachedTabId by remember { mutableStateOf("") }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { hostContainer ->
            val targetWebView = tabWebViewPool.getOrCreateWebView(
                tabId = tabId,
                context = hostContainer.context
            ) { ctx ->
                createTabWebView(
                    context = ctx,
                    tabId = tabId,
                    viewModel = viewModel,
                    tabWebViewPool = tabWebViewPool,
                    thumbnailCache = thumbnailCache
                )
            }

            if (attachedTabId != tabId || targetWebView.parent !== hostContainer) {
                if (attachedTabId.isNotEmpty() && attachedTabId != tabId) {
                    tabWebViewPool.peekWebView(attachedTabId)?.let { previous ->
                        thumbnailCache.captureFromWebView(attachedTabId, previous)
                        previous.onPause()
                    }
                }
                (targetWebView.parent as? ViewGroup)?.removeView(targetWebView)
                hostContainer.removeAllViews()
                hostContainer.addView(
                    targetWebView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                targetWebView.onResume()
                attachedTabId = tabId
                onWebViewCreated(targetWebView)
            }

            val lastRequestedUrl = tabWebViewPool.getLastRequestedUrl(tabId)
            if (url.isNotEmpty() && url != lastRequestedUrl) {
                tabWebViewPool.setLastRequestedUrl(tabId, url)
                targetWebView.loadUrl(url)
            }
        },
        onRelease = { hostContainer ->
            if (attachedTabId.isNotEmpty()) {
                tabWebViewPool.peekWebView(attachedTabId)?.let { activeView ->
                    thumbnailCache.captureFromWebView(attachedTabId, activeView)
                    activeView.onPause()
                }
            }
            hostContainer.removeAllViews()
            attachedTabId = ""
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createTabWebView(
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

        webViewClient = CustomWebViewClient(
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

        webChromeClient = object : WebChromeClient() {
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
    }
}

private class DocumentBottomScrollCoordinator(
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

private class EdgeProbeScheduler(
    private val webView: WebView,
    private val tabId: String,
    private val viewModel: BrowserViewModel,
    private val bottomCoordinator: DocumentBottomScrollCoordinator
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastProbeTimestampMs = 0L
    private var activePort: WebMessagePort? = null

    private val probeRunnable = Runnable {
        lastProbeTimestampMs = SystemClock.uptimeMillis()
        val approachingBottom = bottomCoordinator.isInBottomLookaheadZone()
        WebEdgeColorExtractor.extractEdgeProbe(
            webView = webView,
            isApproachingBottom = approachingBottom
        ) { probeResult ->
            viewModel.updateTopRegionColor(tabId, probeResult.topColor)
            viewModel.updateBottomRegionColor(tabId, probeResult.bottomColor)
            if (viewModel.uiState.value.activeTabId == tabId) {
                viewModel.updateHasFixedBottomBar(probeResult.hasFixedBottomBar)
            }
            bottomCoordinator.onJsScrollMetricsUpdated(probeResult.scrollMetrics)
        }
    }

    fun scheduleDebounced(delayMs: Long) {
        handler.removeCallbacks(probeRunnable)
        handler.postDelayed(probeRunnable, delayMs)
    }

    fun onScroll() {
        val now = SystemClock.uptimeMillis()
        val approachingBottom = bottomCoordinator.isInBottomLookaheadZone()
        val throttleMs = if (approachingBottom) 60L else 140L
        val settleDelayMs = if (approachingBottom) 70L else 120L

        if (now - lastProbeTimestampMs >= throttleMs) {
            handler.removeCallbacks(probeRunnable)
            probeRunnable.run()
        }
        handler.removeCallbacks(probeRunnable)
        handler.postDelayed(probeRunnable, settleDelayMs)
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
                    scheduleDebounced(150L)
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