package com.verve.browser.presentation.browser

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var shouldBlockUrlUseCase: ShouldBlockUrlUseCase
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        shouldBlockUrlUseCase = mockk()
        viewModel = BrowserViewModel(shouldBlockUrlUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onUrlSubmitted formats plain domain into valid https URL`() = runTest {
        viewModel.onUrlSubmitted("example.com")

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.currentUrl).isEqualTo("https://example.com")
        }
    }

    @Test
    fun `onProgressChanged sets isLoading true when loading is between 1 and 99`() = runTest {
        viewModel.onProgressChanged(50)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.loadingProgress).isEqualTo(50)
            assertThat(state.isLoading).isTrue()
        }
    }

    @Test
    fun `onProgressChanged sets isLoading false when progress reaches 100`() = runTest {
        viewModel.onProgressChanged(100)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
        }
    }

    @Test
    fun `checkResourceBlocking increments ad block count when URL is blocked`() = runTest {
        every {
            shouldBlockUrlUseCase("https://ad.com/script.js", "https://example.com", "script")
        } returns UrlCheckResult.Block()

        val isBlocked = viewModel.checkResourceBlocking(
            "https://ad.com/script.js",
            "https://example.com",
            "script"
        )

        assertThat(isBlocked).isTrue()
        assertThat(viewModel.uiState.value.blockedAdsCount).isEqualTo(1)
    }

    @Test
    fun `updateNavigationState modifies back and forward flags`() = runTest {
        viewModel.updateNavigationState(canGoBack = true, canGoForward = false)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.canGoBack).isTrue()
            assertThat(state.canGoForward).isFalse()
        }
    }
}