package com.verve.browser.adblock

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.data.repository.AdBlockRepositoryImpl
import com.verve.browser.domain.model.AdBlockMode
import com.verve.browser.domain.model.UrlCheckResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Before
import org.junit.Test

class AdBlockCategoryBTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var mockAssets: AssetManager
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var mockNativeBlocker: NativeAdBlocker
    private var savedModeName: String? = null

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        savedModeName = null
        tempDir = Files.createTempDirectory("verve_cat_b_test").toFile()
        mockAssets = mockk(relaxed = true)
        mockPrefsEditor = mockk(relaxed = true) {
            every { putStringSet(any(), any()) } returns this
            every { putLong(any(), any()) } returns this
            every { putString("adblock_mode", any()) } answers {
                savedModeName = secondArg()
                this@mockk
            }
        }
        mockPrefs = mockk(relaxed = true) {
            every { getStringSet(any(), any()) } returns emptySet()
            every { getString("adblock_mode", any()) } answers { savedModeName ?: secondArg() }
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
        unmockkStatic(Log::class)
        tempDir.deleteRecursively()
    }

    @Test
    fun `B1 - Standard mode allows 1P subdomain tracker while Aggressive mode blocks it and overrides generichide`() {
        val repo = AdBlockRepositoryImpl(context = mockContext, nativeBlocker = mockNativeBlocker)
        val firstPartyTracker = "https://metrics.example.com/collect.js"
        val pageUrl = "https://www.example.com/article"

        // Native engine allows when sourceUrl is first-party (example.com), blocks when evaluated as 3P
        every {
            mockNativeBlocker.safeEvaluateRequest(firstPartyTracker, pageUrl, "script", "GET")
        } returns arrayOf("ALLOW", "", "")
        every {
            mockNativeBlocker.safeEvaluateRequest(
                url = firstPartyTracker,
                sourceUrl = not(pageUrl),
                resourceType = "script",
                method = "GET"
            )
        } returns arrayOf("BLOCK", "", "")
        every {
            mockNativeBlocker.safeGetCosmeticResources(pageUrl)
        } returns """{"hideSelectors":[".ad"],"exceptions":[],"injectedScript":"","generichide":true}"""

        // 1. Default mode is STANDARD: 1P tracker allowed, generichide respected
        assertThat(repo.adBlockMode.value).isEqualTo(AdBlockMode.STANDARD)
        assertThat(repo.shouldBlockUrl(firstPartyTracker, pageUrl, "script", "GET"))
            .isEqualTo(UrlCheckResult.Allow)
        assertThat(repo.getCosmeticResources(pageUrl).genericHide).isTrue()

        // 2. Switch to AGGRESSIVE: 1P/CNAME tracker blocked, generichide suppressed
        repo.setAdBlockMode(AdBlockMode.AGGRESSIVE)
        assertThat(repo.adBlockMode.value).isEqualTo(AdBlockMode.AGGRESSIVE)
        assertThat(savedModeName).isEqualTo("AGGRESSIVE")
        assertThat(repo.shouldBlockUrl(firstPartyTracker, pageUrl, "script", "GET"))
            .isInstanceOf(UrlCheckResult.Block::class.java)
        assertThat(repo.getCosmeticResources(pageUrl).genericHide).isFalse()

        // 3. Re-instantiating repository restores persisted AGGRESSIVE mode
        val restoredRepo = AdBlockRepositoryImpl(context = mockContext, nativeBlocker = mockNativeBlocker)
        assertThat(restoredRepo.adBlockMode.value).isEqualTo(AdBlockMode.AGGRESSIVE)
    }

    @Test
    fun `B2 - whitelisting apex domain inherits to subdomains without leaking across TLD`() {
        val repo = AdBlockRepositoryImpl(context = mockContext, nativeBlocker = mockNativeBlocker)

        repo.toggleSiteWhitelist("https://www.example.com/home")

        assertThat(repo.isSiteWhitelisted("https://example.com/other")).isTrue()
        assertThat(repo.isSiteWhitelisted("https://m.example.com/article")).isTrue()
        assertThat(repo.isSiteWhitelisted("https://sub.deep.example.com/page")).isTrue()
        assertThat(repo.isSiteWhitelisted("https://badexample.com/")).isFalse()
    }

    @Test
    fun `B3 - curated filter sources include Brave first-party and static CNAME lists`() {
        val field = AdBlockRepositoryImpl::class.java.getDeclaredField("CURATED_FILTER_SOURCES")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sources = field.get(null) as List<Pair<String, String>>
        val fileNames = sources.map { it.first }

        assertThat(fileNames).contains("brave-firstparty.txt")
        assertThat(fileNames).contains("brave-firstparty-cname.txt")
    }
}