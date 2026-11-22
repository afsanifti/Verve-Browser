package com.verve.browser.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.domain.model.CosmeticResources
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.repository.AdBlockRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.core.net.toUri
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AdBlockRepositoryImpl(
    private val context: Context,
    private val nativeBlocker: NativeAdBlocker
) : AdBlockRepository {

    companion object {
        private const val TAG = "AdBlockRepository"
        private const val PREFS_NAME = "verve_adblock_prefs"
        private const val KEY_WHITELISTED_DOMAINS = "whitelisted_domains"
        private const val KEY_LAST_UPDATED_MS = "last_updated_ms"

        private const val DIR_ADBLOCK = "adblock"
        private const val FILE_ENGINE_DAT = "engine_v1.dat"
        private const val FILE_RESOURCES_JSON = "resources.json"
        private const val ASSET_DIR_ADBLOCK = "adblock"

        private val CURATED_FILTER_SOURCES = listOf(
            "easylist.txt" to "https://easylist.to/easylist/easylist.txt",
            "easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt",
            "ublock-filters.txt" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
            "ublock-privacy.txt" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
            "ublock-quick-fixes.txt" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/quick-fixes.txt",
            "ublock-unbreak.txt" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt",
            "brave-unbreak.txt" to "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-unbreak.txt"
        )

        private const val RESOURCES_JSON_URL =
            "https://raw.githubusercontent.com/brave/adblock-resources/master/dist/resources.json"
    }

    private val appContext = context.applicationContext
    private val buildMutex = Mutex()
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val whitelistedHosts = ConcurrentHashMap.newKeySet<String>().apply {
        val saved = runCatching {
            prefs.getStringSet(KEY_WHITELISTED_DOMAINS, emptySet()).orEmpty()
        }.getOrDefault(emptySet())
        addAll(saved.map { it.lowercase() })
    }

    @Volatile
    private var isFullEngineLoaded = false
    private val fullEngineReadyLatch = CountDownLatch(1)
    private val asyncInitExpected = AtomicBoolean(false)
    private val asyncInitInFlight = AtomicInteger(0)

    override fun initializeEngine(initialRules: List<String>) {
        if (!nativeBlocker.isLibraryLoaded || isFullEngineLoaded) return
        if (initialRules.isNotEmpty()) {
            nativeBlocker.initEngine(initialRules.joinToString("\n"))
        }
    }

    override fun prepareAsyncEngineInit() {
        if (!isFullEngineLoaded) {
            asyncInitExpected.set(true)
        }
    }

    override suspend fun initializeEngineAsync() = withContext(Dispatchers.IO) {
        if (!nativeBlocker.isLibraryLoaded) {
            fullEngineReadyLatch.countDown()
            return@withContext
        }
        asyncInitExpected.set(true)
        asyncInitInFlight.incrementAndGet()
        try {
            buildMutex.withLock {
                if (isFullEngineLoaded) return@withLock

                val adblockDir = File(appContext.filesDir, DIR_ADBLOCK).apply { mkdirs() }
                val datFile = File(adblockDir, FILE_ENGINE_DAT)
                val cachedResourcesFile = File(adblockDir, FILE_RESOURCES_JSON)
                val resourcesJson = readBestResourcesJson(cachedResourcesFile)

                // 1. Fast path: restore pre-compiled binary .dat snapshot (~15-25 ms)
                if (datFile.exists() && datFile.length() > 0L) {
                    val loaded = runCatching {
                        nativeBlocker.safeLoadSerializedEngine(datFile.readBytes(), resourcesJson)
                    }.getOrDefault(false)

                    if (loaded) {
                        isFullEngineLoaded = true
                        Log.i(TAG, "Restored pre-compiled adblock engine (${datFile.length() / 1024} KB)")
                        return@withLock
                    } else if (nativeBlocker.supportsV2Engine) {
                        datFile.delete()
                    }
                }

                // 2. Local assets / cached .txt path
                val localRules = readAllLocalRules(adblockDir)
                if (localRules.isNotBlank() && resourcesJson.isNotBlank()) {
                    if (compileAndPersistLocked(adblockDir, localRules, resourcesJson)) {
                        return@withLock
                    }
                }

                // 3. First-launch network bootstrap: fetch lists + dist/resources.json immediately
                Log.i(TAG, "No cached filter snapshot found; downloading curated filter lists...")
                downloadAndCompileLocked(adblockDir)
            }
        } finally {
            if (isFullEngineLoaded || asyncInitInFlight.decrementAndGet() <= 0) {
                fullEngineReadyLatch.countDown()
            }
        }
    }

    private fun awaitFullEngineReadyIfBackground(timeoutMs: Long = 1_500L) {
        if (isFullEngineLoaded) return
        if (!asyncInitExpected.get() && asyncInitInFlight.get() <= 0) return
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull()
        if (mainLooper != null && Looper.myLooper() == mainLooper) return
        runCatching {
            fullEngineReadyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    override suspend fun updateFilterListsFromNetwork(): Boolean = withContext(Dispatchers.IO) {
        if (!nativeBlocker.isLibraryLoaded) return@withContext false
        buildMutex.withLock {
            val adblockDir = File(appContext.filesDir, DIR_ADBLOCK).apply { mkdirs() }
            downloadAndCompileLocked(adblockDir)
        }
    }

    private suspend fun downloadAndCompileLocked(adblockDir: File): Boolean = coroutineScope {
        val listJobs = CURATED_FILTER_SOURCES.map { (fileName, url) ->
            async(Dispatchers.IO) {
                val targetFile = File(adblockDir, fileName)
                val etagFile = File(adblockDir, "$fileName.etag")
                if (!targetFile.exists() && etagFile.exists()) {
                    etagFile.delete()
                }
                val body = fetchTextWithConditionalHeaders(url, etagFile)
                if (!body.isNullOrBlank()) {
                    targetFile.writeText(body)
                    true
                } else {
                    false
                }
            }
        }

        val resourcesJob = async(Dispatchers.IO) {
            val targetFile = File(adblockDir, FILE_RESOURCES_JSON)
            val etagFile = File(adblockDir, "$FILE_RESOURCES_JSON.etag")
            if (!targetFile.exists() && etagFile.exists()) {
                etagFile.delete()
            }
            val updatedResources = fetchTextWithConditionalHeaders(RESOURCES_JSON_URL, etagFile)
            if (!updatedResources.isNullOrBlank()) {
                targetFile.writeText(updatedResources)
                true
            } else {
                false
            }
        }

        val anyDownloaded = listJobs.awaitAll().any { it } || resourcesJob.await()
        if (!anyDownloaded && isFullEngineLoaded) {
            return@coroutineScope true
        }

        val combinedRules = readAllLocalRules(adblockDir)
        if (combinedRules.isBlank()) {
            Log.w(TAG, "Filter list download returned no rules (offline or network blocked)")
            return@coroutineScope false
        }

        val resourcesJson = readBestResourcesJson(File(adblockDir, FILE_RESOURCES_JSON))
        compileAndPersistLocked(adblockDir, combinedRules, resourcesJson)
    }

    private fun compileAndPersistLocked(
        adblockDir: File,
        combinedRules: String,
        resourcesJson: String
    ): Boolean {
        val serialized = nativeBlocker.safeInitEngineFromLists(combinedRules, resourcesJson)
        isFullEngineLoaded = true
        if (serialized != null && serialized.isNotEmpty()) {
            runCatching {
                val datFile = File(adblockDir, FILE_ENGINE_DAT)
                val tmpFile = File(adblockDir, "$FILE_ENGINE_DAT.tmp")
                tmpFile.writeBytes(serialized)
                tmpFile.renameTo(datFile)
            }
            prefs.edit().putLong(KEY_LAST_UPDATED_MS, System.currentTimeMillis()).apply()
            Log.i(
                TAG,
                "Compiled full adblock engine (${serialized.size / 1024} KB, resources=${resourcesJson.length / 1024} KB)"
            )
            return true
        }
        Log.i(TAG, "Initialized adblock engine in v1 compatibility mode")
        return true
    }

    override fun shouldBlockUrl(
        targetUrl: String,
        pageUrl: String,
        resourceType: String,
        method: String
    ): UrlCheckResult {
        if (!nativeBlocker.isLibraryLoaded) return UrlCheckResult.Allow
        awaitFullEngineReadyIfBackground()
        val evaluation = nativeBlocker.safeEvaluateRequest(
            url = targetUrl,
            sourceUrl = pageUrl.ifBlank { targetUrl },
            resourceType = resourceType,
            method = method
        ) ?: return UrlCheckResult.Allow

        if (evaluation.size < 3) return UrlCheckResult.Allow
        return when (evaluation[0]) {
            "REDIRECT" -> {
                val dataUri = evaluation[1]
                if (dataUri.startsWith("data:", ignoreCase = true)) {
                    UrlCheckResult.Redirect(dataUri)
                } else {
                    UrlCheckResult.Block()
                }
            }
            "BLOCK" -> UrlCheckResult.Block()
            "REWRITE" -> {
                val rewritten = evaluation[2]
                if (rewritten.isNotBlank() && rewritten != targetUrl) {
                    UrlCheckResult.RewriteUrl(rewritten)
                } else {
                    UrlCheckResult.Allow
                }
            }
            else -> UrlCheckResult.Allow
        }
    }

    override fun getCosmeticResources(pageUrl: String): CosmeticResources {
        if (!nativeBlocker.isLibraryLoaded || pageUrl.isBlank()) {
            return CosmeticResources()
        }
        awaitFullEngineReadyIfBackground()
        val rawJson = nativeBlocker.safeGetCosmeticResources(pageUrl) ?: return CosmeticResources()
        return runCatching {
            val obj = JSONObject(rawJson)
            val hideArr = obj.optJSONArray("hideSelectors")
            val hideList = buildList {
                if (hideArr != null) {
                    for (i in 0 until hideArr.length()) {
                        val item = hideArr.optString(i)
                        if (item.isNotBlank()) add(item)
                    }
                }
            }
            val excArr = obj.optJSONArray("exceptions")
            val excList = buildList {
                if (excArr != null) {
                    for (i in 0 until excArr.length()) {
                        val item = excArr.optString(i)
                        if (item.isNotBlank()) add(item)
                    }
                }
            }
            CosmeticResources(
                hideSelectors = hideList,
                exceptions = excList,
                injectedScript = obj.optString("injectedScript", ""),
                genericHide = obj.optBoolean("generichide", false)
            )
        }.getOrDefault(CosmeticResources())
    }

    override fun getHiddenClassIdSelectors(
        classes: List<String>,
        ids: List<String>,
        exceptions: List<String>
    ): List<String> {
        if (!nativeBlocker.isLibraryLoaded || (classes.isEmpty() && ids.isEmpty())) {
            return emptyList()
        }
        awaitFullEngineReadyIfBackground()
        val matched = nativeBlocker.safeGetHiddenClassIdSelectors(
            classes = classes.toTypedArray(),
            ids = ids.toTypedArray(),
            exceptions = exceptions.toTypedArray()
        ) ?: return emptyList()
        return matched.filter { it.isNotBlank() }
    }

    override fun isSiteWhitelisted(pageUrl: String): Boolean {
        val host = extractNormalizedHost(pageUrl) ?: return false
        return whitelistedHosts.contains(host)
    }

    override fun toggleSiteWhitelist(pageUrl: String): Boolean {
        val host = extractNormalizedHost(pageUrl) ?: return false
        val nowWhitelisted = if (whitelistedHosts.contains(host)) {
            whitelistedHosts.remove(host)
            false
        } else {
            whitelistedHosts.add(host)
            true
        }
        prefs.edit()
            .putStringSet(KEY_WHITELISTED_DOMAINS, whitelistedHosts.toSet())
            .apply()
        return nowWhitelisted
    }

    private fun readBestResourcesJson(cachedFile: File): String {
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            runCatching { cachedFile.readText() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }
        return runCatching {
            appContext.assets.open("$ASSET_DIR_ADBLOCK/$FILE_RESOURCES_JSON")
                .bufferedReader()
                .use { it.readText() }
        }.getOrDefault("")
    }

    private fun readAllLocalRules(adblockDir: File): String {
        val builder = StringBuilder(4 * 1024 * 1024)
        for ((fileName, _) in CURATED_FILTER_SOURCES) {
            val downloadedFile = File(adblockDir, fileName)
            val content = if (downloadedFile.exists() && downloadedFile.length() > 0L) {
                runCatching { downloadedFile.readText() }.getOrNull()
            } else {
                runCatching {
                    appContext.assets.open("$ASSET_DIR_ADBLOCK/$fileName")
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrNull()
            }
            if (!content.isNullOrBlank()) {
                builder.append(content).append('\n')
            }
        }
        return builder.toString()
    }

    private fun fetchTextWithConditionalHeaders(urlString: String, etagFile: File): String? {
        return runCatching {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("User-Agent", "VerveBrowser-AdBlock/1.0")
                if (etagFile.exists()) {
                    val savedEtag = etagFile.readText().trim()
                    if (savedEtag.isNotEmpty()) {
                        setRequestProperty("If-None-Match", savedEtag)
                    }
                }
            }
            try {
                when (conn.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> null
                    HttpURLConnection.HTTP_OK -> {
                        val newEtag = conn.getHeaderField("ETag")
                        if (!newEtag.isNullOrBlank()) {
                            etagFile.writeText(newEtag.trim())
                        }
                        conn.inputStream.bufferedReader().use { it.readText() }
                    }
                    else -> null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun extractNormalizedHost(url: String): String? {
        if (url.isBlank()) return null
        return runCatching { url.toUri().host?.lowercase()?.removePrefix("www.") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}