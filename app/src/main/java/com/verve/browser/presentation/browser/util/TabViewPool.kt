package com.verve.browser.presentation.browser.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.LruCache
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

class TabThumbnailCache(maxEntries: Int = 16) {

    private val cache = LruCache<String, ImageBitmap>(maxEntries)
    private val revisions = mutableStateMapOf<String, Int>()

    fun get(tabId: String): ImageBitmap? {
        revisions[tabId]
        return cache.get(tabId)
    }

    fun put(tabId: String, bitmap: ImageBitmap) {
        cache.put(tabId, bitmap)
        revisions[tabId] = (revisions[tabId] ?: 0) + 1
    }

    fun remove(tabId: String) {
        cache.remove(tabId)
        revisions.remove(tabId)
    }

    fun clear() {
        cache.evictAll()
        revisions.clear()
    }

    fun captureFromWebView(tabId: String, webView: WebView?) {
        val view = webView ?: return
        if (tabId.isBlank() || !view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return

        val captured = renderWebViewBitmap(view) ?: return
        put(tabId, captured)
    }

    private fun renderWebViewBitmap(webView: WebView): ImageBitmap? {
        val viewWidth = webView.width
        val viewHeight = webView.height
        if (viewWidth <= 0 || viewHeight <= 0) return null

        val targetWidth = minOf(viewWidth, 540)
        val scale = targetWidth.toFloat() / viewWidth.toFloat()
        val targetHeight = (viewHeight * scale).roundToInt().coerceAtLeast(1)

        var bitmap: Bitmap? = null
        return runCatching {
            val created = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            bitmap = created
            val canvas = Canvas(created)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            created.asImageBitmap()
        }.getOrElse {
            bitmap?.recycle()
            null
        }
    }
}

class TabWebViewPool(private val maxLiveWebViews: Int = 6) {

    private val liveWebViews = LinkedHashMap<String, WebView>(maxLiveWebViews, 0.75f, true)
    private val savedStates = HashMap<String, Bundle>()
    private val loadedUrls = HashMap<String, String>()
    private val crashHaltedTabs = HashSet<String>()
    private val poolRevisions = mutableStateMapOf<String, Int>()

    fun peekWebView(tabId: String): WebView? {
        poolRevisions[tabId]
        return liveWebViews[tabId]
    }

    fun getLastRequestedUrl(tabId: String): String = loadedUrls[tabId].orEmpty()

    fun setLastRequestedUrl(tabId: String, url: String) {
        crashHaltedTabs.remove(tabId)
        loadedUrls[tabId] = url
    }

    fun getOrCreateWebView(
        tabId: String,
        context: Context,
        factory: (Context) -> WebView
    ): WebView {
        poolRevisions[tabId]
        liveWebViews[tabId]?.let { existing ->
            return existing
        }

        evictOldestIfNeeded(excludeTabId = tabId)

        val created = factory(context)
        val restored = savedStates.remove(tabId)?.let { bundle ->
            runCatching { created.restoreState(bundle) != null }.getOrDefault(false)
        } ?: false

        if (!restored && !crashHaltedTabs.contains(tabId)) {
            loadedUrls.remove(tabId)
        }

        liveWebViews[tabId] = created
        return created
    }

    fun handleRenderProcessGone(
        tabId: String,
        deadWebView: WebView,
        shouldReload: Boolean
    ) {
        savedStates.remove(tabId)
        if (shouldReload) {
            crashHaltedTabs.remove(tabId)
            loadedUrls.remove(tabId)
        } else {
            crashHaltedTabs.add(tabId)
        }
        if (liveWebViews[tabId] === deadWebView) {
            liveWebViews.remove(tabId)
        }
        destroyWebViewSafely(deadWebView)
        poolRevisions[tabId] = (poolRevisions[tabId] ?: 0) + 1
    }

    fun resetTab(tabId: String) {
        savedStates.remove(tabId)
        loadedUrls.remove(tabId)
        crashHaltedTabs.remove(tabId)
        poolRevisions.remove(tabId)
        liveWebViews.remove(tabId)?.let { destroyWebViewSafely(it) }
    }

    fun destroyAll() {
        savedStates.clear()
        loadedUrls.clear()
        crashHaltedTabs.clear()
        poolRevisions.clear()
        val allViews = liveWebViews.values.toList()
        liveWebViews.clear()
        allViews.forEach { destroyWebViewSafely(it) }
    }

    private fun evictOldestIfNeeded(excludeTabId: String) {
        while (liveWebViews.size >= maxLiveWebViews) {
            val oldestEntry = liveWebViews.entries.firstOrNull { it.key != excludeTabId } ?: break
            val oldestId = oldestEntry.key
            val oldestView = oldestEntry.value
            liveWebViews.remove(oldestId)

            val bundle = Bundle()
            if (oldestView.saveState(bundle) != null) {
                savedStates[oldestId] = bundle
            }
            destroyWebViewSafely(oldestView)
        }
    }

    private fun destroyWebViewSafely(webView: WebView) {
        runCatching {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.onPause()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }
}