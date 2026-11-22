package com.verve.browser.presentation.browser.components.webview

import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.presentation.browser.BrowserViewModel
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

object AdBlockWebViewHelper {

    private const val JS_BRIDGE_NAME = "__verveAdBlock"
    private val serviceWorkerHooked = AtomicBoolean(false)
    private val activeTabUrls = ConcurrentHashMap<String, String>()

    fun updateActivePageUrl(tabId: String, url: String) {
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            activeTabUrls[tabId] = url
        }
    }

    fun getCachedPageUrl(tabId: String): String = activeTabUrls[tabId].orEmpty()

    fun clearTab(tabId: String) {
        activeTabUrls.remove(tabId)
    }

    fun ensureServiceWorkerInterception(viewModel: BrowserViewModel) {
        if (!serviceWorkerHooked.compareAndSet(false, true)) return
        runCatching {
            ServiceWorkerController.getInstance().setServiceWorkerClient(
                object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        val targetUrl = request.url?.toString() ?: return null
                        val headers = request.requestHeaders.orEmpty()
                        val sourceUrl = headerIgnoreCase(headers, "Referer")
                            ?: headerIgnoreCase(headers, "Origin")
                            ?: activeTabUrls.values.firstOrNull()
                            ?: targetUrl
                        val resourceType = inferResourceType(request)
                        return interceptNetworkRequest(
                            viewModel = viewModel,
                            request = request,
                            targetUrl = targetUrl,
                            pageUrl = sourceUrl,
                            resourceType = resourceType
                        )
                    }
                }
            )
        }
    }

    fun installCosmeticAndScriptletEngine(
        webView: WebView,
        tabId: String,
        viewModel: BrowserViewModel
    ) {
        webView.addJavascriptInterface(
            VerveCosmeticBridge(tabId = tabId, viewModel = viewModel),
            JS_BRIDGE_NAME
        )

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                buildDocumentStartBootstrapScript(),
                setOf("*")
            )
        }
    }

    fun interceptNetworkRequest(
        viewModel: BrowserViewModel,
        request: WebResourceRequest,
        targetUrl: String,
        pageUrl: String,
        resourceType: String = inferResourceType(request)
    ): WebResourceResponse? {
        if (!targetUrl.startsWith("http://", ignoreCase = true) &&
            !targetUrl.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        val method = request.method?.uppercase() ?: "GET"
        return when (val result = viewModel.evaluateResourceRequest(targetUrl, pageUrl, resourceType, method)) {
            UrlCheckResult.Allow -> null
            is UrlCheckResult.Block -> emptyBlockedResponse()
            is UrlCheckResult.Redirect -> buildSurrogateResponse(result.dataUri) ?: emptyBlockedResponse()
            is UrlCheckResult.RewriteUrl -> executeRewrittenRequest(request, result.cleanedUrl)
        }
    }

    fun inferResourceType(request: WebResourceRequest): String {
        if (request.isForMainFrame) return "main_frame"
        val headers = request.requestHeaders.orEmpty()
        val secFetchDest = headerIgnoreCase(headers, "Sec-Fetch-Dest")?.lowercase()

        when (secFetchDest) {
            "iframe", "frame", "document" -> return "sub_frame"
            "script", "worker", "sharedworker", "serviceworker" -> return "script"
            "style" -> return "stylesheet"
            "image" -> return "image"
            "font" -> return "font"
            "audio", "video", "track" -> return "media"
            "empty" -> return "xmlhttprequest"
        }

        val urlPath = request.url?.path?.lowercase().orEmpty()
        val rawQuery = (request.url?.encodedQuery ?: request.url?.query)?.lowercase().orEmpty()
        val accept = headerIgnoreCase(headers, "Accept")?.lowercase().orEmpty()
        val xRequestedWith = headerIgnoreCase(headers, "X-Requested-With")?.lowercase().orEmpty()
        val secFetchMode = headerIgnoreCase(headers, "Sec-Fetch-Mode")?.lowercase().orEmpty()

        return when {
            urlPath.endsWith(".js") ||
                    urlPath.endsWith(".mjs") ||
                    accept.contains("javascript") ||
                    hasJsonpCallback(rawQuery) -> "script"
            urlPath.endsWith(".css") || accept.contains("text/css") -> "stylesheet"
            accept.contains("image/") ||
                    urlPath.endsWith(".png") ||
                    urlPath.endsWith(".jpg") ||
                    urlPath.endsWith(".jpeg") ||
                    urlPath.endsWith(".gif") ||
                    urlPath.endsWith(".webp") ||
                    urlPath.endsWith(".svg") ||
                    urlPath.endsWith(".avif") ||
                    urlPath.endsWith(".ico") -> "image"
            accept.contains("font/") ||
                    urlPath.endsWith(".woff2") ||
                    urlPath.endsWith(".woff") ||
                    urlPath.endsWith(".ttf") ||
                    urlPath.endsWith(".otf") -> "font"
            urlPath.endsWith(".mp4") ||
                    urlPath.endsWith(".webm") ||
                    urlPath.endsWith(".m3u8") -> "media"
            xRequestedWith == "xmlhttprequest" ||
                    secFetchMode == "cors" ||
                    secFetchMode == "same-origin" ||
                    isTelemetryEndpoint(urlPath) -> "xmlhttprequest"
            else -> "other"
        }
    }

    private fun headerIgnoreCase(headers: Map<String, String>, targetKey: String): String? {
        headers[targetKey]?.let { return it }
        val lowerTarget = targetKey.lowercase()
        headers[lowerTarget]?.let { return it }
        for ((key, value) in headers) {
            if (key.equals(targetKey, ignoreCase = true)) return value
        }
        return null
    }

    private fun hasJsonpCallback(lowerQuery: String): Boolean {
        if (lowerQuery.isEmpty()) return false
        return lowerQuery.startsWith("callback=") ||
                lowerQuery.startsWith("jsonp=") ||
                lowerQuery.contains("&callback=") ||
                lowerQuery.contains("&jsonp=")
    }

    private fun isTelemetryEndpoint(lowerPath: String): Boolean {
        return lowerPath.endsWith("/collect") ||
                lowerPath.endsWith("/beacon") ||
                lowerPath.endsWith("/envelope") ||
                lowerPath.endsWith("/envelope/") ||
                lowerPath.endsWith("/events") ||
                lowerPath.endsWith("/event") ||
                lowerPath.endsWith("/log") ||
                lowerPath.endsWith("/ping") ||
                lowerPath.endsWith("/telemetry")
    }

    private fun emptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun buildSurrogateResponse(dataUri: String): WebResourceResponse? {
        if (!dataUri.startsWith("data:", ignoreCase = true)) return null
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex <= 5) return null

        val metadata = dataUri.substring(5, commaIndex)
        val payload = dataUri.substring(commaIndex + 1)
        val isBase64 = metadata.endsWith(";base64", ignoreCase = true)
        val mimeType = metadata
            .removeSuffix(";base64")
            .substringBefore(';')
            .ifBlank { "application/javascript" }

        val bytes = runCatching {
            if (isBase64) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                Uri.decode(payload).toByteArray(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null

        return WebResourceResponse(
            mimeType,
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Cache-Control" to "max-age=3600",
                "Access-Control-Allow-Origin" to "*"
            ),
            ByteArrayInputStream(bytes)
        )
    }

    private fun executeRewrittenRequest(
        original: WebResourceRequest,
        rewrittenUrl: String
    ): WebResourceResponse? {
        if (!original.method.equals("GET", ignoreCase = true)) return null
        return runCatching {
            val conn = (URL(rewrittenUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                original.requestHeaders.forEach { (k, v) ->
                    if (!k.equals("Host", ignoreCase = true)) {
                        setRequestProperty(k, v)
                    }
                }
            }
            val code = conn.responseCode
            val contentType = conn.contentType ?: "text/plain"
            val mime = contentType.substringBefore(';').trim()
            val encoding = contentType.substringAfter("charset=", "UTF-8").substringBefore(';').trim()
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            WebResourceResponse(
                mime,
                encoding,
                code,
                conn.responseMessage ?: "OK",
                emptyMap(),
                stream ?: ByteArrayInputStream(ByteArray(0))
            )
        }.getOrNull()
    }

    @Suppress("unused")
    private class VerveCosmeticBridge(
        private val tabId: String,
        private val viewModel: BrowserViewModel
    ) {
        private fun resolveHttpPageUrl(rawUrl: String): String {
            if (rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true)
            ) {
                return rawUrl
            }
            val cached = getCachedPageUrl(tabId)
            return if (cached.startsWith("http://", ignoreCase = true) ||
                cached.startsWith("https://", ignoreCase = true)
            ) {
                cached
            } else {
                ""
            }
        }

        @JavascriptInterface
        fun getInitialCosmeticPayload(pageUrl: String): String {
            val resolvedUrl = resolveHttpPageUrl(pageUrl)
            if (resolvedUrl.isEmpty()) return "{}"

            val resources = viewModel.getCosmeticResources(resolvedUrl)
            return JSONObject().apply {
                put("hideSelectors", JSONArray(resources.hideSelectors))
                put("exceptions", JSONArray(resources.exceptions))
                put("injectedScript", resources.injectedScript)
                put("genericHide", resources.genericHide)
            }.toString()
        }

        @JavascriptInterface
        fun queryHiddenSelectors(
            pageUrl: String,
            classesJson: String,
            idsJson: String,
            exceptionsJson: String
        ): String {
            val resolvedUrl = resolveHttpPageUrl(pageUrl)
            if (resolvedUrl.isEmpty()) return "[]"

            val classes = parseBoundedJsonArray(classesJson, maxItems = 256)
            val ids = parseBoundedJsonArray(idsJson, maxItems = 256)
            val exceptions = parseBoundedJsonArray(exceptionsJson, maxItems = 512)
            if (classes.isEmpty() && ids.isEmpty()) return "[]"

            val matched = viewModel.getHiddenClassIdSelectors(
                pageUrl = resolvedUrl,
                classes = classes,
                ids = ids,
                exceptions = exceptions
            )
            return JSONArray(matched).toString()
        }

        private fun parseBoundedJsonArray(raw: String, maxItems: Int): List<String> {
            if (raw.isBlank() || raw == "[]") return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val limit = arr.length().coerceAtMost(maxItems)
                buildList(limit) {
                    for (i in 0 until limit) {
                        val token = arr.optString(i)
                        if (token.isNotBlank()) add(token)
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun buildDocumentStartBootstrapScript(): String = """
        (function() {
            if (!window.__verveAdBlock) return;

            function installForCurrentDocument() {
                var doc = window.document;
                if (!doc) return false;

                var rawHref = window.location.href || '';
                var isHttp = /^https?:\/\//i.test(rawHref);
                var lookupUrl = isHttp ? rawHref : '';

                if (doc.__verveCosmeticInstalled) {
                    return isHttp;
                }

                var initialRaw = '';
                try {
                    initialRaw = window.__verveAdBlock.getInitialCosmeticPayload(lookupUrl);
                } catch (e) {
                    return false;
                }
                if (!initialRaw || initialRaw === '{}') {
                    return false;
                }

                doc.__verveCosmeticInstalled = true;
                var activePageUrl = rawHref;

                var payload;
                try {
                    payload = JSON.parse(initialRaw);
                } catch (e) {
                    return false;
                }

                // 1. Execute Brave/uBlock scriptlets (##+js) BEFORE page scripts run
                if (payload.injectedScript && payload.injectedScript.length > 0) {
                    try {
                        (new Function(payload.injectedScript))();
                    } catch (e) {}
                }

                // 2. Create or reuse a single shared <style> element on THIS document
                var styleEl = doc.querySelector('style[data-verve-cosmetic="1"]');
                if (!styleEl) {
                    styleEl = doc.createElement('style');
                    styleEl.type = 'text/css';
                    styleEl.setAttribute('data-verve-cosmetic', '1');
                }

                function ensureStyleAttached() {
                    if (!styleEl.parentNode) {
                        var target = doc.head || doc.documentElement;
                        if (target) target.appendChild(styleEl);
                    }
                }
                ensureStyleAttached();
                if (!styleEl.parentNode) {
                    doc.addEventListener('readystatechange', ensureStyleAttached, { once: true });
                }

                var appliedSelectors = doc.__verveAppliedSelectors || new Set();
                doc.__verveAppliedSelectors = appliedSelectors;

                function appendSelectors(selectors) {
                    if (!selectors || !selectors.length) return;
                    ensureStyleAttached();
                    var sheet = styleEl.sheet;
                    var fallbackCss = '';
                    for (var i = 0; i < selectors.length; i++) {
                        var sel = selectors[i];
                        if (!sel || appliedSelectors.has(sel)) continue;
                        appliedSelectors.add(sel);
                        var rule = sel + '{display:none!important;visibility:hidden!important;}';
                        if (sheet) {
                            try {
                                sheet.insertRule(rule, sheet.cssRules.length);
                            } catch (e) {}
                        } else {
                            fallbackCss += rule;
                        }
                    }
                    if (fallbackCss) {
                        styleEl.appendChild(doc.createTextNode(fallbackCss));
                    }
                }

                appendSelectors(payload.hideSelectors);

                // 3. If genericHide is active on this domain, skip generic class/id scanning
                if (payload.genericHide) return isHttp;

                if (doc.__verveCosmeticObserver) {
                    try { doc.__verveCosmeticObserver.disconnect(); } catch (e) {}
                }

                var exceptionsJson = JSON.stringify(payload.exceptions || []);
                var seenClasses = new Set();
                var seenIds = new Set();
                var pendingClasses = new Set();
                var pendingIds = new Set();
                var flushTimer = null;

                function collectElementTokens(el) {
                    if (!el || el.nodeType !== 1) return;
                    if (el.id && !seenIds.has(el.id)) {
                        seenIds.add(el.id);
                        pendingIds.add(el.id);
                    }
                    var clsList = el.classList;
                    if (clsList && clsList.length) {
                        for (var i = 0; i < clsList.length; i++) {
                            var c = clsList[i];
                            if (c && !seenClasses.has(c)) {
                                seenClasses.add(c);
                                pendingClasses.add(c);
                            }
                        }
                    }
                }

                function flushPendingTokens() {
                    flushTimer = null;
                    if (pendingClasses.size === 0 && pendingIds.size === 0) return;

                    var classesBatch = Array.from(pendingClasses);
                    var idsBatch = Array.from(pendingIds);
                    pendingClasses.clear();
                    pendingIds.clear();

                    try {
                        var matchedRaw = window.__verveAdBlock.queryHiddenSelectors(
                            activePageUrl,
                            JSON.stringify(classesBatch),
                            JSON.stringify(idsBatch),
                            exceptionsJson
                        );
                        if (matchedRaw && matchedRaw !== '[]') {
                            appendSelectors(JSON.parse(matchedRaw));
                        }
                    } catch (e) {}
                }

                function scheduleFlush() {
                    if (flushTimer !== null) return;
                    flushTimer = setTimeout(flushPendingTokens, 120);
                }

                function scanSubtree(root) {
                    if (!root || root.nodeType !== 1) return;
                    collectElementTokens(root);
                    var descendants = root.getElementsByTagName('*');
                    var limit = Math.min(descendants.length, 400);
                    for (var i = 0; i < limit; i++) {
                        collectElementTokens(descendants[i]);
                    }
                    scheduleFlush();
                }

                var observer = new MutationObserver(function(mutations) {
                    for (var i = 0; i < mutations.length; i++) {
                        var m = mutations[i];
                        if (m.type === 'childList') {
                            for (var j = 0; j < m.addedNodes.length; j++) {
                                var node = m.addedNodes[j];
                                if (node.nodeType === 1) {
                                    collectElementTokens(node);
                                    var children = node.getElementsByTagName('*');
                                    var cap = Math.min(children.length, 150);
                                    for (var k = 0; k < cap; k++) {
                                        collectElementTokens(children[k]);
                                    }
                                }
                            }
                        } else if (m.type === 'attributes') {
                            collectElementTokens(m.target);
                        }
                    }
                    if (pendingClasses.size > 0 || pendingIds.size > 0) {
                        scheduleFlush();
                    }
                });
                doc.__verveCosmeticObserver = observer;

                function startObserver() {
                    var root = doc.documentElement || doc.body;
                    if (!root) return;
                    scanSubtree(root);
                    observer.observe(root, {
                        childList: true,
                        subtree: true,
                        attributes: true,
                        attributeFilter: ['class', 'id']
                    });
                }

                if (doc.readyState === 'loading') {
                    doc.addEventListener('DOMContentLoaded', startObserver, { once: true });
                } else {
                    startObserver();
                }

                return isHttp;
            }

            if (!installForCurrentDocument()) {
                var onReadyState = function() {
                    if (installForCurrentDocument()) {
                        document.removeEventListener('readystatechange', onReadyState);
                    }
                };
                document.addEventListener('readystatechange', onReadyState);
            }
        })();
    """.trimIndent()
}