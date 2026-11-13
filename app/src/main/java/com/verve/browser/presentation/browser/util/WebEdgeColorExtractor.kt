package com.verve.browser.presentation.browser.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

data class WebScrollMetrics(
    val contentHeightPx: Int,
    val viewportHeightPx: Int,
    val scrollOffsetPx: Int
)

data class WebEdgeProbeResult(
    val topColor: Color?,
    val bottomColor: Color?,
    val scrollMetrics: WebScrollMetrics?
)

object WebEdgeColorExtractor {

    private const val MAX_PAYLOAD_LENGTH = 768

    private val EDGE_COLOR_PROBE_JS = """
        (() => {
            try {
                const w = window.innerWidth || document.documentElement.clientWidth || 0;
                const h = window.innerHeight || document.documentElement.clientHeight || 0;
                if (w <= 0 || h <= 0) return null;

                const sy = Math.max(0, window.scrollY || window.pageYOffset || 0);
                const sh = Math.max(
                    document.documentElement ? document.documentElement.scrollHeight : 0,
                    document.body ? document.body.scrollHeight : 0,
                    h
                );

                const parseRgba = (str) => {
                    if (!str || str === 'transparent') return [0, 0, 0, 0];
                    const m = str.match(/rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*[,\/]\s*([\d.]+))?\s*\)/i);
                    if (!m) return [0, 0, 0, 0];
                    return [
                        Math.max(0, Math.min(255, parseFloat(m[1]) || 0)),
                        Math.max(0, Math.min(255, parseFloat(m[2]) || 0)),
                        Math.max(0, Math.min(255, parseFloat(m[3]) || 0)),
                        m[4] !== undefined ? Math.max(0, Math.min(1, parseFloat(m[4]))) : 1
                    ];
                };

                const compositeLayers = (layers) => {
                    let r = 0, g = 0, b = 0, a = 0;
                    for (let i = layers.length - 1; i >= 0; i--) {
                        const [lr, lg, lb, la] = layers[i];
                        const outA = la + a * (1 - la);
                        if (outA > 0) {
                            r = (lr * la + r * a * (1 - la)) / outA;
                            g = (lg * la + g * a * (1 - la)) / outA;
                            b = (lb * la + b * a * (1 - la)) / outA;
                            a = outA;
                        }
                    }
                    return a >= 0.2 ? [Math.round(r), Math.round(g), Math.round(b)] : null;
                };

                const samplePoint = (x, y) => {
                    const stack = document.elementsFromPoint ? document.elementsFromPoint(x, y) : [];
                    const seen = new Set(stack);
                    const fullStack = Array.from(stack);
                    if (document.body && !seen.has(document.body)) fullStack.push(document.body);
                    if (document.documentElement && !seen.has(document.documentElement)) fullStack.push(document.documentElement);

                    const layers = [];
                    let accAlpha = 0;
                    let isComplex = false;

                    for (let i = 0; i < fullStack.length; i++) {
                        const el = fullStack[i];
                        if (!el || el.nodeType !== 1) continue;
                        const style = window.getComputedStyle(el);
                        if (!style || style.display === 'none' || style.visibility === 'hidden') continue;

                        const opacity = Math.max(0, Math.min(1, parseFloat(style.opacity)));
                        if (opacity <= 0.01) continue;

                        const tag = el.tagName;
                        if (tag === 'IMG' || tag === 'VIDEO' || tag === 'CANVAS' || tag === 'SVG') {
                            isComplex = true;
                        }
                        if (style.backgroundImage && style.backgroundImage !== 'none') {
                            isComplex = true;
                        }

                        const bg = parseRgba(style.backgroundColor);
                        const effA = bg[3] * opacity;
                        if (effA > 0.01) {
                            layers.push([bg[0], bg[1], bg[2], effA]);
                            accAlpha = accAlpha + effA * (1 - accAlpha);
                            if (accAlpha >= 0.985) break;
                        }
                    }

                    const rgb = compositeLayers(layers);
                    return {
                        rgb,
                        opaque: accAlpha >= 0.95,
                        complex: isComplex
                    };
                };

                const sampleEdge = (y) => {
                    const xs = [
                        Math.floor(w * 0.10),
                        Math.floor(w * 0.30),
                        Math.floor(w * 0.50),
                        Math.floor(w * 0.70),
                        Math.floor(w * 0.90)
                    ];
                    const samples = xs.map(x => samplePoint(x, y));
                    const complex = samples.some(s => s.complex);
                    const valid = samples.filter(s => s.rgb !== null);
                    if (valid.length === 0) {
                        return { rgb: null, opaque: false, complex };
                    }
                    let best = valid[0];
                    let bestScore = -1;
                    for (let i = 0; i < valid.length; i++) {
                        let score = 0;
                        for (let j = 0; j < valid.length; j++) {
                            const dist = Math.abs(valid[i].rgb[0] - valid[j].rgb[0]) +
                                         Math.abs(valid[i].rgb[1] - valid[j].rgb[1]) +
                                         Math.abs(valid[i].rgb[2] - valid[j].rgb[2]);
                            if (dist < 20) score++;
                        }
                        if (score > bestScore || (score === bestScore && i === 2)) {
                            bestScore = score;
                            best = valid[i];
                        }
                    }
                    return { rgb: best.rgb, opaque: best.opaque, complex };
                };

                // Pre-resolve the webpage's true footer background before it scrolls into view
                const resolveDocumentFooter = () => {
                    const candidates = [];
                    const footers = document.querySelectorAll('footer, [role="contentinfo"]');
                    for (let i = 0; i < footers.length; i++) {
                        const rect = footers[i].getBoundingClientRect();
                        if (rect.width >= w * 0.7 && rect.height >= 16) {
                            candidates.push({ el: footers[i], bottom: sy + rect.bottom });
                        }
                    }
                    if (candidates.length === 0 && document.body) {
                        const children = document.body.children;
                        for (let i = children.length - 1; i >= 0; i--) {
                            const rect = children[i].getBoundingClientRect();
                            if (rect.width >= w * 0.7 && rect.height >= 16) {
                                candidates.push({ el: children[i], bottom: sy + rect.bottom });
                                break;
                            }
                        }
                    }
                    if (candidates.length === 0) return null;
                    candidates.sort((a, b) => b.bottom - a.bottom);
                    let cur = candidates[0].el;
                    const layers = [];
                    let accAlpha = 0;
                    while (cur && cur.nodeType === 1) {
                        const style = window.getComputedStyle(cur);
                        if (style && style.display !== 'none' && style.visibility !== 'hidden') {
                            const opacity = Math.max(0, Math.min(1, parseFloat(style.opacity)));
                            const bg = parseRgba(style.backgroundColor);
                            const effA = bg[3] * opacity;
                            if (effA > 0.01) {
                                layers.push([bg[0], bg[1], bg[2], effA]);
                                accAlpha = accAlpha + effA * (1 - accAlpha);
                                if (accAlpha >= 0.985) break;
                            }
                        }
                        cur = cur.parentElement;
                    }
                    return compositeLayers(layers);
                };

                const top = sampleEdge(Math.min(3, Math.max(0, h - 1)));
                const bottom = sampleEdge(Math.max(0, h - 3));
                const footerRgb = resolveDocumentFooter();

                const metaTheme = document.querySelector('meta[name="theme-color"]');
                const themeColor = metaTheme ? (metaTheme.getAttribute('content') || '').trim().slice(0, 32) : '';

                return JSON.stringify({
                    t: top.rgb,
                    to: top.opaque,
                    tc: top.complex,
                    b: bottom.rgb,
                    bo: bottom.opaque,
                    bc: bottom.complex,
                    f: footerRgb,
                    m: themeColor,
                    vh: h,
                    sh: sh,
                    sy: sy
                });
            } catch (e) {
                return null;
            }
        })();
    """.trimIndent()

    fun extractEdgeProbe(
        webView: WebView?,
        isApproachingBottom: Boolean = false,
        onResult: (WebEdgeProbeResult) -> Unit
    ) {
        val view = webView ?: return
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 4) return

        runCatching {
            view.evaluateJavascript(EDGE_COLOR_PROBE_JS) { rawResult ->
                if (!view.isAttachedToWindow) return@evaluateJavascript
                val parsed = parseProbeResponse(view, rawResult, isApproachingBottom)
                onResult(parsed)
            }
        }
    }

    private fun parseProbeResponse(
        webView: WebView,
        rawResult: String?,
        isApproachingBottom: Boolean
    ): WebEdgeProbeResult {
        if (rawResult.isNullOrBlank() || rawResult == "null" || rawResult == "\"null\"") {
            return sampleFallbackStrips(
                webView = webView,
                needTop = true,
                needBottom = true,
                domTop = null,
                domBottom = null,
                metaTheme = null,
                scrollMetrics = null
            )
        }

        return runCatching {
            val unquoted = unquoteJsJsonString(rawResult)
            if (unquoted.isEmpty() || unquoted.length > MAX_PAYLOAD_LENGTH) {
                return@runCatching WebEdgeProbeResult(null, null, null)
            }

            val json = JSONObject(unquoted)
            val domTop = parseRgbArray(json.optJSONArray("t"))
            val topOpaque = json.optBoolean("to", false)
            val topComplex = json.optBoolean("tc", false)

            val domBottom = parseRgbArray(json.optJSONArray("b"))
            val bottomOpaque = json.optBoolean("bo", false)
            val bottomComplex = json.optBoolean("bc", false)
            val footerRgb = parseRgbArray(json.optJSONArray("f"))

            val metaTheme = parseCssColorHex(json.optString("m", ""))

            val cssVh = json.optDouble("vh", 0.0)
            val cssSh = json.optDouble("sh", 0.0)
            val cssSy = json.optDouble("sy", 0.0)
            val scrollMetrics = if (cssVh > 1.0 && cssSh >= cssVh && webView.height > 0) {
                val cssToViewScale = webView.height.toDouble() / cssVh
                WebScrollMetrics(
                    contentHeightPx = (cssSh * cssToViewScale).roundToInt(),
                    viewportHeightPx = webView.height,
                    scrollOffsetPx = (cssSy * cssToViewScale).roundToInt()
                )
            } else {
                null
            }

            val resolvedDomBottom = if (isApproachingBottom && footerRgb != null) {
                footerRgb
            } else {
                domBottom ?: footerRgb
            }

            val needTopStrip = topComplex || !topOpaque || domTop == null
            val needBottomStrip = (!isApproachingBottom || footerRgb == null) &&
                    (bottomComplex || !bottomOpaque || resolvedDomBottom == null)

            if (needTopStrip || needBottomStrip) {
                sampleFallbackStrips(
                    webView = webView,
                    needTop = needTopStrip,
                    needBottom = needBottomStrip,
                    domTop = domTop,
                    domBottom = resolvedDomBottom,
                    metaTheme = metaTheme,
                    scrollMetrics = scrollMetrics
                )
            } else {
                WebEdgeProbeResult(
                    topColor = domTop,
                    bottomColor = resolvedDomBottom,
                    scrollMetrics = scrollMetrics
                )
            }
        }.getOrElse {
            WebEdgeProbeResult(null, null, null)
        }
    }

    private fun sampleFallbackStrips(
        webView: WebView,
        needTop: Boolean,
        needBottom: Boolean,
        domTop: Color?,
        domBottom: Color?,
        metaTheme: Color?,
        scrollMetrics: WebScrollMetrics?
    ): WebEdgeProbeResult {
        val topStripColor = if (needTop) {
            sampleRenderedEdgeStrip(webView, isTop = true) ?: domTop ?: metaTheme
        } else {
            domTop
        }

        val bottomStripColor = if (needBottom) {
            sampleRenderedEdgeStrip(webView, isTop = false) ?: domBottom
        } else {
            domBottom
        }

        return WebEdgeProbeResult(
            topColor = topStripColor,
            bottomColor = bottomStripColor,
            scrollMetrics = scrollMetrics
        )
    }

    private fun sampleRenderedEdgeStrip(webView: WebView, isTop: Boolean): Color? {
        val viewWidth = webView.width
        val viewHeight = webView.height
        if (viewWidth <= 0 || viewHeight <= 4) return null

        return runCatching {
            val sampleWidth = 72
            val stripHeight = 2
            val bitmap = Bitmap.createBitmap(sampleWidth, stripHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                val scaleX = sampleWidth.toFloat() / viewWidth.toFloat()
                val yOffset = if (isTop) 0f else -(viewHeight - stripHeight).toFloat()
                canvas.scale(scaleX, 1f)
                canvas.translate(0f, yOffset)
                webView.draw(canvas)

                val probeXs = intArrayOf(6, 16, 26, 36, 46, 56, 66)
                val samples = ArrayList<Int>(probeXs.size)
                val row = if (isTop) 1 else 0
                for (px in probeXs) {
                    val pixel = bitmap.getPixel(px.coerceIn(0, sampleWidth - 1), row)
                    if (android.graphics.Color.alpha(pixel) >= 200) {
                        samples.add(pixel)
                    }
                }

                if (samples.isEmpty()) return@runCatching null

                var bestPixel = samples[samples.size / 2]
                var bestClusterSize = -1
                for (i in samples.indices) {
                    val c1 = samples[i]
                    var count = 0
                    for (j in samples.indices) {
                        val c2 = samples[j]
                        val dist = abs(android.graphics.Color.red(c1) - android.graphics.Color.red(c2)) +
                                abs(android.graphics.Color.green(c1) - android.graphics.Color.green(c2)) +
                                abs(android.graphics.Color.blue(c1) - android.graphics.Color.blue(c2))
                        if (dist < 24) count++
                    }
                    if (count > bestClusterSize) {
                        bestClusterSize = count
                        bestPixel = c1
                    }
                }

                Color(
                    red = android.graphics.Color.red(bestPixel),
                    green = android.graphics.Color.green(bestPixel),
                    blue = android.graphics.Color.blue(bestPixel)
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    private fun parseRgbArray(array: org.json.JSONArray?): Color? {
        if (array == null || array.length() != 3) return null
        val r = array.optInt(0, -1)
        val g = array.optInt(1, -1)
        val b = array.optInt(2, -1)
        if (r !in 0..255 || g !in 0..255 || b !in 0..255) return null
        return Color(red = r, green = g, blue = b)
    }

    private fun parseCssColorHex(raw: String): Color? {
        if (!raw.startsWith("#")) return null
        val hex = raw.removePrefix("#")
        return runCatching {
            when (hex.length) {
                3 -> {
                    val r = hex[0].digitToInt(16) * 17
                    val g = hex[1].digitToInt(16) * 17
                    val b = hex[2].digitToInt(16) * 17
                    Color(r, g, b)
                }
                6 -> {
                    val value = hex.toInt(16)
                    Color(
                        red = (value shr 16) and 0xFF,
                        green = (value shr 8) and 0xFF,
                        blue = value and 0xFF
                    )
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun unquoteJsJsonString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return JSONObject("{\"v\":$trimmed}").optString("v", "")
        }
        return trimmed
    }

    fun isPerceptuallySimilar(a: Color?, b: Color?, threshold: Int = 10): Boolean {
        if (a == null || b == null) return a == b
        val dr = abs((a.red * 255f).roundToInt() - (b.red * 255f).roundToInt())
        val dg = abs((a.green * 255f).roundToInt() - (b.green * 255f).roundToInt())
        val db = abs((a.blue * 255f).roundToInt() - (b.blue * 255f).roundToInt())
        return (dr + dg + db) < threshold
    }
}