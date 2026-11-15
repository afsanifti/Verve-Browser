package com.verve.browser.presentation.browser.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CachedPageColor(val color: Color?)

class TabPageColorCache(private val maxEntries: Int = 10) {
    private val map = object : LinkedHashMap<String, CachedPageColor>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedPageColor>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(url: String): CachedPageColor? {
        val key = WebSnapshotColorExtractor.normalizeUrl(url)
        if (key.isEmpty()) return null
        return map[key]
    }

    @Synchronized
    fun put(url: String, color: Color?) {
        val key = WebSnapshotColorExtractor.normalizeUrl(url)
        if (key.isNotEmpty() && key != "about:blank" && !key.startsWith("data:")) {
            map[key] = CachedPageColor(color)
        }
    }

    @Synchronized
    fun remove(url: String) {
        val key = WebSnapshotColorExtractor.normalizeUrl(url)
        if (key.isNotEmpty()) {
            map.remove(key)
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

object WebSnapshotColorExtractor {

    private const val SAMPLE_SIZE = 128
    private const val HUE_BINS = 36
    private const val VIEWPORT_SAMPLE_RATIO = 0.42f
    private const val MIN_PRIMARY_PIXELS = 96
    private const val MIN_SURFACE_PIXELS = 600

    private val mainHandler = Handler(Looper.getMainLooper())
    private val extractionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class ExtractionState(
        var generation: Long = 0L,
        var pendingRunnable: Runnable? = null,
        var activeJob: Job? = null
    )

    private val states = WeakHashMap<WebView, ExtractionState>()

    private const val DOM_COLOR_FALLBACK_SCRIPT = """
        (function() {
            var meta = document.querySelector('meta[name="theme-color"]');
            if (meta && meta.getAttribute('content')) {
                return meta.getAttribute('content');
            }
            var els = [document.querySelector('header'), document.querySelector('nav'), document.body];
            for (var i = 0; i < els.length; i++) {
                if (!els[i]) continue;
                var bg = window.getComputedStyle(els[i]).backgroundColor;
                if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent' &&
                    bg !== 'rgb(255, 255, 255)' && bg !== 'rgb(0, 0, 0)') {
                    return bg;
                }
            }
            return '';
        })();
    """

    fun normalizeUrl(url: String): String = url.trim()

    fun cancel(webView: WebView?) {
        if (webView == null) return
        val state = states[webView] ?: return
        state.generation += 1L
        state.pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        state.pendingRunnable = null
        state.activeJob?.cancel()
        state.activeJob = null
    }

    fun extractFromWebView(
        webView: WebView?,
        delayMs: Long = 160L,
        onColorExtracted: (Color?) -> Unit
    ) {
        if (webView == null) {
            onColorExtracted(null)
            return
        }

        val state = states.getOrPut(webView) { ExtractionState() }
        state.generation += 1L
        val requestGeneration = state.generation
        state.pendingRunnable?.let { mainHandler.removeCallbacks(it) }
        state.activeJob?.cancel()
        state.activeJob = null

        val runnable = Runnable {
            state.pendingRunnable = null
            if (!isRequestValid(webView, requestGeneration)) return@Runnable

            webView.postVisualStateCallback(
                requestGeneration,
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (!isRequestValid(webView, requestGeneration)) return

                        val bitmap = captureTopViewportSnapshot(webView)
                        if (bitmap == null) {
                            extractDomFallback(webView, requestGeneration, onColorExtracted)
                            return
                        }

                        state.activeJob = extractionScope.launch {
                            val snapshotColor = try {
                                if (!isActive) null else extractDominantSeedColor(bitmap)
                            } finally {
                                bitmap.recycle()
                            }

                            if (!isActive) return@launch

                            withContext(Dispatchers.Main) {
                                if (!isRequestValid(webView, requestGeneration)) return@withContext
                                if (snapshotColor != null) {
                                    onColorExtracted(snapshotColor)
                                } else {
                                    extractDomFallback(webView, requestGeneration, onColorExtracted)
                                }
                            }
                        }
                    }
                }
            )
        }

        state.pendingRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun isRequestValid(webView: WebView, expectedGeneration: Long): Boolean {
        val currentGeneration = states[webView]?.generation ?: return false
        return currentGeneration == expectedGeneration &&
                webView.isAttachedToWindow &&
                webView.windowToken != null &&
                webView.width > 0 &&
                webView.height > 0
    }

    private fun captureTopViewportSnapshot(webView: WebView): Bitmap? {
        return runCatching {
            val bitmap = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val targetHeight = (webView.height * VIEWPORT_SAMPLE_RATIO).coerceAtLeast(1f)
            val scaleX = SAMPLE_SIZE.toFloat() / webView.width.toFloat()
            val scaleY = SAMPLE_SIZE.toFloat() / targetHeight

            canvas.scale(scaleX, scaleY)
            webView.draw(canvas)
            bitmap
        }.getOrNull()
    }

    private fun extractDominantSeedColor(bitmap: Bitmap): Color? {
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        bitmap.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)

        // Pass 1: Brand / accent colors (buttons, banners, badges, logos)
        val primaryColor = quantizeHueBins(
            pixels = pixels,
            minSaturation = 0.18f,
            minBrightness = 0.12f,
            maxBrightness = 0.96f,
            minPixelCount = MIN_PRIMARY_PIXELS
        )
        if (primaryColor != null) return primaryColor

        // Pass 2: Soft pastel / tinted surfaces (e.g., tinted hero cards or headers)
        return quantizeHueBins(
            pixels = pixels,
            minSaturation = 0.045f,
            minBrightness = 0.10f,
            maxBrightness = 0.975f,
            minPixelCount = MIN_SURFACE_PIXELS
        )
    }

    private fun quantizeHueBins(
        pixels: IntArray,
        minSaturation: Float,
        minBrightness: Float,
        maxBrightness: Float,
        minPixelCount: Int
    ): Color? {
        val hsv = FloatArray(3)
        val binWeights = FloatArray(HUE_BINS)
        val binR = FloatArray(HUE_BINS)
        val binG = FloatArray(HUE_BINS)
        val binB = FloatArray(HUE_BINS)

        var matchedPixelCount = 0

        for (index in pixels.indices) {
            val pixel = pixels[index]
            if (android.graphics.Color.alpha(pixel) < 128) continue

            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

            android.graphics.Color.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val saturation = hsv[1]
            val brightness = hsv[2]

            if (saturation < minSaturation) continue
            if (brightness < minBrightness || brightness > maxBrightness) continue
            // Reject near-white highlights with negligible tint
            if (brightness > 0.94f && saturation < 0.08f) continue

            matchedPixelCount++

            // Bias toward the top of the viewport (header & hero) over lower content
            val row = index / SAMPLE_SIZE
            val spatialWeight = 1.25f - 0.50f * (row.toFloat() / SAMPLE_SIZE.toFloat())
            // Balance area proportion (30%) and chroma (70%) so tiny specks can't dominate
            val chromaWeight = 0.30f + 0.70f * saturation
            val weight = spatialWeight * chromaWeight

            val bin = (hue / 10f).toInt().coerceIn(0, HUE_BINS - 1)
            binWeights[bin] += weight
            binR[bin] += r * weight
            binG[bin] += g * weight
            binB[bin] += b * weight
        }

        if (matchedPixelCount < minPixelCount) return null

        // Score using a circular 3-bin window so multi-shade purples/pinks/reds don't split votes
        var bestBin = -1
        var bestSmoothedScore = 0f
        for (i in 0 until HUE_BINS) {
            val prev = (i + HUE_BINS - 1) % HUE_BINS
            val next = (i + 1) % HUE_BINS
            val smoothedScore = 0.45f * binWeights[prev] + binWeights[i] + 0.45f * binWeights[next]
            if (smoothedScore > bestSmoothedScore) {
                bestSmoothedScore = smoothedScore
                bestBin = i
            }
        }

        if (bestBin == -1) return null

        // Blend the winning bin with its immediate neighbors (only if they carry meaningful weight)
        val prev = (bestBin + HUE_BINS - 1) % HUE_BINS
        val next = (bestBin + 1) % HUE_BINS
        val centerWeight = binWeights[bestBin]
        val prevWeight = if (binWeights[prev] > centerWeight * 0.20f) binWeights[prev] * 0.5f else 0f
        val nextWeight = if (binWeights[next] > centerWeight * 0.20f) binWeights[next] * 0.5f else 0f

        val totalWeight = centerWeight + prevWeight + nextWeight
        if (totalWeight <= 0f) return null

        val prevScale = if (binWeights[prev] > 0f) prevWeight / binWeights[prev] else 0f
        val nextScale = if (binWeights[next] > 0f) nextWeight / binWeights[next] else 0f

        val avgR = ((binR[bestBin] + binR[prev] * prevScale + binR[next] * nextScale) / totalWeight)
            .toInt().coerceIn(0, 255)
        val avgG = ((binG[bestBin] + binG[prev] * prevScale + binG[next] * nextScale) / totalWeight)
            .toInt().coerceIn(0, 255)
        val avgB = ((binB[bestBin] + binB[prev] * prevScale + binB[next] * nextScale) / totalWeight)
            .toInt().coerceIn(0, 255)

        return Color(avgR, avgG, avgB)
    }

    private fun extractDomFallback(
        webView: WebView,
        expectedGeneration: Long,
        onColorExtracted: (Color?) -> Unit
    ) {
        if (!isRequestValid(webView, expectedGeneration)) return
        webView.evaluateJavascript(DOM_COLOR_FALLBACK_SCRIPT) { raw ->
            if (!isRequestValid(webView, expectedGeneration)) return@evaluateJavascript
            val clean = raw?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
            onColorExtracted(parseCssColor(clean))
        }
    }

    private fun parseCssColor(value: String?): Color? {
        if (value.isNullOrBlank() || value == "null") return null
        return runCatching {
            val parsed = if (value.startsWith("rgb")) {
                val nums = value.substringAfter("(").substringBefore(")")
                    .split(",")
                    .mapNotNull { it.trim().toFloatOrNull()?.toInt() }
                if (nums.size >= 3) Color(nums[0], nums[1], nums[2]) else null
            } else {
                var hex = value.removePrefix("#")
                if (hex.length == 3) {
                    hex = "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}"
                }
                if (hex.length == 6 || hex.length == 8) Color("#$hex".toColorInt()) else null
            }
            // Ignore near-white or near-black CSS fallbacks so neutral pages keep the default theme
            parsed?.takeUnless { c ->
                (c.red > 0.94f && c.green > 0.94f && c.blue > 0.94f) ||
                        (c.red < 0.06f && c.green < 0.06f && c.blue < 0.06f)
            }
        }.getOrNull()
    }
}