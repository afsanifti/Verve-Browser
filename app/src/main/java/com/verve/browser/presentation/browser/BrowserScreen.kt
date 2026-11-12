package com.verve.browser.presentation.browser

import android.webkit.WebView
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.components.ExpressiveBottomToolbar
import com.verve.browser.presentation.browser.components.PageRevealCurtain
import com.verve.browser.presentation.browser.components.SearchSuggestionsList
import com.verve.browser.presentation.browser.components.VerveWebView
import kotlinx.coroutines.delay

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val uiState: BrowserUiState by viewModel.uiState.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current

    val isOnWebPage = uiState.currentUrl.isNotEmpty()
    val canGoBackInWeb = uiState.canGoBack || (webViewRef?.canGoBack() == true)

    LaunchedEffect(uiState.currentUrl) {
        if (uiState.currentUrl.isNotEmpty()) {
            delay(1500L)
            viewModel.onRevealTimeout()
        }
    }

    BackHandler(enabled = uiState.isSearchFocused || isOnWebPage || canGoBackInWeb) {
        if (uiState.isSearchFocused) {
            focusManager.clearFocus()
            viewModel.onSearchFocusChanged(false)
            return@BackHandler
        }

        val webView = webViewRef
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            webViewRef = null
            viewModel.navigateHome()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (isOnWebPage) {
            VerveWebView(
                url = uiState.currentUrl,
                viewModel = viewModel,
                onWebViewCreated = { webViewRef = it },
                modifier = Modifier.fillMaxSize()
            )

            PageRevealCurtain(
                visible = !uiState.isPageReadyToReveal,
                progress = uiState.loadingProgress
            )
        }

        // Focused Search Overlay Backdrop (surfaceContainerLow)
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

        // Bottom-Anchored Suggestions + Search Toolbar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding(),
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
                onTabSwitcherClick = { /* open tab switcher */ },
                currentUrl = uiState.currentUrl,
                isWebMode = isOnWebPage,
                isCollapsed = uiState.isToolbarCollapsed,
                isSearchFocused = uiState.isSearchFocused,
                onSearchFocusChanged = viewModel::onSearchFocusChanged,
                onCollapsedChange = viewModel::setToolbarCollapsed,
                onLeftActionClick = { /* open page menu sheet */ },
                onSiteSettingsClick = { /* open site settings / adblock info */ },
                onRefreshClick = { webViewRef?.reload() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}