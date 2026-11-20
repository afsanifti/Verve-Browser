package com.verve.browser.presentation.browser.screen.overlay

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
import com.verve.browser.presentation.browser.components.searchpill.BlankTabQuickClosePill
import com.verve.browser.presentation.browser.components.searchpill.ExpressiveBottomToolbar
import com.verve.browser.presentation.browser.components.searchpill.SearchSuggestionsList
import com.verve.browser.presentation.browser.components.searchpill.pageactions.PageZoomBottomBar
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool

@Composable
internal fun BoxScope.BrowserBottomOverlayDock(
    uiState: BrowserUiState,
    overlayState: BrowserOverlayState,
    isOnWebPage: Boolean,
    bottomNavInsets: WindowInsets,
    webViewRef: WebView?,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache,
    viewModel: BrowserViewModel,
    onSwipeStarted: () -> Unit,
    onSwipeFractionChanged: (Float) -> Unit,
    onSwipeCommitted: (targetTabId: String, direction: Int, currentFraction: Float) -> Unit,
    onSwipeCancelled: () -> Unit,
    onSiteControlsClick: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    val activeTabIndex = remember(uiState.tabs, uiState.activeTabId) {
        uiState.tabs.indexOfFirst { it.id == uiState.activeTabId }
    }
    val leftTab = if (activeTabIndex > 0) uiState.tabs[activeTabIndex - 1] else null
    val rightTab = if (activeTabIndex != -1 && activeTabIndex < uiState.tabs.lastIndex) {
        uiState.tabs[activeTabIndex + 1]
    } else {
        null
    }

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

    // Suggestions list + quick-close pill + floating bottom pill toolbar
    AnimatedVisibility(
        visible = !overlayState.isZoomBarVisible,
        enter = slideInVertically(
            animationSpec = BottomBarSlideSpring,
            initialOffsetY = { fullHeight -> fullHeight }
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = slideOutVertically(
            animationSpec = BottomBarSlideSpring,
            targetOffsetY = { fullHeight -> fullHeight }
        ) + fadeOut(animationSpec = tween(durationMillis = 140)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            AnimatedVisibility(
                visible = uiState.isSearchFocused &&
                        !uiState.isIncognito &&
                        uiState.searchSuggestions.isNotEmpty(),
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

            BlankTabQuickClosePill(
                visible = uiState.quickCloseTabId != null &&
                        uiState.quickCloseTabId == uiState.activeTabId &&
                        !uiState.isSearchFocused &&
                        !overlayState.isFindInPageVisible,
                onCloseClick = {
                    viewModel.confirmQuickCloseTab { closedId ->
                        tabWebViewPool.resetTab(closedId)
                        thumbnailCache.remove(closedId)
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            ExpressiveBottomToolbar(
                inputText = uiState.searchInput,
                onInputTextChange = viewModel::onSearchInputChanged,
                onUrlSubmitted = viewModel::onUrlSubmitted,
                onTabSwitcherClick = {
                    webViewRef?.let { activeView ->
                        thumbnailCache.captureFromWebView(
                            tabId = uiState.activeTabId,
                            webView = activeView,
                            isIncognito = uiState.isIncognito
                        )
                    }
                    viewModel.openTabSwitcher()
                },
                isThemeLoading = uiState.isThemeExtracting,
                onThemeAnimationCompleted = viewModel::onThemeGradientAnimationCompleted,
                currentUrl = uiState.currentUrl,
                isWebMode = isOnWebPage,
                isCollapsed = uiState.isToolbarCollapsed,
                isSearchFocused = uiState.isSearchFocused,
                isFindInPage = overlayState.isFindInPageVisible,
                findQuery = overlayState.findQuery,
                findActiveMatchOrdinal = overlayState.findActiveMatchOrdinal,
                findTotalMatches = overlayState.findTotalMatches,
                isPageLoading = uiState.isLoading,
                isIncognito = uiState.isIncognito,
                canSwipeToLeftTab = leftTab != null,
                canSwipeToRightTab = rightTab != null,
                bottomWindowInsets = bottomNavInsets,
                onSearchFocusChanged = viewModel::onSearchFocusChanged,
                onCollapsedChange = { collapsed ->
                    viewModel.setToolbarCollapsed(collapsed = collapsed, isManual = true)
                },
                onFindQueryChange = { updatedQuery ->
                    overlayState.findQuery = updatedQuery
                },
                onFindPrevious = {
                    webViewRef?.findNext(false)
                },
                onFindNext = {
                    webViewRef?.findNext(true)
                },
                onCloseFindInPage = {
                    focusManager.clearFocus()
                    overlayState.closeFindInPage()
                },
                onSwipeStarted = {
                    webViewRef?.let { activeView ->
                        thumbnailCache.captureFromWebView(
                            tabId = uiState.activeTabId,
                            webView = activeView,
                            isIncognito = uiState.isIncognito
                        )
                    }
                    onSwipeStarted()
                },
                onSwipeFractionChanged = onSwipeFractionChanged,
                onSwipeCommitted = { direction, currentFraction ->
                    val target = if (direction > 0) leftTab else rightTab
                    if (target != null) {
                        onSwipeCommitted(target.id, direction, currentFraction)
                    } else {
                        onSwipeCancelled()
                    }
                },
                onSwipeCancelled = onSwipeCancelled,
                onToggleIncognito = viewModel::toggleIncognito,
                onLeftActionClick = {
                    focusManager.clearFocus()
                    overlayState.isPageActionsSheetOpen = true
                },
                onSiteSettingsClick = {
                    focusManager.clearFocus()
                    overlayState.isSiteControlsSheetOpen = true
                    onSiteControlsClick()
                },
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

    // Temporary Floating Page Zoom Bar (slides in from bottom when Zoom is selected)
    AnimatedVisibility(
        visible = overlayState.isZoomBarVisible,
        enter = slideInVertically(
            animationSpec = BottomBarSlideSpring,
            initialOffsetY = { fullHeight -> fullHeight }
        ) + fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 50)),
        exit = slideOutVertically(
            animationSpec = BottomBarSlideSpring,
            targetOffsetY = { fullHeight -> fullHeight }
        ) + fadeOut(animationSpec = tween(durationMillis = 150)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
    ) {
        PageZoomBottomBar(
            zoomPercent = overlayState.zoomPercentFor(uiState.activeTabId),
            bottomWindowInsets = bottomNavInsets,
            onZoomPercentChange = { updatedPercent ->
                overlayState.tabZoomLevels[uiState.activeTabId] = updatedPercent
                webViewRef?.settings?.textZoom = updatedPercent
                overlayState.zoomInteractionTick++
            },
            onInteractionStateChange = { interacting ->
                overlayState.isInteractingWithZoom = interacting
                if (!interacting) {
                    overlayState.zoomInteractionTick++
                }
            }
        )
    }
}