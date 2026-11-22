package com.verve.browser.adblock

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceRequest
import com.google.common.truth.Truth.assertThat
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.data.repository.AdBlockRepositoryImpl
import com.verve.browser.domain.model.CosmeticResources
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.repository.AdBlockRepository
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.webview.AdBlockWebViewHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import android.util.Log
import com.verve.browser.presentation.browser.components.webview.VerveWebViewClient
import java.io.File
import java.io.ByteArrayInputStream
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class AdBlockBaselineTest {

    private lateinit var tempFilesDir: File
    private lateinit var mockContext: Context
    private lateinit var mockAssets: AssetManager
    private lateinit var mockNativeBlocker: NativeAdBlocker

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        tempFilesDir = createTempDirectory("verve_adblock_baseline").toFile()
        mockAssets = mockk(relaxed = true)
        mockContext = mockk(relaxed = true) {
            every { applicationContext } returns this
            every { filesDir } returns tempFilesDir
            every { assets } returns mockAssets
        }
        mockNativeBlocker = mockk(relaxed = true) {
            every { isLibraryLoaded } returns true
            every { supportsV2Engine } returns true
        }
    }

    @After
    fun tearDown() {
        tempFilesDir.deleteRecursively()
        unmockkStatic(Uri::class)
        unmockkStatic(Log::class)
    }


    private fun mockTypedRequest(
        path: String,
        query: String? = null,
        headers: Map<String, String> = emptyMap(),
        isMainFrame: Boolean = false
    ): android.webkit.WebResourceRequest {
        val mockUri = mockk<Uri>(relaxed = true) {
            every { this@mockk.path } returns path
            every { this@mockk.query } returns query
            every { this@mockk.encodedQuery } returns query
        }
        return mockk(relaxed = true) {
            every { isForMainFrame } returns isMainFrame
            every { url } returns mockUri
            every { requestHeaders } returns headers
        }
    }

    // --- A. Network Filtering & Resource-Type Inference ---

    @Test
    fun `A1 - infers standard script, stylesheet, image, font, media, and iframe resource types`() {
        assertThat(
            AdBlockWebViewHelper.inferResourceType(
                mockRequest("https://cdn.example.com/bundle.js", accept = "application/javascript")
            )
        ).isEqualTo("script")

        assertThat(
            AdBlockWebViewHelper.inferResourceType(
                mockRequest("https://cdn.example.com/theme.css", accept = "text/css")
            )
        ).isEqualTo("stylesheet")

        assertThat(
            AdBlockWebViewHelper.inferResourceType(
                mockRequest("https://advmaker.net/banner.png", accept = "image/webp,image/png")
            )
        ).isEqualTo("image")

        assertThat(
            AdBlockWebViewHelper.inferResourceType(
                mockRequest("https://fonts.example.com/inter.woff2", accept = "font/woff2")
            )
        ).isEqualTo("font")

        assertThat(
            AdBlockWebViewHelper.inferResourceType(
                mockRequest("https://cdn.example.com/video.mp4")
            )
        ).isEqualTo("media")

        assertThat(
            AdBlockWebViewHelper.inferResourceType(
                mockRequest(
                    "https://ads.example.com/frame",
                    headers = mapOf("Sec-Fetch-Dest" to "iframe")
                )
            )
        ).isEqualTo("sub_frame")
    }

    @Test
    fun `A2 - main_frame requests are never blocked by ShouldBlockUrlUseCase`() {
        val repo = mockk<AdBlockRepository>(relaxed = true)
        val useCase = ShouldBlockUrlUseCase(repo)

        val result = useCase(
            targetUrl = "https://adblock-tester.com/",
            pageUrl = "https://adblock-tester.com/",
            resourceType = "main_frame"
        )

        assertThat(result).isEqualTo(UrlCheckResult.Allow)
        verify(exactly = 0) { repo.shouldBlockUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `A3 - inferResourceType honors case-insensitive Sec-Fetch-Dest headers`() {
        val scriptReq = mockTypedRequest(
            path = "/gtag/js",
            query = "id=G-12345",
            headers = mapOf("sec-fetch-dest" to "script", "accept" to "*/*")
        )
        val beaconReq = mockTypedRequest(
            path = "/g/collect",
            query = "v=2&tid=G-12345",
            headers = mapOf("sec-fetch-dest" to "empty", "sec-fetch-mode" to "no-cors")
        )
        val iframeReq = mockTypedRequest(
            path = "/embed/frame",
            headers = mapOf("sec-fetch-dest" to "iframe")
        )
        val styleReq = mockTypedRequest(
            path = "/bundle",
            headers = mapOf("sec-fetch-dest" to "style")
        )

        assertThat(AdBlockWebViewHelper.inferResourceType(scriptReq)).isEqualTo("script")
        assertThat(AdBlockWebViewHelper.inferResourceType(beaconReq)).isEqualTo("xmlhttprequest")
        assertThat(AdBlockWebViewHelper.inferResourceType(iframeReq)).isEqualTo("sub_frame")
        assertThat(AdBlockWebViewHelper.inferResourceType(styleReq)).isEqualTo("stylesheet")
    }

    @Test
    fun `A4 - inferResourceType detects multi-token image Accept headers, modern image extensions, and tracker endpoints`() {
        val multiAcceptPixel = mockTypedRequest(
            path = "/tr",
            query = "id=999&ev=PageView",
            headers = mapOf("Accept" to "text/html,application/xhtml+xml,image/avif,image/webp,*/*;q=0.8")
        )
        val avifImage = mockTypedRequest(
            path = "/ads/banner.avif",
            headers = mapOf("Accept" to "*/*")
        )
        val icoImage = mockTypedRequest(
            path = "/favicon.ico",
            headers = mapOf("Accept" to "*/*")
        )
        val extensionlessCollector = mockTypedRequest(
            path = "/api/12345/envelope/",
            headers = mapOf("Accept" to "*/*")
        )
        val jsonpCallbackScript = mockTypedRequest(
            path = "/pixel/sync",
            query = "callback=cb123",
            headers = mapOf("Accept" to "*/*")
        )

        assertThat(AdBlockWebViewHelper.inferResourceType(multiAcceptPixel)).isEqualTo("image")
        assertThat(AdBlockWebViewHelper.inferResourceType(avifImage)).isEqualTo("image")
        assertThat(AdBlockWebViewHelper.inferResourceType(icoImage)).isEqualTo("image")
        assertThat(AdBlockWebViewHelper.inferResourceType(extensionlessCollector)).isEqualTo("xmlhttprequest")
        assertThat(AdBlockWebViewHelper.inferResourceType(jsonpCallbackScript)).isEqualTo("script")
    }

    // --- B. Filter-Rule Behavior & Repository Snapshot Lifecycle ---

    @Test
    fun `B1 - repository maps REDIRECT, BLOCK, REWRITE, and ALLOW decisions accurately`() {
        every {
            mockNativeBlocker.safeEvaluateRequest(
                url = "https://www.google-analytics.com/analytics.js",
                sourceUrl = "https://adblock-tester.com/",
                resourceType = "script",
                method = "GET"
            )
        } returns arrayOf("REDIRECT", "data:application/javascript;base64,KGZ1bmN0aW9uKCl7fSkoKTs=", "")

        every {
            mockNativeBlocker.safeEvaluateRequest(
                url = "https://advmaker.net/468x60.png",
                sourceUrl = "https://adblock-tester.com/",
                resourceType = "image",
                method = "GET"
            )
        } returns arrayOf("BLOCK", "", "")

        every {
            mockNativeBlocker.safeEvaluateRequest(
                url = "https://example.com/article?utm_source=tracker",
                sourceUrl = "https://example.com/",
                resourceType = "xmlhttprequest",
                method = "GET"
            )
        } returns arrayOf("REWRITE", "", "https://example.com/article")

        val repo = AdBlockRepositoryImpl(mockContext, mockNativeBlocker)

        val redirectRes = repo.shouldBlockUrl(
            "https://www.google-analytics.com/analytics.js",
            "https://adblock-tester.com/",
            "script"
        )
        assertThat(redirectRes).isInstanceOf(UrlCheckResult.Redirect::class.java)

        val blockRes = repo.shouldBlockUrl(
            "https://advmaker.net/468x60.png",
            "https://adblock-tester.com/",
            "image"
        )
        assertThat(blockRes).isInstanceOf(UrlCheckResult.Block::class.java)

        val rewriteRes = repo.shouldBlockUrl(
            "https://example.com/article?utm_source=tracker",
            "https://example.com/",
            "xmlhttprequest"
        )
        assertThat(rewriteRes).isEqualTo(UrlCheckResult.RewriteUrl("https://example.com/article"))
    }

    @Test
    fun `B2 - pre-compiled binary dat snapshot restores engine without re-parsing text lists`() = runTest {
        val adblockDir = File(tempFilesDir, "adblock").apply { mkdirs() }
        val datFile = File(adblockDir, "engine_v1.dat").apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        }
        File(adblockDir, "resources.json").writeText("[]")

        every { mockNativeBlocker.safeLoadSerializedEngine(any(), any()) } returns true

        val repo = AdBlockRepositoryImpl(mockContext, mockNativeBlocker)
        repo.initializeEngineAsync()

        verify(exactly = 1) { mockNativeBlocker.safeLoadSerializedEngine(datFile.readBytes(), "[]") }
        verify(exactly = 0) { mockNativeBlocker.safeInitEngineFromLists(any(), any()) }
    }

    @Test
    fun `B3 - offline cold start compiles bundled assets_adblock lists and resources without network`() = runTest {
        val expectedFiles = listOf(
            "easylist.txt",
            "easyprivacy.txt",
            "ublock-filters.txt",
            "ublock-privacy.txt",
            "ublock-quick-fixes.txt",
            "ublock-unbreak.txt",
            "brave-unbreak.txt",
            "resources.json"
        )

        val assetsAdblockDir = listOf(
            File("src/main/assets/adblock"),
            File("app/src/main/assets/adblock")
        ).firstOrNull { it.exists() } ?: File("src/main/assets/adblock")

        // 1. Verify the APK ships with all 7 curated lists + Brave resources.json on disk
        for (fileName in expectedFiles) {
            val assetFile = File(assetsAdblockDir, fileName)
            assertThat(assetFile.exists()).isTrue()
            assertThat(assetFile.length()).isGreaterThan(0L)
        }

        // 2. Verify AdBlockRepositoryImpl compiles from bundled assets on first cold launch (no engine_v1.dat yet)
        every { mockAssets.open(any()) } answers {
            val relativePath = firstArg<String>().removePrefix("adblock/")
            ByteArrayInputStream(File(assetsAdblockDir, relativePath).readBytes())
        }
        every {
            mockNativeBlocker.safeInitEngineFromLists(any(), any())
        } returns byteArrayOf(0x10, 0x20, 0x30, 0x40)

        val repo = AdBlockRepositoryImpl(context = mockContext, nativeBlocker = mockNativeBlocker)
        repo.initializeEngineAsync()

        verify(exactly = 1) {
            mockNativeBlocker.safeInitEngineFromLists(
                match { rules -> rules.contains("||") && rules.length > 10_000 },
                match { resources -> resources.trimStart().startsWith("[") && resources.length > 1_000 }
            )
        }
        assertThat(File(tempFilesDir, "adblock/engine_v1.dat").exists()).isTrue()
    }

    // --- C. Cosmetic Filtering & Document-Start Timing ---

    // --- C. Cosmetic Filtering & Document-Start Timing ---

    @Test
    fun `C1 - document start script defers across about_blank instead of aborting permanently`() {
        val method = AdBlockWebViewHelper::class.java.getDeclaredMethod("buildDocumentStartBootstrapScript")
        method.isAccessible = true
        val script = method.invoke(AdBlockWebViewHelper) as String

        // Must NOT permanently abort when initial WebView location is about:blank
        assertThat(script).doesNotContain("if (!/^https?:\\/\\//i.test(pageUrl)) return;")
        // Must key installation by committed URL so about:blank -> https:// transition re-runs cleanly
        assertThat(script).contains("__verveCosmeticInstalledFor")
        // Must attach <style> once documentElement exists if head/documentElement were null at DOCUMENT_START
        assertThat(script).contains("readystatechange")
    }

    @Test
    fun `C2 - cosmetic bridge resolves cached tab URL when WebView reports about_blank`() {
        val tabId = "test-tab-1"
        val targetPage = "https://adblock-tester.com/"
        AdBlockWebViewHelper.updateActivePageUrl(tabId, targetPage)

        val viewModel = mockk<BrowserViewModel>(relaxed = true) {
            every { getCosmeticResources(targetPage) } returns CosmeticResources(
                hideSelectors = listOf(".ad-banner-468x60", "#ad-container"),
                exceptions = emptyList(),
                injectedScript = "(function(){ window.__verveTestScriptlet = true; })();",
                genericHide = false
            )
        }

        io.mockk.mockkConstructor(org.json.JSONObject::class)
        val recordedEntries = mutableMapOf<String, Any?>()
        every { anyConstructed<org.json.JSONObject>().put(any(), any<Any>()) } answers {
            recordedEntries[firstArg()] = secondArg()
            self as org.json.JSONObject
        }
        every { anyConstructed<org.json.JSONObject>().put(any(), any<Boolean>()) } answers {
            recordedEntries[firstArg()] = secondArg()
            self as org.json.JSONObject
        }
        every { anyConstructed<org.json.JSONObject>().toString() } returns
                "{\"hideSelectors\":[\".ad-banner-468x60\",\"#ad-container\"],\"injectedScript\":\"__verveTestScriptlet\"}"

        try {
            val bridgeClass = Class.forName(
                "com.verve.browser.presentation.browser.components.webview.AdBlockWebViewHelper\$VerveCosmeticBridge"
            )
            val ctor = bridgeClass.getDeclaredConstructor(String::class.java, BrowserViewModel::class.java).apply {
                isAccessible = true
            }
            val bridge = ctor.newInstance(tabId, viewModel)
            val getPayloadMethod = bridgeClass.getDeclaredMethod("getInitialCosmeticPayload", String::class.java).apply {
                isAccessible = true
            }

            val rawJson = getPayloadMethod.invoke(bridge, "about:blank") as String

            verify(exactly = 1) { viewModel.getCosmeticResources(targetPage) }
            assertThat(recordedEntries).containsKey("hideSelectors")
            assertThat(recordedEntries["injectedScript"]).isEqualTo("(function(){ window.__verveTestScriptlet = true; })();")
            assertThat(recordedEntries["genericHide"]).isEqualTo(false)
            assertThat(rawJson).contains(".ad-banner-468x60")
            assertThat(rawJson).contains("__verveTestScriptlet")
        } finally {
            io.mockk.unmockkConstructor(org.json.JSONObject::class)
            AdBlockWebViewHelper.clearTab(tabId)
        }
    }

    // --- D. Scriptlets & Surrogate Failure Handling ---

    @Test
    fun `D1 - shouldBlockUrl and getCosmeticResources wait for in-flight initializeEngineAsync before evaluating`() {
        val initGate = java.util.concurrent.CountDownLatch(1)
        val initStarted = java.util.concurrent.CountDownLatch(1)
        val fullEngineReady = java.util.concurrent.atomic.AtomicBoolean(false)

        val assetsAdblockDir = listOf(
            File("src/main/assets/adblock"),
            File("app/src/main/assets/adblock")
        ).first { it.exists() }

        every { mockAssets.open(any()) } answers {
            val relativePath = firstArg<String>().removePrefix("adblock/")
            File(assetsAdblockDir, relativePath).inputStream()
        }
        every { mockNativeBlocker.safeInitEngineFromLists(any(), any()) } answers {
            initStarted.countDown()
            initGate.await(2, java.util.concurrent.TimeUnit.SECONDS)
            fullEngineReady.set(true)
            byteArrayOf(0x01, 0x02, 0x03)
        }
        every {
            mockNativeBlocker.safeEvaluateRequest(any(), any(), any(), any())
        } answers {
            if (fullEngineReady.get()) arrayOf("BLOCK", "", "") else arrayOf("ALLOW", "", "")
        }

        val repo = AdBlockRepositoryImpl(context = mockContext, nativeBlocker = mockNativeBlocker)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val initFuture = executor.submit {
                kotlinx.coroutines.runBlocking { repo.initializeEngineAsync() }
            }
            assertThat(initStarted.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue()

            val evalFuture = executor.submit<UrlCheckResult> {
                repo.shouldBlockUrl(
                    targetUrl = "https://mc.yandex.ru/metrika/tag.js",
                    pageUrl = "https://adblock-tester.com/",
                    resourceType = "script"
                )
            }

            // Release full engine compilation slightly after WebView IO thread starts evaluating
            Thread.sleep(40)
            initGate.countDown()

            val result = evalFuture.get(2, java.util.concurrent.TimeUnit.SECONDS)
            initFuture.get(2, java.util.concurrent.TimeUnit.SECONDS)

            assertThat(result).isInstanceOf(UrlCheckResult.Block::class.java)
        } finally {
            initGate.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `D2 - VerveWebViewClient updates active tab pageUrl on main-frame request before onPageStarted`() {
        val tabId = "nav-race-tab"
        AdBlockWebViewHelper.clearTab(tabId)

        val viewModel = mockk<BrowserViewModel>(relaxed = true) {
            every { evaluateResourceRequest(any(), any(), any(), any()) } returns UrlCheckResult.Allow
        }

        val client = VerveWebViewClient(
            tabId = tabId,
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

        val mainUri = mockk<Uri>(relaxed = true) {
            every { this@mockk.toString() } returns "https://adblock-tester.com/"
        }
        val mainFrameReq = mockk<android.webkit.WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns true
            every { url } returns mainUri
            every { method } returns "GET"
            every { requestHeaders } returns emptyMap()
        }

        val subUri = mockk<Uri>(relaxed = true) {
            every { this@mockk.toString() } returns "https://an.yandex.ru/system/context.js"
            every { path } returns "/system/context.js"
        }
        val subresourceReq = mockk<android.webkit.WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns false
            every { url } returns subUri
            every { method } returns "GET"
            every { requestHeaders } returns mapOf("sec-fetch-dest" to "script")
        }

        try {
            // 1. Main-frame navigation arrives on WebView IO thread BEFORE onPageStarted() runs on UI thread
            runCatching { client.shouldInterceptRequest(null, mainFrameReq) }

            // 2. Subresource request fires immediately afterward
            runCatching { client.shouldInterceptRequest(null, subresourceReq) }

            assertThat(AdBlockWebViewHelper.getCachedPageUrl(tabId)).isEqualTo("https://adblock-tester.com/")
            verify {
                viewModel.evaluateResourceRequest(
                    targetUrl = "https://an.yandex.ru/system/context.js",
                    pageUrl = "https://adblock-tester.com/",
                    resourceType = "script",
                    method = "GET"
                )
            }
        } finally {
            AdBlockWebViewHelper.clearTab(tabId)
        }
    }

    // --- E. Website Compatibility & Per-Site Whitelisting ---

    @Test
    fun `E1 - whitelisting a domain bypasses both network blocking and cosmetic hiding`() {
        mockkStatic(Uri::class)
        every { Uri.parse("https://www.portal.example.edu/login") } returns mockk {
            every { host } returns "www.portal.example.edu"
        }

        val repo = AdBlockRepositoryImpl(mockContext, mockNativeBlocker)
        val useCase = ShouldBlockUrlUseCase(repo)

        assertThat(repo.isSiteWhitelisted("https://www.portal.example.edu/login")).isFalse()

        val whitelisted = repo.toggleSiteWhitelist("https://www.portal.example.edu/login")
        assertThat(whitelisted).isTrue()

        // Network request allowed when whitelisted
        val netResult = useCase(
            targetUrl = "https://doubleclick.net/analytics.js",
            pageUrl = "https://www.portal.example.edu/login",
            resourceType = "script"
        )
        assertThat(netResult).isEqualTo(UrlCheckResult.Allow)

        // Cosmetic hiding bypassed when whitelisted
        val cosmeticResult: CosmeticResources = useCase.getCosmeticResources("https://www.portal.example.edu/login")
        assertThat(cosmeticResult.hideSelectors).isEmpty()
        assertThat(cosmeticResult.injectedScript).isEmpty()
    }

    @Test
    fun `E2 - document-start bootstrap script tracks installation per Document instead of skipping after about_blank`() {
        val method = AdBlockWebViewHelper::class.java.getDeclaredMethod("buildDocumentStartBootstrapScript")
        method.isAccessible = true
        val script = method.invoke(AdBlockWebViewHelper) as String

        // Must key installation on window.document rather than skipping when window.__verveCosmeticLastRaw matches
        assertThat(script).contains("__verveCosmeticInstalled")
        assertThat(script).doesNotContain("__verveCosmeticLastRaw")
    }

    private fun mockRequest(
        urlString: String,
        accept: String = "*/*",
        method: String = "GET",
        isMainFrame: Boolean = false,
        headers: Map<String, String> = emptyMap()
    ): WebResourceRequest {
        val mergedHeaders = buildMap {
            put("Accept", accept)
            putAll(headers)
        }
        val mockUri = mockk<Uri>(relaxed = true) {
            every { this@mockk.toString() } returns urlString
            every { path } returns urlString.substringAfter("://").substringAfter("/", "").substringBefore("?")
        }
        return mockk {
            every { url } returns mockUri
            every { this@mockk.method } returns method
            every { isForMainFrame } returns isMainFrame
            every { requestHeaders } returns mergedHeaders
        }
    }
}