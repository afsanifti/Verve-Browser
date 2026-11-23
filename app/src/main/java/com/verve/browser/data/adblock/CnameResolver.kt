package com.verve.browser.data.adblock

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class CnameResolver(
    private val lookupClient: CnameLookupClient = DohCnameLookupClient(),
    private val maxCacheEntries: Int = 1024,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    fun interface CnameLookupClient {
        fun queryCnameChain(host: String): CnameQueryResult
    }

    data class CnameQueryResult(
        val cnameChain: List<String>,
        val ttlSeconds: Long,
        val isError: Boolean = false
    )

    private data class CacheEntry(
        val cnameChain: List<String>,
        val expiresAtMs: Long
    )

    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, CacheEntry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > maxCacheEntries
        }
    }
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<List<String>>>()

    fun resolveCnameChain(rawHost: String): List<String> {
        val host = rawHost.trim().trimEnd('.').lowercase()
        if (!isEligibleHost(host)) return emptyList()

        val now = nowMs()
        synchronized(cacheLock) {
            val cached = cache[host]
            if (cached != null) {
                if (cached.expiresAtMs > now) {
                    return cached.cnameChain
                }
                cache.remove(host)
            }
        }

        val createdFuture = CompletableFuture<List<String>>()
        val existingFuture = inFlight.putIfAbsent(host, createdFuture)
        if (existingFuture != null) {
            return runCatching { existingFuture.get(1_500L, TimeUnit.MILLISECONDS) }
                .getOrDefault(emptyList())
        }

        return try {
            val queryResult = runCatching { lookupClient.queryCnameChain(host) }
                .getOrElse { CnameQueryResult(emptyList(), ttlSeconds = 60L, isError = true) }

            val normalizedChain = queryResult.cnameChain
                .map { it.trim().trimEnd('.').lowercase() }
                .filter { it.isNotBlank() && it != host && isEligibleHost(it) }
                .distinct()

            val boundedTtlMs = when {
                queryResult.isError -> 60_000L
                normalizedChain.isEmpty() -> queryResult.ttlSeconds.coerceIn(60L, 300L) * 1_000L
                else -> queryResult.ttlSeconds.coerceIn(60L, 900L) * 1_000L
            }

            synchronized(cacheLock) {
                cache[host] = CacheEntry(
                    cnameChain = normalizedChain,
                    expiresAtMs = nowMs() + boundedTtlMs
                )
            }
            createdFuture.complete(normalizedChain)
            normalizedChain
        } catch (t: Throwable) {
            createdFuture.complete(emptyList())
            emptyList()
        } finally {
            inFlight.remove(host, createdFuture)
        }
    }

    fun clearCache() {
        synchronized(cacheLock) {
            cache.clear()
        }
        inFlight.clear()
    }

    internal fun isEligibleHost(host: String): Boolean {
        if (host.isBlank() || !host.contains('.')) return false
        if (host.contains(':') || IPV4_REGEX.matches(host)) return false
        return !host.endsWith(".local") &&
                !host.endsWith(".invalid") &&
                !host.endsWith(".internal") &&
                !host.endsWith(".onion")
    }

    private class DohCnameLookupClient : CnameLookupClient {
        override fun queryCnameChain(host: String): CnameQueryResult {
            val encoded = URLEncoder.encode(host, "UTF-8")
            val endpoints = listOf(
                "https://cloudflare-dns.com/dns-query?name=$encoded&type=CNAME",
                "https://dns.google/resolve?name=$encoded&type=CNAME"
            )

            for (endpoint in endpoints) {
                val parsed = fetchAndParseDoh(endpoint)
                if (parsed != null) return parsed
            }
            return CnameQueryResult(emptyList(), ttlSeconds = 60L, isError = true)
        }

        private fun fetchAndParseDoh(endpointUrl: String): CnameQueryResult? {
            return runCatching {
                val conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1_200
                    readTimeout = 1_200
                    setRequestProperty("Accept", "application/dns-json")
                    setRequestProperty("User-Agent", "VerveBrowser-AdBlock/1.0")
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parseDohJson(body)
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
    }

    companion object {
        private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val ANSWER_BLOCK_REGEX = Regex(""""Answer"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        private val OBJECT_REGEX = Regex("""\{[^{}]*\}""")
        private val TYPE_CNAME_REGEX = Regex(""""type"\s*:\s*5\b""")
        private val DATA_REGEX = Regex(""""data"\s*:\s*"([^"]+)"""")
        private val TTL_REGEX = Regex(""""TTL"\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)

        internal fun parseDohJson(rawJson: String): CnameQueryResult {
            val parsedWithOrgJson = runCatching {
                val root = JSONObject(rawJson)
                val answers = root.optJSONArray("Answer")
                    ?: return@runCatching CnameQueryResult(emptyList(), ttlSeconds = 300L)
                val targets = mutableListOf<String>()
                var minTtl = Long.MAX_VALUE
                for (i in 0 until answers.length()) {
                    val entry = answers.optJSONObject(i) ?: continue
                    if (entry.optInt("type", -1) != 5) continue
                    val data = entry.optString("data", "")
                        .trim()
                        .trimEnd('.')
                        .lowercase()
                    if (data.isNotBlank()) {
                        targets.add(data)
                        val ttl = entry.optLong("TTL", 300L)
                        if (ttl in 1 until minTtl) minTtl = ttl
                    }
                }
                CnameQueryResult(
                    cnameChain = targets,
                    ttlSeconds = if (minTtl == Long.MAX_VALUE) 300L else minTtl
                )
            }.getOrNull()

            if (parsedWithOrgJson != null) {
                return parsedWithOrgJson
            }

            val answerArray = ANSWER_BLOCK_REGEX.find(rawJson)?.groupValues?.getOrNull(1)
                ?: return CnameQueryResult(emptyList(), ttlSeconds = 300L)

            val targets = mutableListOf<String>()
            var minTtl = Long.MAX_VALUE

            for (objMatch in OBJECT_REGEX.findAll(answerArray)) {
                val obj = objMatch.value
                if (!TYPE_CNAME_REGEX.containsMatchIn(obj)) continue
                val data = DATA_REGEX.find(obj)?.groupValues?.getOrNull(1)
                    ?.trim()
                    ?.trimEnd('.')
                    ?.lowercase()
                if (!data.isNullOrBlank()) {
                    targets.add(data)
                    val ttl = TTL_REGEX.find(obj)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 300L
                    if (ttl < minTtl) minTtl = ttl
                }
            }

            return CnameQueryResult(
                cnameChain = targets,
                ttlSeconds = if (minTtl == Long.MAX_VALUE) 300L else minTtl
            )
        }
    }
}