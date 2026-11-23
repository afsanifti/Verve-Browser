package com.verve.browser.adblock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.data.adblock.CnameResolver
import com.verve.browser.data.repository.AdBlockRepositoryImpl
import com.verve.browser.domain.model.UrlCheckResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Before
import org.junit.Test

class AdBlockCategoryCTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var mockNativeBlocker: NativeAdBlocker

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        tempDir = Files.createTempDirectory("verve_cat_c_test").toFile()
        val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
            every { putStringSet(any(), any()) } returns this
            every { putString(any(), any()) } returns this
            every { putLong(any(), any()) } returns this
        }
        val mockPrefs = mockk<SharedPreferences>(relaxed = true) {
            every { getStringSet(any(), any()) } returns emptySet()
            every { getString(any(), any()) } answers { secondArg() }
            every { edit() } returns mockPrefsEditor
        }
        mockContext = mockk(relaxed = true) {
            every { applicationContext } returns this
            every { filesDir } returns tempDir
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
    fun `C1 - dynamically uncloaks first-party CNAME tracker and caches DNS lookup`() {
        val dnsCalls = AtomicInteger(0)
        val resolver = CnameResolver(
            lookupClient = CnameResolver.CnameLookupClient { host ->
                dnsCalls.incrementAndGet()
                if (host == "metrics.publisher.com") {
                    CnameResolver.CnameQueryResult(
                        cnameChain = listOf("publisher.omtrdc.net"),
                        ttlSeconds = 300L
                    )
                } else {
                    CnameResolver.CnameQueryResult(emptyList(), ttlSeconds = 300L)
                }
            }
        )

        val repo = AdBlockRepositoryImpl(
            context = mockContext,
            nativeBlocker = mockNativeBlocker,
            cnameResolver = resolver
        )

        val pageUrl = "https://www.publisher.com/news"
        val cloakedUrl = "https://metrics.publisher.com/b/ss/pub/1/JS-2.0?page=home"
        val uncloakedUrl = "https://publisher.omtrdc.net/b/ss/pub/1/JS-2.0?page=home"

        // Native engine allows the 1P subdomain directly, but blocks the canonical 3P tracker host
        every { mockNativeBlocker.safeEvaluateRequest(cloakedUrl, any(), "script", "GET") } returns
                arrayOf("ALLOW", "", "")
        every { mockNativeBlocker.safeEvaluateRequest(uncloakedUrl, pageUrl, "script", "GET") } returns
                arrayOf("BLOCK", "", "")

        val firstDecision = repo.shouldBlockUrl(cloakedUrl, pageUrl, "script", "GET")
        assertThat(firstDecision).isInstanceOf(UrlCheckResult.Block::class.java)
        assertThat(dnsCalls.get()).isEqualTo(1)

        // Second request to the same cloaked host must hit in-memory LRU cache without another DNS query
        val secondDecision = repo.shouldBlockUrl(cloakedUrl, pageUrl, "script", "GET")
        assertThat(secondDecision).isInstanceOf(UrlCheckResult.Block::class.java)
        assertThat(dnsCalls.get()).isEqualTo(1)
    }

    @Test
    fun `C1 - allows legitimate first-party CDN CNAMEs when uncloaked host is not a tracker`() {
        val resolver = CnameResolver(
            lookupClient = CnameResolver.CnameLookupClient {
                CnameResolver.CnameQueryResult(
                    cnameChain = listOf("cdn.publisher.com.edgekey.net"),
                    ttlSeconds = 300L
                )
            }
        )
        val repo = AdBlockRepositoryImpl(
            context = mockContext,
            nativeBlocker = mockNativeBlocker,
            cnameResolver = resolver
        )

        every { mockNativeBlocker.safeEvaluateRequest(any(), any(), any(), any()) } returns
                arrayOf("ALLOW", "", "")

        val decision = repo.shouldBlockUrl(
            targetUrl = "https://cdn.publisher.com/assets/app.js",
            pageUrl = "https://www.publisher.com/",
            resourceType = "script",
            method = "GET"
        )
        assertThat(decision).isEqualTo(UrlCheckResult.Allow)
    }
}