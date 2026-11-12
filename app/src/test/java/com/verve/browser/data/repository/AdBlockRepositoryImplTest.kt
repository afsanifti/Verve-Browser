package com.verve.browser.data.repository

import com.google.common.truth.Truth.assertThat
import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.domain.model.UrlCheckResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class AdBlockRepositoryImplTest {

    private lateinit var nativeAdBlocker: NativeAdBlocker
    private lateinit var repository: AdBlockRepositoryImpl

    @Before
    fun setUp() {
        nativeAdBlocker = mockk(relaxed = true)
        repository = AdBlockRepositoryImpl(nativeAdBlocker)
    }

    @Test
    fun `initializeEngine joins rules with newline delimiter and calls native bridge`() {
        val rules = listOf("||doubleclick.net^", "||google-analytics.com^")

        repository.initializeEngine(rules)

        verify(exactly = 1) {
            nativeAdBlocker.initEngineSafe("||doubleclick.net^\n||google-analytics.com^")
        }
    }

    @Test
    fun `shouldBlockUrl maps native true to UrlCheckResult Block`() {
        every {
            nativeAdBlocker.shouldBlockSafe("https://ad.com/banner.js", "https://example.com", "script")
        } returns true

        val result = repository.shouldBlockUrl("https://ad.com/banner.js", "https://example.com", "script")

        assertThat(result).isInstanceOf(UrlCheckResult.Block::class.java)
    }

    @Test
    fun `shouldBlockUrl maps native false to UrlCheckResult Allow`() {
        every {
            nativeAdBlocker.shouldBlockSafe("https://example.com/logo.png", "https://example.com", "image")
        } returns false

        val result = repository.shouldBlockUrl("https://example.com/logo.png", "https://example.com", "image")

        assertThat(result).isEqualTo(UrlCheckResult.Allow)
    }
}