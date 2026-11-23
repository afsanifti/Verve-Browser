package com.verve.browser.data.repository

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.data.adblock.CnameResolver
import com.verve.browser.domain.model.AdBlockMode
import com.verve.browser.domain.model.CosmeticResources
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.repository.AdBlockRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AdBlockRepositoryImpl(
    private val context: Context,
    private val nativeBlocker: NativeAdBlocker,
    private val cnameResolver: CnameResolver = CnameResolver()
) : AdBlockRepository {

    companion object {
        private const val TAG = "AdBlockRepository"
        private const val PREFS_NAME = "verve_adblock_prefs"
        private const val KEY_WHITELISTED_DOMAINS = "whitelisted_domains"
        private const val KEY_LAST_UPDATED_MS = "last_updated_ms"
        private const val KEY_ADBLOCK_MODE = "adblock_mode"
        private const val AGGRESSIVE_THIRD_PARTY_SOURCE_URL = "https://verve-aggressive-context.invalid/"

        private const val DIR_ADBLOCK = "adblock"
        private const val FILE_ENGINE_DAT = "engine_v1.dat"
        private const val FILE_ENGINE_MANIFEST = "engine_manifest.json"
        private const val FILE_RESOURCES_JSON = "resources.json"
        private const val ASSET_DIR_ADBLOCK = "adblock"
        private const val ENGINE_FORMAT_VERSION = 1

        private val CURATED_FILTER_SOURCES = listOf(
            "easylist.txt" to "https://easylist.to/easylist/easylist.txt",
            "easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt",
            "ublock-filters.txt" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
            "ublock-privacy.txt" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
            "ublock-quick-fixes.txt" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/quick-fixes.txt",
            "ublock-unbreak.txt" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt",
            "brave-unbreak.txt" to "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-unbreak.txt",
            "brave-firstparty.txt" to "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-firstparty.txt",
            "brave-firstparty-cname.txt" to "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-firstparty-cname.txt"
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

    private val _adBlockMode = MutableStateFlow(
        AdBlockMode.fromStorage(
            runCatching {
                prefs.getString(KEY_ADBLOCK_MODE, AdBlockMode.STANDARD.name)
            }.getOrNull()
        )
    )
    override val adBlockMode: StateFlow<AdBlockMode> = _adBlockMode.asStateFlow()

    override fun setAdBlockMode(mode: AdBlockMode) {
        if (_adBlockMode.value == mode) return
        _adBlockMode.value = mode
        prefs.edit().putString(KEY_ADBLOCK_MODE, mode.name).apply()
    }

    @Volatile
    private var isFullEngineLoaded = false
    private val fullEngineReadyLatch = CountDownLatch(1)
    private val hasWarmEngineManifest = runCatching {
        File(File(appContext.filesDir, DIR_ADBLOCK), FILE_ENGINE_MANIFEST).exists()
    }.getOrDefault(false)
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
                val manifestFile = File(adblockDir, FILE_ENGINE_MANIFEST)
                val cachedResourcesFile = File(adblockDir, FILE_RESOURCES_JSON)
                val resourcesJson = readBestResourcesJson(cachedResourcesFile)
                val localRules = readAllLocalRules(adblockDir)
                val expectedHash = if (localRules.isNotBlank()) {
                    computeEngineContentHash(localRules, resourcesJson)
                } else {
                    ""
                }

                // 1. Fast path: restore pre-compiled binary .dat snapshot ONLY if manifest hash & size match
                if (datFile.exists() &&
                    datFile.length() > 0L &&
                    expectedHash.isNotEmpty() &&
                    isManifestValid(manifestFile, datFile, expectedHash)
                ) {
                    val loaded = runCatching {
                        nativeBlocker.safeLoadSerializedEngine(datFile.readBytes(), resourcesJson)
                    }.getOrDefault(false)

                    if (loaded) {
                        isFullEngineLoaded = true
                        Log.i(TAG, "Restored pre-compiled adblock engine (${datFile.length() / 1024} KB)")
                        return@withLock
                    }
                }

                if (datFile.exists()) datFile.delete()
                if (manifestFile.exists()) manifestFile.delete()

                // 2. Local assets / cached .txt path
                if (localRules.isNotBlank() && resourcesJson.isNotBlank()) {
                    if (compileAndPersistLocked(adblockDir, localRules, resourcesJson, expectedHash)) {
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

    private fun awaitFullEngineReadyIfBackground(
        timeoutMs: Long = if (hasWarmEngineManifest) 1_500L else 4_000L
    ) {
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
        val contentHash = computeEngineContentHash(combinedRules, resourcesJson)
        compileAndPersistLocked(adblockDir, combinedRules, resourcesJson, contentHash)
    }

    private fun compileAndPersistLocked(
        adblockDir: File,
        combinedRules: String,
        resourcesJson: String,
        contentHash: String
    ): Boolean {
        val serialized = nativeBlocker.safeInitEngineFromLists(combinedRules, resourcesJson)
        isFullEngineLoaded = true
        if (serialized != null && serialized.isNotEmpty()) {
            runCatching {
                val datFile = File(adblockDir, FILE_ENGINE_DAT)
                val tmpFile = File(adblockDir, "$FILE_ENGINE_DAT.tmp")
                tmpFile.writeBytes(serialized)
                if (!tmpFile.renameTo(datFile)) {
                    datFile.writeBytes(serialized)
                    tmpFile.delete()
                }

                val manifestFile = File(adblockDir, FILE_ENGINE_MANIFEST)
                val manifestTmp = File(adblockDir, "$FILE_ENGINE_MANIFEST.tmp")
                val manifestJson =
                    """{"engineFormatVersion":$ENGINE_FORMAT_VERSION,"contentSha256":"$contentHash","datSizeBytes":${serialized.size.toLong()}}"""
                manifestTmp.writeText(manifestJson)
                if (!manifestTmp.renameTo(manifestFile)) {
                    manifestFile.writeText(manifestJson)
                    manifestTmp.delete()
                }
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

    private fun isManifestValid(manifestFile: File, datFile: File, expectedHash: String): Boolean {
        if (!manifestFile.exists() || manifestFile.length() == 0L) return false
        return runCatching {
            val raw = manifestFile.readText()
            val version = Regex(""""engineFormatVersion"\s*:\s*(\d+)""")
                .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
            val hash = Regex(""""contentSha256"\s*:\s*"([0-9a-fA-F]+)""")
                .find(raw)?.groupValues?.getOrNull(1).orEmpty()
            val sizeBytes = Regex(""""datSizeBytes"\s*:\s*(\d+)""")
                .find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: -1L
            version == ENGINE_FORMAT_VERSION &&
                    hash == expectedHash &&
                    sizeBytes == datFile.length()
        }.getOrDefault(false)
    }

    private fun computeEngineContentHash(rules: String, resourcesJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(rules.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(resourcesJson.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun shouldBlockUrl(
        targetUrl: String,
        pageUrl: String,
        resourceType: String,
        method: String
    ): UrlCheckResult {
        if (!nativeBlocker.isLibraryLoaded) return UrlCheckResult.Allow
        awaitFullEngineReadyIfBackground()
        val effectiveSource = pageUrl.ifBlank { targetUrl }
        val primaryResult = evaluateNativeRequest(
            targetUrl = targetUrl,
            sourceUrl = effectiveSource,
            resourceType = resourceType,
            method = method
        )
        if (primaryResult != UrlCheckResult.Allow) {
            return primaryResult
        }

        val isSubresource = pageUrl.isNotBlank() &&
                !resourceType.equals("main_frame", ignoreCase = true)

        if (isSubresource &&
            _adBlockMode.value == AdBlockMode.AGGRESSIVE &&
            !effectiveSource.equals(AGGRESSIVE_THIRD_PARTY_SOURCE_URL, ignoreCase = true)
        ) {
            val aggressiveResult = evaluateNativeRequest(
                targetUrl = targetUrl,
                sourceUrl = AGGRESSIVE_THIRD_PARTY_SOURCE_URL,
                resourceType = resourceType,
                method = method
            )
            if (aggressiveResult != UrlCheckResult.Allow) {
                return aggressiveResult
            }
        }

        if (isSubresource) {
            val cnameResult = evaluateUncloakedCnames(
                targetUrl = targetUrl,
                pageUrl = pageUrl,
                effectiveSource = effectiveSource,
                resourceType = resourceType,
                method = method
            )
            if (cnameResult != UrlCheckResult.Allow) {
                return cnameResult
            }
        }

        return UrlCheckResult.Allow
    }

    private fun evaluateUncloakedCnames(
        targetUrl: String,
        pageUrl: String,
        effectiveSource: String,
        resourceType: String,
        method: String
    ): UrlCheckResult {
        val targetHost = extractRawHost(targetUrl) ?: return UrlCheckResult.Allow
        val pageHost = extractRawHost(pageUrl) ?: return UrlCheckResult.Allow
        if (targetHost.equals(pageHost, ignoreCase = true)) return UrlCheckResult.Allow

        val cnameChain = cnameResolver.resolveCnameChain(targetHost)
        if (cnameChain.isEmpty()) return UrlCheckResult.Allow

        for (canonicalHost in cnameChain) {
            if (canonicalHost.equals(targetHost, ignoreCase = true)) continue
            val uncloakedUrl = replaceUrlHost(targetUrl, canonicalHost) ?: continue

            val primaryCnameCheck = evaluateNativeRequest(
                targetUrl = uncloakedUrl,
                sourceUrl = effectiveSource,
                resourceType = resourceType,
                method = method
            )
            if (primaryCnameCheck is UrlCheckResult.Block || primaryCnameCheck is UrlCheckResult.Redirect) {
                return primaryCnameCheck
            }

            if (_adBlockMode.value == AdBlockMode.AGGRESSIVE &&
                !effectiveSource.equals(AGGRESSIVE_THIRD_PARTY_SOURCE_URL, ignoreCase = true)
            ) {
                val aggressiveCnameCheck = evaluateNativeRequest(
                    targetUrl = uncloakedUrl,
                    sourceUrl = AGGRESSIVE_THIRD_PARTY_SOURCE_URL,
                    resourceType = resourceType,
                    method = method
                )
                if (aggressiveCnameCheck is UrlCheckResult.Block ||
                    aggressiveCnameCheck is UrlCheckResult.Redirect
                ) {
                    return aggressiveCnameCheck
                }
            }
        }
        return UrlCheckResult.Allow
    }

    private fun extractRawHost(url: String): String? {
        if (url.isBlank()) return null
        val host = runCatching { java.net.URI(url).host }.getOrNull()
            ?: runCatching { url.toUri().host }.getOrNull()
        return host?.lowercase()?.takeIf { it.isNotBlank() }
    }

    internal fun replaceUrlHost(targetUrl: String, newHost: String): String? {
        val schemeEnd = targetUrl.indexOf("://")
        if (schemeEnd < 0) return null
        val authorityStart = schemeEnd + 3
        var authorityEnd = targetUrl.length
        for (i in authorityStart until targetUrl.length) {
            val ch = targetUrl[i]
            if (ch == '/' || ch == '?' || ch == '#') {
                authorityEnd = i
                break
            }
        }
        val authority = targetUrl.substring(authorityStart, authorityEnd)
        val atIndex = authority.lastIndexOf('@')
        val hostStartInAuth = if (atIndex >= 0) atIndex + 1 else 0
        val colonIndex = authority.indexOf(':', hostStartInAuth)
        val hostEndInAuth = if (colonIndex >= 0) colonIndex else authority.length
        if (hostStartInAuth >= hostEndInAuth) return null

        val updatedAuthority = buildString {
            append(authority, 0, hostStartInAuth)
            append(newHost)
            append(authority, hostEndInAuth, authority.length)
        }
        return targetUrl.substring(0, authorityStart) +
                updatedAuthority +
                targetUrl.substring(authorityEnd)
    }

    private fun evaluateNativeRequest(
        targetUrl: String,
        sourceUrl: String,
        resourceType: String,
        method: String
    ): UrlCheckResult {
        val evaluation = nativeBlocker.safeEvaluateRequest(
            url = targetUrl,
            sourceUrl = sourceUrl,
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
        val parsedGenericHide = Regex(""""generichide"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
            .find(rawJson)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true) ?: false
        val effectiveGenericHide =
            if (_adBlockMode.value == AdBlockMode.AGGRESSIVE) false else parsedGenericHide

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
                genericHide = effectiveGenericHide
            )
        }.getOrDefault(
            CosmeticResources(
                hideSelectors = extractJsonStringArray(rawJson, "hideSelectors"),
                exceptions = extractJsonStringArray(rawJson, "exceptions"),
                injectedScript = "",
                genericHide = effectiveGenericHide
            )
        )
    }

    private fun extractJsonStringArray(rawJson: String, key: String): List<String> {
        val arrayBody = Regex(""""$key"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(rawJson)?.groupValues?.getOrNull(1) ?: return emptyList()
        return Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(arrayBody)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toList()
    }

    override fun isSiteWhitelisted(pageUrl: String): Boolean {
        val host = extractNormalizedHost(pageUrl) ?: return false
        var candidate = host
        while (candidate.contains('.')) {
            if (whitelistedHosts.contains(candidate)) {
                return true
            }
            val nextDot = candidate.indexOf('.')
            if (nextDot < 0 || nextDot + 1 >= candidate.length) break
            candidate = candidate.substring(nextDot + 1)
        }
        return false
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
        val host = runCatching { java.net.URI(url).host }.getOrNull()
            ?: runCatching { url.toUri().host }.getOrNull()
        return host?.lowercase()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
    }
}