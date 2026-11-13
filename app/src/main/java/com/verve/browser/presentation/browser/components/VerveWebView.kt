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
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastLoadedUrl by remember { mutableStateOf("") }
    var scrollCoordinatorRef by remember { mutableStateOf<DocumentBottomScrollCoordinator?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
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
                            edgeProbeScheduler.scheduleDebounced(80L)
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
            if (url.isNotEmpty() && url != lastLoadedUrl) {
                lastLoadedUrl = url
                scrollCoordinatorRef?.resetForNavigation()
                webView.loadUrl(url)
            }
        }
    )
}

/**
 * Option 3: Zero-resize document-bottom scroll coordinator.
 * Because WebView maintains a constant height, vertical scroll offsets never experience
 * resize clamping or layout-compensation feedback loops during fast flings.
 */
private class DocumentBottomScrollCoordinator(
    private val webView: WebView,
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
        viewModel.updateBottomCurtainProgress(0f)
        viewModel.updateHasFixedBottomBar(false)
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
            viewModel.updateTopRegionColor(probeResult.topColor)
            viewModel.updateBottomRegionColor(probeResult.bottomColor)
            viewModel.updateHasFixedBottomBar(probeResult.hasFixedBottomBar)
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
            edgeProbeScheduler.scheduleDebounced(30L)
            WebSnapshotColorExtractor.extractFromWebView(view, delayMs = 80L) { extractedColor ->
                viewModel.onSnapshotColorReady(extractedColor)
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (!url.isNullOrBlank() && url != "about:blank") {
            edgeProbeScheduler.scheduleDebounced(140L)
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
            edgeProbeScheduler.scheduleDebounced(160L)
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