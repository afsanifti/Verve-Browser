package com.verve.browser.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.repository.AdBlockRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class ShouldBlockUrlUseCaseTest {

    private lateinit var repository: AdBlockRepository
    private lateinit var useCase: ShouldBlockUrlUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = ShouldBlockUrlUseCase(repository)
    }

    @Test
    fun `invoke returns Allow immediately when resourceType is main_frame`() {
        val result = useCase(
            targetUrl = "https://doubleclick.net",
            pageUrl = "https://doubleclick.net",
            resourceType = "main_frame"
        )

        assertThat(result).isEqualTo(UrlCheckResult.Allow)
        verify(exactly = 0) { repository.shouldBlockUrl(any(), any(), any()) }
    }

    @Test
    fun `invoke delegates to repository when resourceType is script`() {
        every {
            repository.shouldBlockUrl("https://doubleclick.net/ad.js", "https://example.com", "script")
        } returns UrlCheckResult.Block("Matched ad rule")

        val result = useCase(
            targetUrl = "https://doubleclick.net/ad.js",
            pageUrl = "https://example.com",
            resourceType = "script"
        )

        assertThat(result).isInstanceOf(UrlCheckResult.Block::class.java)
        verify(exactly = 1) {
            repository.shouldBlockUrl("https://doubleclick.net/ad.js", "https://example.com", "script")
        }
    }
}