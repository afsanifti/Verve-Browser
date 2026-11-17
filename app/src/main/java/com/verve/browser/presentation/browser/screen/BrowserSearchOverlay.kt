package com.verve.browser.presentation.browser.screen

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.searchpill.ExpressiveBottomToolbar
import com.verve.browser.presentation.browser.components.searchpill.SearchSuggestionsList
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool

@Composable
internal fun BrowserSearchOverlay(
    uiState: BrowserUiState,
    isOnWebPage: Boolean,
    bottomNavInsets: WindowInsets,
    webViewRef: WebView?,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Box(modifier = modifier.fillMaxSize()) {
        // Dim scrim behind search suggestions when focused
        AnimatedVisibility(
            visible = uiState.isSearchFocused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        viewModel.onSearchFocusChanged(false)
                    }
            )
        }

        // Suggestions list + floating bottom pill toolbar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            AnimatedVisibility(
                visible = uiState.isSearchFocused && !uiState.isIncognito && uiState.searchSuggestions.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SearchSuggestionsList(
                    suggestions = uiState.searchSuggestions,
                    onSuggestionClick = { suggestion ->
                        focusManager.clearFocus()
                        viewModel.onUrlSubmitted(suggestion)
                    },
                    onSuggestionFill = { suggestion ->
                        viewModel.onSearchInputChanged(suggestion)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            ExpressiveBottomToolbar(
                inputText = uiState.searchInput,
                onInputTextChange = viewModel::onSearchInputChanged,
                onUrlSubmitted = viewModel::onUrlSubmitted,
                onTabSwitcherClick = {
                    webViewRef?.let { activeView ->
                        thumbnailCache.captureFromWebView(uiState.activeTabId, activeView)
                    }
                    viewModel.openTabSwitcher()
                },
                isThemeLoading = uiState.isThemeExtracting,
                onThemeAnimationCompleted = viewModel::onThemeGradientAnimationCompleted,
                currentUrl = uiState.currentUrl,
                isWebMode = isOnWebPage,
                isCollapsed = uiState.isToolbarCollapsed,
                isSearchFocused = uiState.isSearchFocused,
                isPageLoading = uiState.isLoading,
                isIncognito = uiState.isIncognito,
                bottomWindowInsets = bottomNavInsets,
                onSearchFocusChanged = viewModel::onSearchFocusChanged,
                onCollapsedChange = { collapsed ->
                    viewModel.setToolbarCollapsed(collapsed = collapsed, isManual = true)
                },
                onToggleIncognito = viewModel::toggleIncognito,
                onRefreshClick = {
                    webViewRef?.reload()
                },
                onStopLoadingClick = {
                    webViewRef?.stopLoading()
                    viewModel.onPageLoadFinished(uiState.activeTabId)
                    viewModel.onRevealTimeout()
                }
            )
        }
    }
}