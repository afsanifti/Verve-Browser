package com.verve.browser.presentation.browser.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TabThumbnailCache(
    context: Context? = null,
    maxEntries: Int = 16
) {
    private val appContext = context?.applicationContext
    private val diskCacheDir: File? = appContext?.let {
        File(it.cacheDir, "tab_thumbnails").apply { mkdirs() }
    }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache = LruCache<String, ImageBitmap>(maxEntries)
    private val revisions = mutableStateMapOf<String, Int>()
    private val pendingDiskLoads = mutableSetOf<String>()

    companion object {
        private const val TARGET_WIDTH_PX = 540
        private const val CARD_ASPECT_RATIO = 269f / 564f
    }

    fun get(tabId: String): ImageBitmap? {
        revisions[tabId]
        cache.get(tabId)?.let { return it }

        val dir = diskCacheDir ?: return null
        if (pendingDiskLoads.add(tabId)) {
            ioScope.launch {
                val diskFile = File(dir, "$tabId.webp")
                val decoded = if (diskFile.exists()) {
                    runCatching {
                        BitmapFactory.decodeFile(diskFile.absolutePath)?.asImageBitmap()
                    }.getOrNull()
                } else {
                    null
                }
                withContext(Dispatchers.Main) {
                    pendingDiskLoads.remove(tabId)
                    if (decoded != null && cache.get(tabId) == null) {
                        put(tabId, decoded)
                    }
                }
            }
        }
        return null
    }

    fun put(tabId: String, bitmap: ImageBitmap) {
        cache.put(tabId, bitmap)
        revisions[tabId] = (revisions[tabId] ?: 0) + 1
    }

    fun remove(tabId: String) {
        cache.remove(tabId)
        revisions.remove(tabId)
        val dir = diskCacheDir ?: return
        ioScope.launch {
            File(dir, "$tabId.webp").delete()
        }
    }

    fun pruneOrphans(validTabIds: Set<String>) {
        val dir = diskCacheDir ?: return
        ioScope.launch {
            dir.listFiles()?.forEach { file ->
                if (file.nameWithoutExtension !in validTabIds) {
                    file.delete()
                }
            }
        }
    }

    fun clear() {
        cache.evictAll()
        revisions.clear()
    }

    fun captureFromWebView(
        tabId: String,
        webView: WebView?,
        isIncognito: Boolean = false
    ) {
        val view = webView ?: return
        if (tabId.isBlank() || !view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return

        val loadedUrl = view.url
        if (loadedUrl.isNullOrBlank() || loadedUrl == "about:blank") return

        val bitmap = renderWebViewBitmap(view) ?: return
        put(tabId, bitmap.asImageBitmap())

        val dir = diskCacheDir ?: return
        ioScope.launch {
            val diskFile = File(dir, "$tabId.webp")
            if (isIncognito) {
                if (diskFile.exists()) diskFile.delete()
                return@launch
            }
            runCatching {
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                diskFile.outputStream().use { out ->
                    bitmap.compress(format, 80, out)
                }
            }
        }
    }

    private fun renderWebViewBitmap(webView: WebView): Bitmap? {
        val viewWidth = webView.width
        val viewHeight = webView.height
        if (viewWidth <= 0 || viewHeight <= 0) return null

        val targetWidth = minOf(viewWidth, TARGET_WIDTH_PX)
        val scale = targetWidth.toFloat() / viewWidth.toFloat()
        val maxScaledHeight = (viewHeight * scale).roundToInt().coerceAtLeast(1)
        val cardScaledHeight = (targetWidth / CARD_ASPECT_RATIO).roundToInt().coerceAtLeast(1)
        val targetHeight = minOf(maxScaledHeight, cardScaledHeight)

        var bitmap: Bitmap? = null
        return runCatching {
            val created = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap = created
            val canvas = Canvas(created)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            created
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
    private var isDarkTheme: Boolean = false

    fun updateDarkTheme(darkTheme: Boolean) {
        isDarkTheme = darkTheme
        liveWebViews.values.forEach { webView ->
            WebViewThemeSync.applyToWebView(webView, darkTheme)
        }
    }

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
            WebViewThemeSync.applyToWebView(existing, isDarkTheme)
            return existing
        }

        evictOldestIfNeeded(excludeTabId = tabId)

        val created = factory(context)
        WebViewThemeSync.applyToWebView(created, isDarkTheme)
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