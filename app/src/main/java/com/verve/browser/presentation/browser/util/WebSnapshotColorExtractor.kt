package com.verve.browser.presentation.browser.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WebSnapshotColorExtractor {

    private const val SAMPLE_SIZE = 128
    private val mainHandler = Handler(Looper.getMainLooper())

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

    fun extractFromWebView(
        webView: WebView?,
        delayMs: Long = 100L,
        onColorExtracted: (Color?) -> Unit
    ) {
        if (webView == null) {
            onColorExtracted(null)
            return
        }

        mainHandler.postDelayed({
            if (!webView.isAttachedToWindow || webView.width <= 0 || webView.height <= 0) {
                onColorExtracted(null)
                return@postDelayed
            }

            webView.postVisualStateCallback(
                System.nanoTime(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        val bitmap = captureTopViewportSnapshot(webView)
                        if (bitmap == null) {
                            extractDomFallback(webView, onColorExtracted)
                            return
                        }

                        CoroutineScope(Dispatchers.Default).launch {
                            val snapshotColor = try {
                                extractDominantVibrantColor(bitmap)
                            } finally {
                                bitmap.recycle()
                            }

                            withContext(Dispatchers.Main) {
                                if (snapshotColor != null) {
                                    onColorExtracted(snapshotColor)
                                } else {
                                    extractDomFallback(webView, onColorExtracted)
                                }
                            }
                        }
                    }
                }
            )
        }, delayMs)
    }

    private fun captureTopViewportSnapshot(webView: WebView): Bitmap? {
        return runCatching {
            val bitmap = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val targetHeight = (webView.height * 0.60f).coerceAtLeast(1f)
            val scaleX = SAMPLE_SIZE.toFloat() / webView.width.toFloat()
            val scaleY = SAMPLE_SIZE.toFloat() / targetHeight

            canvas.scale(scaleX, scaleY)
            webView.draw(canvas)
            bitmap
        }.getOrNull()
    }

    private fun extractDominantVibrantColor(bitmap: Bitmap): Color? {
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        bitmap.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)

        val hsv = FloatArray(3)
        val binWeights = FloatArray(36)
        val binR = FloatArray(36)
        val binG = FloatArray(36)
        val binB = FloatArray(36)

        var chromaticPixelCount = 0

        for (pixel in pixels) {
            if (android.graphics.Color.alpha(pixel) < 128) continue

            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

            android.graphics.Color.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val saturation = hsv[1]
            val brightness = hsv[2]

            if (brightness > 0.94f && saturation < 0.12f) continue
            if (brightness < 0.10f) continue
            if (saturation < 0.14f) continue

            chromaticPixelCount++
            val bin = (hue / 10f).toInt().coerceIn(0, 35)
            val weight = saturation * saturation

            binWeights[bin] += weight
            binR[bin] += r * weight
            binG[bin] += g * weight
            binB[bin] += b * weight
        }

        if (chromaticPixelCount < 4) return null

        var bestBin = -1
        var maxWeight = 0f
        for (i in 0 until 36) {
            if (binWeights[i] > maxWeight) {
                maxWeight = binWeights[i]
                bestBin = i
            }
        }

        if (bestBin != -1 && maxWeight > 0f) {
            val avgR = (binR[bestBin] / maxWeight).toInt().coerceIn(0, 255)
            val avgG = (binG[bestBin] / maxWeight).toInt().coerceIn(0, 255)
            val avgB = (binB[bestBin] / maxWeight).toInt().coerceIn(0, 255)
            return Color(avgR, avgG, avgB)
        }

        return null
    }

    private fun extractDomFallback(webView: WebView, onColorExtracted: (Color?) -> Unit) {
        if (!webView.isAttachedToWindow) {
            onColorExtracted(null)
            return
        }
        webView.evaluateJavascript(DOM_COLOR_FALLBACK_SCRIPT) { raw ->
            val clean = raw?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
            onColorExtracted(parseCssColor(clean))
        }
    }

    private fun parseCssColor(value: String?): Color? {
        if (value.isNullOrBlank() || value == "null") return null
        return runCatching {
            if (value.startsWith("rgb")) {
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
        }.getOrNull()
    }
}