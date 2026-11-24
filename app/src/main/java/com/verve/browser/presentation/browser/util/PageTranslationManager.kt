package com.verve.browser.presentation.browser.util

import android.webkit.WebView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class TranslateLanguageOption(
    val displayName: String,
    val nativeName: String,
    val code: String
)

internal object PageTranslationManager {

    val supportedLanguages: List<TranslateLanguageOption> = listOf(
        TranslateLanguageOption("English", "English", "en"),
        TranslateLanguageOption("Bengali", "বাংলা", "bn"),
        TranslateLanguageOption("Hindi", "हिन्दी", "hi"),
        TranslateLanguageOption("Arabic", "العربية", "ar"),
        TranslateLanguageOption("Spanish", "Español", "es"),
        TranslateLanguageOption("French", "Français", "fr"),
        TranslateLanguageOption("German", "Deutsch", "de"),
        TranslateLanguageOption("Portuguese", "Português", "pt"),
        TranslateLanguageOption("Russian", "Русский", "ru"),
        TranslateLanguageOption("Japanese", "日本語", "ja"),
        TranslateLanguageOption("Korean", "한국어", "ko"),
        TranslateLanguageOption("Chinese (Simplified)", "简体中文", "zh-CN"),
        TranslateLanguageOption("Chinese (Traditional)", "繁體中文", "zh-TW"),
        TranslateLanguageOption("Turkish", "Türkçe", "tr"),
        TranslateLanguageOption("Indonesian", "Bahasa Indonesia", "id"),
        TranslateLanguageOption("Urdu", "اردو", "ur"),
        TranslateLanguageOption("Italian", "Italiano", "it"),
        TranslateLanguageOption("Dutch", "Nederlands", "nl"),
        TranslateLanguageOption("Vietnamese", "Tiếng Việt", "vi"),
        TranslateLanguageOption("Thai", "ไทย", "th")
    )

    private const val FIRST_BATCH_CHAR_LIMIT = 2_400
    private const val NEXT_BATCH_CHAR_LIMIT = 4_500
    private const val MAX_CONCURRENT_REQUESTS = 4

    private val markerRegex = Regex("""\[\s*\[\s*(\d+)\s*\]\s*\]\s*""")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val translationCache = ConcurrentHashMap<String, String>()

    fun resolveLanguageCode(displayName: String): String {
        return supportedLanguages.firstOrNull {
            it.displayName.equals(displayName, ignoreCase = true)
        }?.code ?: "en"
    }

    fun cancel(webView: WebView?) {
        val key = webView?.hashCode() ?: return
        activeJobs.remove(key)?.cancel()
    }

    fun translatePage(
        webView: WebView,
        targetLanguageName: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val targetCode = resolveLanguageCode(targetLanguageName)
        val viewKey = webView.hashCode()
        activeJobs.remove(viewKey)?.cancel()

        val job = scope.launch {
            try {
                val rawSnapshot = evaluateJsSuspend(webView, buildCollectScript())
                val snapshotJson = decodeJsJsonString(rawSnapshot)
                if (snapshotJson.isNullOrBlank()) {
                    onComplete(false)
                    return@launch
                }

                val payload = JSONObject(snapshotJson)
                val title = payload.optString("title", "")
                val textsArray = payload.optJSONArray("texts") ?: JSONArray()
                val priorityArray = payload.optJSONArray("priorityOrder") ?: JSONArray()

                // Collect unique strings in viewport-first priority order so visible text translates first
                val uniqueTexts = LinkedHashSet<String>()
                if (title.isNotBlank()) uniqueTexts.add(title)

                if (priorityArray.length() == textsArray.length()) {
                    for (i in 0 until priorityArray.length()) {
                        val entryIdx = priorityArray.optInt(i, -1)
                        if (entryIdx in 0 until textsArray.length()) {
                            val item = textsArray.optString(entryIdx)
                            if (item.isNotBlank()) uniqueTexts.add(item)
                        }
                    }
                } else {
                    for (i in 0 until textsArray.length()) {
                        val item = textsArray.optString(i)
                        if (item.isNotBlank()) uniqueTexts.add(item)
                    }
                }

                if (uniqueTexts.isEmpty()) {
                    onComplete(false)
                    return@launch
                }

                val resolvedMap = ConcurrentHashMap<String, String>(uniqueTexts.size)
                val uncached = ArrayList<String>()

                for (text in uniqueTexts) {
                    val cached = translationCache["$targetCode::$text"]
                    if (cached != null) {
                        resolvedMap[text] = cached
                    } else {
                        uncached.add(text)
                    }
                }

                if (uncached.isEmpty()) {
                    applyTranslationMap(webView, title, textsArray, resolvedMap, targetCode)
                    onComplete(true)
                    return@launch
                }

                val batches = buildBatches(uncached)
                val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

                withContext(Dispatchers.IO) {
                    coroutineScope {
                        val deferredBatches = batches.map { batch ->
                            async {
                                semaphore.withPermit {
                                    translateBatch(batch, targetCode, resolvedMap)
                                }
                            }
                        }

                        // Apply the first (in-viewport) batch immediately as soon as it arrives
                        deferredBatches.firstOrNull()?.await()
                        if (resolvedMap.isNotEmpty()) {
                            withContext(Dispatchers.Main.immediate) {
                                applyTranslationMap(webView, title, textsArray, resolvedMap, targetCode)
                                onComplete(true)
                            }
                        }

                        // Finish remaining off-screen batches in parallel and patch the rest of the DOM
                        if (deferredBatches.size > 1) {
                            deferredBatches.drop(1).awaitAll()
                            if (resolvedMap.isNotEmpty()) {
                                withContext(Dispatchers.Main.immediate) {
                                    applyTranslationMap(webView, title, textsArray, resolvedMap, targetCode)
                                }
                            }
                        } else if (resolvedMap.isEmpty()) {
                            withContext(Dispatchers.Main.immediate) {
                                onComplete(false)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onComplete(false)
            } finally {
                activeJobs.remove(viewKey)
            }
        }

        activeJobs[viewKey] = job
    }

    fun restore(webView: WebView) {
        cancel(webView)
        val script = """
            (function() {
                var state = window.__verveTranslateState;
                if (!state) return;
                if (typeof state.origTitle === 'string') {
                    document.title = state.origTitle;
                }
                if (typeof state.origLang === 'string') {
                    document.documentElement.lang = state.origLang;
                }
                if (Array.isArray(state.entries)) {
                    for (var i = 0; i < state.entries.length; i++) {
                        var entry = state.entries[i];
                        if (entry && entry.node) {
                            entry.node.nodeValue = entry.orig;
                        }
                    }
                }
                window.__verveTranslateState = null;
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private suspend fun applyTranslationMap(
        webView: WebView,
        title: String,
        textsArray: JSONArray,
        translatedMap: Map<String, String>,
        targetCode: String
    ) {
        val resultPayload = JSONObject().apply {
            if (title.isNotBlank()) {
                val translatedTitle = translatedMap[title]
                if (!translatedTitle.isNullOrBlank()) {
                    put("title", translatedTitle)
                }
            }
            val outArray = JSONArray()
            for (i in 0 until textsArray.length()) {
                val orig = textsArray.optString(i)
                outArray.put(translatedMap[orig].orEmpty())
            }
            put("texts", outArray)
        }
        evaluateJsSuspend(webView, buildApplyScript(resultPayload.toString(), targetCode))
    }

    private fun buildBatches(uncached: List<String>): List<List<String>> {
        val batches = ArrayList<List<String>>()
        var index = 0
        while (index < uncached.size) {
            val limit = if (batches.isEmpty()) FIRST_BATCH_CHAR_LIMIT else NEXT_BATCH_CHAR_LIMIT
            val batch = ArrayList<String>()
            var charCount = 0

            while (index < uncached.size) {
                val candidate = uncached[index]
                val weight = candidate.length + 8
                if (batch.isNotEmpty() && charCount + weight > limit) {
                    break
                }
                batch.add(candidate)
                charCount += weight
                index++
            }
            batches.add(batch)
        }
        return batches
    }

    private suspend fun translateBatch(
        batch: List<String>,
        targetLangCode: String,
        resolvedMap: ConcurrentHashMap<String, String>
    ) {
        if (batch.isEmpty()) return

        if (batch.size == 1) {
            val single = batch[0]
            val translated = requestGoogleTranslate(single, targetLangCode)
            if (!translated.isNullOrBlank()) {
                resolvedMap[single] = translated
                translationCache["$targetLangCode::$single"] = translated
            }
            return
        }

        val joinedQuery = batch.mapIndexed { i, text -> "[[$i]] $text" }.joinToString("\n")
        val translatedBatch = requestGoogleTranslate(joinedQuery, targetLangCode)

        val missingIndices = ArrayList<Int>()
        if (translatedBatch != null) {
            val matches = markerRegex.findAll(translatedBatch).toList()
            val parsedByIndex = HashMap<Int, String>(matches.size)

            for (m in matches.indices) {
                val match = matches[m]
                val idx = match.groupValues[1].toIntOrNull() ?: continue
                if (idx !in batch.indices) continue
                val contentStart = match.range.last + 1
                val contentEnd = if (m + 1 < matches.size) {
                    matches[m + 1].range.first
                } else {
                    translatedBatch.length
                }
                if (contentStart <= contentEnd) {
                    val segment = translatedBatch.substring(contentStart, contentEnd).trim()
                    if (segment.isNotEmpty()) {
                        parsedByIndex[idx] = segment
                    }
                }
            }

            for (i in batch.indices) {
                val orig = batch[i]
                val translated = parsedByIndex[i]
                if (!translated.isNullOrBlank()) {
                    resolvedMap[orig] = translated
                    translationCache["$targetLangCode::$orig"] = translated
                } else {
                    missingIndices.add(i)
                }
            }
        } else {
            missingIndices.addAll(batch.indices)
        }

        // Only translate any unmatched items, and do them concurrently
        if (missingIndices.isNotEmpty()) {
            coroutineScope {
                missingIndices.map { idx ->
                    async {
                        val orig = batch[idx]
                        val translated = requestGoogleTranslate(orig, targetLangCode)
                        if (!translated.isNullOrBlank()) {
                            resolvedMap[orig] = translated
                            translationCache["$targetLangCode::$orig"] = translated
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun requestGoogleTranslate(
        text: String,
        targetLangCode: String
    ): String? = suspendCancellableCoroutine { continuation ->
        val endpoint =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLangCode&dt=t"
        var connection: HttpURLConnection? = null

        continuation.invokeOnCancellation {
            runCatching { connection?.disconnect() }
        }

        val result = runCatching {
            val postBody = ("q=" + URLEncoder.encode(text, "UTF-8")).toByteArray(Charsets.UTF_8)
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6_000
                readTimeout = 6_000
                setFixedLengthStreamingMode(postBody.size)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
            }
            connection = conn

            conn.outputStream.use { it.write(postBody) }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rootArray = JSONArray(body)
            val sentences = rootArray.optJSONArray(0) ?: return@runCatching null
            buildString {
                for (i in 0 until sentences.length()) {
                    val segment = sentences.optJSONArray(i)?.optString(0).orEmpty()
                    append(segment)
                }
            }.takeIf { it.isNotBlank() }
        }.getOrNull()

        runCatching { connection?.disconnect() }
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }

    private suspend fun evaluateJsSuspend(webView: WebView, script: String): String? =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }

    private fun decodeJsJsonString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            JSONArray("[$raw]").getString(0)
        }.getOrNull()
    }

    private fun buildCollectScript(): String = """
        (function() {
            var state = window.__verveTranslateState;
            if (!state) {
                var skippedTags = {
                    SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, TEXTAREA: 1,
                    INPUT: 1, SELECT: 1, OPTION: 1, CODE: 1, PRE: 1, SVG: 1
                };
                var entries = [];
                var walker = document.createTreeWalker(
                    document.body,
                    NodeFilter.SHOW_TEXT,
                    {
                        acceptNode: function(node) {
                            var parent = node.parentElement;
                            if (!parent) return NodeFilter.FILTER_REJECT;
                            if (skippedTags[parent.tagName]) return NodeFilter.FILTER_REJECT;
                            if (parent.isContentEditable) return NodeFilter.FILTER_REJECT;
                            if (parent.closest('[translate="no"], .notranslate')) {
                                return NodeFilter.FILTER_REJECT;
                            }
                            var text = (node.nodeValue || '').trim();
                            if (text.length < 2) return NodeFilter.FILTER_REJECT;
                            if (/^[\d\s.,:%+\-–—/\\|•·()\[\]{}]+$/.test(text)) {
                                return NodeFilter.FILTER_REJECT;
                            }
                            return NodeFilter.FILTER_ACCEPT;
                        }
                    }
                );

                var current;
                while ((current = walker.nextNode()) && entries.length < 450) {
                    var raw = current.nodeValue || '';
                    var leadingMatch = raw.match(/^\s*/);
                    var trailingMatch = raw.match(/\s*$/);
                    entries.push({
                        node: current,
                        orig: raw,
                        core: raw.trim(),
                        lead: leadingMatch ? leadingMatch[0] : '',
                        trail: trailingMatch ? trailingMatch[0] : ''
                    });
                }

                state = {
                    origTitle: document.title || '',
                    origLang: document.documentElement.lang || '',
                    entries: entries
                };
                window.__verveTranslateState = state;
            }

            var viewH = window.innerHeight || document.documentElement.clientHeight || 800;
            var texts = [];
            var inView = [];
            var offScreen = [];

            for (var i = 0; i < state.entries.length; i++) {
                texts.push(state.entries[i].core);
                var el = state.entries[i].node.parentElement;
                var rect = el ? el.getBoundingClientRect() : null;
                if (rect && rect.height > 0 && rect.bottom >= -100 && rect.top <= viewH + 200) {
                    inView.push(i);
                } else {
                    offScreen.push(i);
                }
            }

            return JSON.stringify({
                title: state.origTitle,
                texts: texts,
                priorityOrder: inView.concat(offScreen)
            });
        })();
    """.trimIndent()

    private fun buildApplyScript(payloadJson: String, targetCode: String): String {
        val escapedPayload = JSONObject.quote(payloadJson)
        val escapedLang = JSONObject.quote(targetCode)
        return """
            (function() {
                var state = window.__verveTranslateState;
                if (!state || !Array.isArray(state.entries)) return;
                var data = JSON.parse($escapedPayload);
                if (typeof data.title === 'string' && data.title.length > 0) {
                    document.title = data.title;
                }
                document.documentElement.lang = $escapedLang;
                var list = data.texts || [];
                var count = Math.min(state.entries.length, list.length);
                for (var i = 0; i < count; i++) {
                    var entry = state.entries[i];
                    var translated = list[i];
                    if (entry && entry.node && typeof translated === 'string' && translated.length > 0) {
                        entry.node.nodeValue = entry.lead + translated + entry.trail;
                    }
                }
            })();
        """.trimIndent()
    }
}