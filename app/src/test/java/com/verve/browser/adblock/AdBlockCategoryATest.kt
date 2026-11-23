package com.verve.browser.adblock

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.data.repository.AdBlockRepositoryImpl
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.repository.AdBlockRepository
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.webview.AdBlockWebViewHelper
import com.verve.browser.presentation.browser.components.webview.VerveWebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import org.junit.After
import org.junit.Before
import org.junit.Test

class AdBlockCategoryATest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var mockAssets: AssetManager
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var mockNativeBlocker: NativeAdBlocker

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        tempDir = Files.createTempDirectory("verve_cat_a_test").toFile()
        mockAssets = mockk(relaxed = true)
        mockPrefsEditor = mockk(relaxed = true) {
            every { putStringSet(any(), any()) } returns this
            every { putLong(any(), any()) } returns this
        }
        mockPrefs = mockk(relaxed = true) {
            every { getStringSet(any(), any()) } returns emptySet()
            every { edit() } returns mockPrefsEditor
        }
        mockContext = mockk(relaxed = true) {
            every { applicationContext } returns this
            every { filesDir } returns tempDir
            every { assets } returns mockAssets
            every { getSharedPreferences(any(), any()) } returns mockPrefs
        }
        mockNativeBlocker = mockk(relaxed = true) {
            every { isLibraryLoaded } returns true
            every { supportsV2Engine } returns true
        }
    }

    @After
    fun tearDown() {
        AdBlockWebViewHelper.clearTab("tab-1")
        AdBlockWebViewHelper.clearTab("tab-2")
        AdBlockWebViewHelper.clearTab("tab-rewrite")
        unmockkStatic(Log::class)
        tempDir.deleteRecursively()
    }

    @Test
    fun `A1 - ServiceWorker attribution resolves active foreground tab instead of arbitrary map entry`() {
        AdBlockWebViewHelper.updateActivePageUrl("tab-1", "https://news.example.com/frontpage")
        AdBlockWebViewHelper.updateActivePageUrl("tab-2", "https://shop.example.com/checkout")

        val viewModel = mockk<BrowserViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                BrowserUiState(
                    activeTabId = "tab-2",
                    currentUrl = "https://shop.example.com/checkout"
                )
            )
        }

        // Case 1: No Referer/Origin header -> must attribute to active foreground tab (tab-2), not tab-1
        val resolvedFromActiveTab = AdBlockWebViewHelper.resolveServiceWorkerPageUrl(
            headers = emptyMap<String, String>(),
            viewModel = viewModel,
            targetUrl = "https://metrics.tracker.net/sw-ping"
        )
        assertThat(resolvedFromActiveTab).isEqualTo("https://shop.example.com/checkout")

        // Case 2: Origin header matches an open tab -> must promote to that tab's full URL
        val resolvedFromOrigin = AdBlockWebViewHelper.resolveServiceWorkerPageUrl(
            headers = mapOf("origin" to "https://news.example.com"),
            viewModel = viewModel,
            targetUrl = "https://metrics.tracker.net/sw-ping"
        )
        assertThat(resolvedFromOrigin).isEqualTo("https://news.example.com/frontpage")
    }

    @Test
    fun `A2 - scriptlet injection uses nonce-aware script tag and deduplicates execution under CSP`() {
        val method = AdBlockWebViewHelper::class.java.getDeclaredMethod("buildDocumentStartBootstrapScript")
        method.isAccessible = true
        val script = method.invoke(AdBlockWebViewHelper) as String

        assertThat(script).contains("querySelector('script[nonce]')")
        assertThat(script).contains("__verveScriptletExecuted")
    }

    @Test
    fun `A3 - main-frame tracking parameters are rewritten on navigation and subresource rewrites preserve headers`() {
        val dirtyUrl = "https://example.com/article?id=42&fbclid=IwAR0xyz&utm_source=social"
        val cleanUrl = "https://example.com/article?id=42"

        val mockRepo = mockk<AdBlockRepository>(relaxed = true) {
            every { isSiteWhitelisted(any()) } returns false
            every {
                shouldBlockUrl(dirtyUrl, any(), "main_frame", any())
            } returns UrlCheckResult.RewriteUrl(cleanUrl)
        }
        val useCase = ShouldBlockUrlUseCase(mockRepo)

        // 1. UseCase must allow RewriteUrl on main_frame rather than returning Allow
        val mainFrameDecision = useCase(dirtyUrl, dirtyUrl, "main_frame", "GET")
        assertThat(mainFrameDecision).isEqualTo(UrlCheckResult.RewriteUrl(cleanUrl))

        // 2. VerveWebViewClient.shouldOverrideUrlLoading must intercept dirty main-frame URL and load cleaned URL
        val viewModel = mockk<BrowserViewModel>(relaxed = true) {
            every {
                evaluateResourceRequest(dirtyUrl, any(), "main_frame", any())
            } returns UrlCheckResult.RewriteUrl(cleanUrl)
        }
        val mockWebView = mockk<WebView>(relaxed = true)
        val client = VerveWebViewClient(
            tabId = "tab-rewrite",
            viewModel = viewModel,
            tabWebViewPool = mockk(relaxed = true),
            pageColorCache = mockk(relaxed = true),
            edgeProbeScheduler = mockk(relaxed = true),
            bottomCoordinator = mockk(relaxed = true),
            scheduleThumbnailCapture = {},
            scheduleSnapshotColorExtract = {},
            scheduleSilentSnapshotColorExtract = {},
            onNavigatedUrl = {},
            onWebViewDestroyed = {}
        )
        val parsedUri = mockk<Uri>(relaxed = true) {
            every { scheme } returns "https"
            every { host } returns "example.com"
            every { this@mockk.toString() } returns dirtyUrl
        }
        val navRequest = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns true
            every { hasGesture() } returns true
            every { method } returns "GET"
            every { url } returns parsedUri
        }

        val overridden = client.shouldOverrideUrlLoading(mockWebView, navRequest)
        assertThat(overridden).isTrue()
        verify { mockWebView.loadUrl(cleanUrl) }

        // 3. Rewritten subresource requests must preserve HTTP response headers (CORS, Cache-Control) and body
        val serverSocket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val serverThread = Thread {
            while (!serverSocket.isClosed) {
                runCatching {
                    serverSocket.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = "window.cleanLoaded = true;".toByteArray(Charsets.UTF_8)
                        val responseHead = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: application/javascript; charset=utf-8\r\n")
                            append("Cache-Control: max-age=600\r\n")
                            append("Access-Control-Allow-Origin: *\r\n")
                            append("Content-Length: ${body.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().use { out ->
                            out.write(responseHead)
                            out.write(body)
                            out.flush()
                        }
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        try {
            val cleanSubUrl = "http://127.0.0.1:${serverSocket.localPort}/clean.js"
            val dirtySubUrl = "$cleanSubUrl?utm_campaign=test"

            val subViewModel = mockk<BrowserViewModel>(relaxed = true) {
                every {
                    evaluateResourceRequest(dirtySubUrl, "https://example.com/", "script", "GET")
                } returns UrlCheckResult.RewriteUrl(cleanSubUrl)
            }
            val subReq = mockk<WebResourceRequest>(relaxed = true) {
                every { method } returns "GET"
                every { requestHeaders } returns mapOf("Sec-Fetch-Dest" to "script")
            }

            val spec = AdBlockWebViewHelper.fetchRewrittenResponseSpec(subReq, cleanSubUrl)
            assertThat(spec).isNotNull()
            assertThat(spec!!.statusCode).isEqualTo(200)
            assertThat(spec.mimeType).isEqualTo("application/javascript")
            assertThat(spec.headers["Access-Control-Allow-Origin"]).isEqualTo("*")
            assertThat(spec.headers["Cache-Control"]).isEqualTo("max-age=600")
            assertThat(spec.bodyStream.bufferedReader().readText()).isEqualTo("window.cleanLoaded = true;")

            val response = AdBlockWebViewHelper.interceptNetworkRequest(
                viewModel = subViewModel,
                request = subReq,
                targetUrl = dirtySubUrl,
                pageUrl = "https://example.com/"
            )
            assertThat(response).isNotNull()
        } finally {
            serverSocket.close()
            serverThread.join(500)
        }
    }

    @Test
    fun `A4 - serialized engine cache invalidates stale DAT when filter rules or resources change`() = runBlocking {
        val adblockDir = File(tempDir, "adblock").apply { mkdirs() }
        val staleDatFile = File(adblockDir, "engine_v1.dat").apply {
            writeBytes(byteArrayOf(0x09, 0x09, 0x09, 0x09))
        }

        var currentRules = "||ads.example.com^\n"
        val resourcesContent = """{"redirects":[]}"""
        every { mockAssets.open(any()) } answers {
            val path = firstArg<String>()
            val content = if (path.endsWith("resources.json")) resourcesContent else currentRules
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }
        every { mockNativeBlocker.safeInitEngineFromLists(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every { mockNativeBlocker.safeLoadSerializedEngine(any(), any()) } returns true

        // 1. Stale DAT without matching manifest must NOT be loaded; must recompile and write manifest
        val repo1 = AdBlockRepositoryImpl(context = mockContext, nativeBlocker = mockNativeBlocker)
        repo1.initializeEngineAsync()

        verify(exactly = 0) { mockNativeBlocker.safeLoadSerializedEngine(any(), any()) }
        verify(exactly = 1) { mockNativeBlocker.safeInitEngineFromLists(any(), any()) }
        val manifestFile = File(adblockDir, "engine_manifest.json")
        assertThat(manifestFile.exists()).isTrue()
        assertThat(staleDatFile.readBytes()).isEqualTo(byteArrayOf(0x01, 0x02, 0x03))

        // 2. Unchanged rules on next startup MUST hit serialized DAT fast path
        val repo2 = AdBlockRepositoryImpl(context = mockContext, nativeBlocker = mockNativeBlocker)
        repo2.initializeEngineAsync()
        verify(exactly = 1) { mockNativeBlocker.safeLoadSerializedEngine(any(), any()) }

        // 3. Updated bundled rules MUST invalidate manifest and recompile
        currentRules = "||ads.example.com^\n||new-tracker.example.com^\n"
        val repo3 = AdBlockRepositoryImpl(context = mockContext, nativeBlocker = mockNativeBlocker)
        repo3.initializeEngineAsync()
        verify(exactly = 2) { mockNativeBlocker.safeInitEngineFromLists(any(), any()) }
    }
}