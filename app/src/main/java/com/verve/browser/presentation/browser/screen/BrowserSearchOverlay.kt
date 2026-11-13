package com.verve.browser.presentation.browser.screen

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.ExpressiveBottomToolbar
import com.verve.browser.presentation.browser.components.SearchSuggestionsList
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool

@Composable
internal fun BoxScope.BrowserSearchOverlay(
    uiState: BrowserUiState,
    isOnWebPage: Boolean,
    bottomNavInsets: WindowInsets,
    webViewRef: WebView?,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache,
    viewModel: BrowserViewModel
) {
    val focusManager = LocalFocusManager.current

    // Focused Search Overlay Backdrop
    AnimatedVisibility(
        visible = uiState.isSearchFocused,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                    viewModel.onSearchFocusChanged(false)
                }
        )
    }

    // Native Floating Material You Search Bar & Suggestions Overlay
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsPadding(bottomNavInsets.union(WindowInsets.ime)),
        verticalArrangement = Arrangement.Bottom
    ) {
        AnimatedVisibility(
            visible = uiState.isSearchFocused && uiState.searchSuggestions.isNotEmpty(),
            enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 6 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { it / 6 }
        ) {
            SearchSuggestionsList(
                suggestions = uiState.searchSuggestions,
                onSuggestionClick = { suggestion ->
                    viewModel.onUrlSubmitted(suggestion)
                    focusManager.clearFocus()
                },
                onSuggestionFill = { suggestion ->
                    viewModel.onSearchInputChanged(suggestion)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        ExpressiveBottomToolbar(
            inputText = uiState.searchInput,
            onInputTextChange = viewModel::onSearchInputChanged,
            onUrlSubmitted = viewModel::onUrlSubmitted,
            onTabSwitcherClick = {
                val activeId = uiState.activeTabId
                val activeView = tabWebViewPool.peekWebView(activeId) ?: webViewRef
                thumbnailCache.captureFromWebView(activeId, activeView)
                viewModel.openTabSwitcher()
            },
            currentUrl = uiState.currentUrl,
            isWebMode = isOnWebPage,
            isCollapsed = uiState.isToolbarCollapsed,
            isSearchFocused = uiState.isSearchFocused,
            bottomWindowInsets = WindowInsets(0, 0, 0, 0),
            onSearchFocusChanged = viewModel::onSearchFocusChanged,
            onCollapsedChange = { collapsed ->
                viewModel.setToolbarCollapsed(collapsed = collapsed, isManual = true)
            },
            onLeftActionClick = { /* open page menu sheet */ },
            onSiteSettingsClick = { /* open site settings / adblock info */ },
            onRefreshClick = { webViewRef?.reload() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}