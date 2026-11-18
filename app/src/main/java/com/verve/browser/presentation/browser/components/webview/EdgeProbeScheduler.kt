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
    private val bottomCoordinator: DocumentBottomScrollCoordinator,
    private val isActivelyScrolling: () -> Boolean = { false },
    private val canRunProbe: () -> Boolean = { webView.isAttachedToWindow && webView.windowToken != null },
    private val onDomVisualMutation: () -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastProbeTimestampMs = 0L
    private var activePort: WebMessagePort? = null
    private var isDisposed = false

    private val probeRunnable: Runnable = Runnable {
        if (isDisposed || !canRunProbe()) return@Runnable
        if (isActivelyScrolling()) {
            handler.postDelayed(probeRunnable, 90L)
            return@Runnable
        }

        lastProbeTimestampMs = SystemClock.uptimeMillis()
        val approachingBottom = bottomCoordinator.isInBottomLookaheadZone()
        WebEdgeColorExtractor.extractEdgeProbe(
            webView = webView,
            isApproachingBottom = approachingBottom
        ) { probeResult ->
            if (isDisposed || !canRunProbe()) return@extractEdgeProbe
            viewModel.updateTopRegionColor(tabId, probeResult.topColor)
            viewModel.updateBottomRegionColor(tabId, probeResult.bottomColor)
            bottomCoordinator.onJsScrollMetricsUpdated(probeResult.scrollMetrics)

            if (viewModel.uiState.value.activeTabId != tabId) return@extractEdgeProbe

            when {
                probeResult.hasFixedBottomBar -> {
                    bottomCoordinator.setFixedFrameBarLatched(false)
                    viewModel.updateHasFixedBottomBar(true)
                }
                bottomCoordinator.isFixedFrameBarLatched() &&
                        bottomCoordinator.isCandidateFixedFrameViewport() -> {
                    viewModel.updateHasFixedBottomBar(true)
                }
                bottomCoordinator.isCandidateFixedFrameViewport() -> {
                    probeFixedFrameBottomContent()
                }
                else -> {
                    bottomCoordinator.setFixedFrameBarLatched(false)
                    viewModel.updateHasFixedBottomBar(false)
                }
            }
        }
    }

    private fun probeFixedFrameBottomContent() {
        if (isDisposed || !canRunProbe()) return
        val detectBottomFooterJs = """
            (() => {
                const vh = window.innerHeight || document.documentElement.clientHeight || 0;
                const vw = window.innerWidth || document.documentElement.clientWidth || 0;
                if (vh <= 0 || vw <= 0) return '0';
                const minTop = vh * 0.50;
                const minBottom = vh - 56;
                const candidates = document.querySelectorAll(
                    'footer, nav, [role="contentinfo"], [role="navigation"], a, button, input, select, [role="button"], [role="link"], div, ul, p, span'
                );
                const limit = Math.min(candidates.length, 450);
                for (let i = 0; i < limit; i++) {
                    const el = candidates[i];
                    const rect = el.getBoundingClientRect();
                    if (rect.width < 12 || rect.height < 8) continue;
                    if (rect.top < minTop || rect.bottom < minBottom || rect.top >= vh) continue;
                    if (rect.right <= 0 || rect.left >= vw) continue;
                    const style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') <= 0.05) {
                        continue;
                    }
                    const hasText = (el.innerText || '').trim().length > 0;
                    const isInteractive = el.matches('a,button,input,select,[role="button"],[role="link"]') ||
                        el.querySelector('a,button,input,select,[role="button"],[role="link"],svg') !== null;
                    if (hasText || isInteractive) return '1';
                }
                return '0';
            })();
        """.trimIndent()

        webView.evaluateJavascript(detectBottomFooterJs) { rawResult ->
            if (isDisposed || !canRunProbe()) return@evaluateJavascript
            if (viewModel.uiState.value.activeTabId != tabId) return@evaluateJavascript
            val hasBottomFooter = rawResult?.trim('"') == "1" &&
                    bottomCoordinator.isCandidateFixedFrameViewport()
            bottomCoordinator.setFixedFrameBarLatched(hasBottomFooter)
            viewModel.updateHasFixedBottomBar(hasBottomFooter)
        }
    }

    fun scheduleDebounced(delayMs: Long) {
        if (isDisposed) return
        handler.removeCallbacks(probeRunnable)
        handler.postDelayed(probeRunnable, delayMs)
    }

    fun onScroll() {
        if (isDisposed) return
        val now = SystemClock.uptimeMillis()
        val approachingBottom = bottomCoordinator.isInBottomLookaheadZone()
        val throttleMs = if (approachingBottom) 60L else 140L
        val settleDelayMs = if (approachingBottom) 70L else 120L

        if (!isActivelyScrolling() && now - lastProbeTimestampMs >= throttleMs) {
            handler.removeCallbacks(probeRunnable)
            probeRunnable.run()
        }
        handler.removeCallbacks(probeRunnable)
        handler.postDelayed(probeRunnable, settleDelayMs)
    }

    fun cancelPending() {
        handler.removeCallbacks(probeRunnable)
    }

    fun dispose() {
        isDisposed = true
        handler.removeCallbacksAndMessages(null)
        runCatching { activePort?.close() }
        activePort = null
    }

    fun attachDomMutationObserver() {
        if (isDisposed || !canRunProbe()) return
        runCatching {
            activePort?.close()
            val channel = webView.createWebMessageChannel()
            val nativePort = channel[0]
            val jsPort = channel[1]
            activePort = nativePort

            nativePort.setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                override fun onMessage(port: WebMessagePort?, message: WebMessage?) {
                    if (!isDisposed) {
                        scheduleDebounced(150L)
                        onDomVisualMutation()
                    }
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
                if (!isDisposed && canRunProbe()) {
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