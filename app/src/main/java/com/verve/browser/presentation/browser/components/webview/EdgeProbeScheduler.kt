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
                    probePinnedBottomBarOnScrollablePage()
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

                const stack = [document.body || document.documentElement];
                let visited = 0;
                const maxNodes = 4000;

                while (stack.length > 0 && visited < maxNodes) {
                    const el = stack.pop();
                    if (!el || el.nodeType !== 1) continue;
                    visited++;

                    const tag = el.tagName;
                    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT') continue;

                    const style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden') continue;

                    const rect = el.getBoundingClientRect();
                    if (tag === 'VIDEO' && rect.width >= vw * 0.5 && rect.height >= vh * 0.25) {
                        return '1';
                    }

                    if (rect.width >= 12 && rect.height >= 8 &&
                        rect.top >= minTop && rect.bottom >= minBottom && rect.top < vh &&
                        rect.right > 0 && rect.left < vw &&
                        parseFloat(style.opacity || '1') > 0.05
                    ) {
                        const hasText = (el.textContent || '').trim().length > 0;
                        const isInteractive = el.matches('footer,nav,[role="contentinfo"],[role="navigation"],a,button,input,select,[role="button"],[role="link"],svg') ||
                            el.querySelector('a,button,input,select,[role="button"],[role="link"],svg') !== null ||
                            el.shadowRoot !== null;
                        if (hasText || isInteractive) return '1';
                    }

                    if (el.shadowRoot) {
                        const srKids = el.shadowRoot.children;
                        for (let i = 0; i < srKids.length; i++) stack.push(srKids[i]);
                    }
                    const kids = el.children;
                    for (let i = 0; i < kids.length; i++) stack.push(kids[i]);
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

    private fun probePinnedBottomBarOnScrollablePage() {
        if (isDisposed || !canRunProbe()) return
        val detectPinnedBottomBarJs = """
            (() => {
                const vh = window.innerHeight || document.documentElement.clientHeight || 0;
                const vw = window.innerWidth || document.documentElement.clientWidth || 0;
                if (vh <= 0 || vw <= 0) return '0';

                const minTop = vh * 0.50;
                const minBottom = vh - 96;
                const maxBottom = vh + 24;
                const minWidth = vw * 0.25;

                const isVisible = (el) => {
                    const style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden') return false;
                    if (parseFloat(style.opacity || '1') <= 0.05) return false;
                    return true;
                };

                const hasContent = (el) => {
                    if (el.matches('a,button,input,select,[role="button"],[role="link"]')) return true;
                    if (el.querySelector('a,button,input,select,[role="button"],[role="link"],svg,img') !== null) return true;
                    if ((el.textContent || '').trim().length > 0) return true;
                    const inner = el.querySelectorAll('*');
                    const cap = Math.min(inner.length, 30);
                    for (let i = 0; i < cap; i++) {
                        if (inner[i].shadowRoot) return true;
                    }
                    return false;
                };

                const isBottomBarRect = (el) => {
                    const rect = el.getBoundingClientRect();
                    if (rect.width < minWidth || rect.height < 24 || rect.height > 180) return false;
                    if (rect.top < minTop || rect.top >= vh - 12) return false;
                    if (rect.bottom < minBottom || rect.bottom > maxBottom) return false;
                    if (rect.right <= 0 || rect.left >= vw) return false;
                    return true;
                };

                const stack = [document.body || document.documentElement];
                let visitedCount = 0;
                const maxNodes = 6000;

                while (stack.length > 0 && visitedCount < maxNodes) {
                    const el = stack.pop();
                    if (!el || el.nodeType !== 1) continue;
                    visitedCount++;

                    const tag = el.tagName;
                    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'SVG' || tag === 'PATH') {
                        continue;
                    }

                    const style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity || '1') <= 0.05) {
                        continue;
                    }

                    const pos = style.position;
                    if (pos === 'fixed' || pos === 'sticky') {
                        if (isBottomBarRect(el) && hasContent(el)) {
                            return '1';
                        }
                        // Catch full-screen fixed overlay wrappers (e.g. inset: 0; pointer-events: none) with a bottom-docked bar inside
                        const rect = el.getBoundingClientRect();
                        if (rect.width >= minWidth && rect.bottom >= minBottom && rect.top < minTop) {
                            const subStack = [];
                            if (el.shadowRoot) {
                                const srKids = el.shadowRoot.children;
                                for (let i = 0; i < srKids.length; i++) subStack.push(srKids[i]);
                            }
                            const kids = el.children;
                            for (let i = 0; i < kids.length; i++) subStack.push(kids[i]);

                            let subCount = 0;
                            while (subStack.length > 0 && subCount < 400) {
                                const sub = subStack.pop();
                                if (!sub || sub.nodeType !== 1) continue;
                                subCount++;
                                if (!isVisible(sub)) continue;
                                if (isBottomBarRect(sub) && hasContent(sub)) {
                                    return '1';
                                }
                                if (sub.shadowRoot) {
                                    const sKids = sub.shadowRoot.children;
                                    for (let i = 0; i < sKids.length; i++) subStack.push(sKids[i]);
                                }
                                const cKids = sub.children;
                                for (let i = 0; i < cKids.length; i++) subStack.push(cKids[i]);
                            }
                        }
                    }

                    if (el.shadowRoot) {
                        const srChildren = el.shadowRoot.children;
                        for (let i = 0; i < srChildren.length; i++) {
                            stack.push(srChildren[i]);
                        }
                    }
                    const children = el.children;
                    for (let i = 0; i < children.length; i++) {
                        stack.push(children[i]);
                    }
                }

                return '0';
            })();
        """.trimIndent()

        webView.evaluateJavascript(detectPinnedBottomBarJs) { rawResult ->
            if (isDisposed || !canRunProbe()) return@evaluateJavascript
            if (viewModel.uiState.value.activeTabId != tabId) return@evaluateJavascript
            val hasPinnedBar = rawResult?.trim('"') == "1"
            viewModel.updateHasFixedBottomBar(hasPinnedBar)
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
        handler.postDelayed(probeRunnable, 550L)
        handler.postDelayed(probeRunnable, 1200L)

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