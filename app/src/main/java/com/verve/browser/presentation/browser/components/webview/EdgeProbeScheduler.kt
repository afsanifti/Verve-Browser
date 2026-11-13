package com.verve.browser.presentation.browser.components.webview

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebView
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.WebEdgeColorExtractor

internal class EdgeProbeScheduler(
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