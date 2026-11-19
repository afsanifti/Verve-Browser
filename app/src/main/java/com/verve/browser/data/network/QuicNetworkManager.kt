package com.verve.browser.data.network

import android.content.Context
import android.webkit.WebView
import androidx.core.content.edit
import com.google.android.gms.net.CronetProviderInstaller
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine

object QuicNetworkManager {

    private const val PREFS_NAME = "verve_network_prefs"
    private const val KEY_QUIC_ENABLED = "quic_http3_enabled"

    private val _isQuicEnabled = MutableStateFlow(true)
    val isQuicEnabled: StateFlow<Boolean> = _isQuicEnabled.asStateFlow()

    @Volatile
    private var cronetEngine: CronetEngine? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isQuicEnabled.value = prefs.getBoolean(KEY_QUIC_ENABLED, true)

        CronetProviderInstaller.installProvider(appContext)
            .addOnCompleteListener {
                rebuildCronetEngine(appContext, _isQuicEnabled.value)
            }
    }

    fun setQuicEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_QUIC_ENABLED, enabled)
        }
        _isQuicEnabled.value = enabled
        rebuildCronetEngine(appContext, enabled)
    }

    /**
     * Primes a QUIC / HTTP/3 session over UDP via Cronet for HTTPS origins so
     * subsequent downloads and subresource requests benefit from 0-RTT / 1-RTT.
     */
    suspend fun warmUpQuicSession(context: Context, url: String) {
        if (!_isQuicEnabled.value) return
        if (!url.startsWith("https://", ignoreCase = true)) return

        val engine = getCronetEngine(context) ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (engine.openConnection(URL(url)) as? HttpURLConnection)
                    ?: return@runCatching
                try {
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = 4_000
                    connection.readTimeout = 4_000
                    connection.connect()
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    /**
     * Queries Chromium's Navigation Timing API to inspect whether the active page
     * loaded over HTTP/3 ("h3"), HTTP/2 ("h2"), or HTTP/1.1.
     */
    fun detectActiveProtocol(
        webView: WebView,
        onResult: (protocol: String, isQuic: Boolean) -> Unit
    ) {
        val script = """
            (function() {
                var nav = performance.getEntriesByType('navigation')[0];
                return nav && nav.nextHopProtocol ? nav.nextHopProtocol : 'unknown';
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            val protocol = raw?.trim('"')?.lowercase().orEmpty().ifBlank { "unknown" }
            val isQuic = protocol.startsWith("h3") || protocol.contains("quic")
            onResult(protocol, isQuic)
        }
    }

    fun getCronetEngine(context: Context): CronetEngine? {
        return cronetEngine ?: rebuildCronetEngine(
            context.applicationContext,
            _isQuicEnabled.value
        )
    }

    private fun rebuildCronetEngine(
        appContext: Context,
        enableQuic: Boolean
    ): CronetEngine? {
        return runCatching {
            cronetEngine?.shutdown()
            CronetEngine.Builder(appContext)
                .enableQuic(enableQuic)
                .enableHttp2(true)
                .enableBrotli(true)
                .apply {
                    if (enableQuic) {
                        addQuicHint("www.google.com", 443, 443)
                        addQuicHint("github.com", 443, 443)
                        addQuicHint("en.wikipedia.org", 443, 443)
                    }
                }
                .build()
                .also { cronetEngine = it }
        }.getOrNull()
    }
}