package com.verve.browser.presentation.browser.screen

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool

@Composable
internal fun BrowserBackHandler(
    uiState: BrowserUiState,
    isOnWebPage: Boolean,
    webViewRef: WebView?,
    onClearWebViewRef: () -> Unit,
    tabWebViewPool: TabWebViewPool,
    thumbnailCache: TabThumbnailCache,
    viewModel: BrowserViewModel
) {
    val focusManager = LocalFocusManager.current
    val hasActiveInlinePdf = uiState.activeTab?.activePdfUriString != null
    val canGoBackInWeb = isOnWebPage && (uiState.canGoBack || (webViewRef?.canGoBack() == true))

    BackHandler(
        enabled = uiState.isTabSwitcherOpen ||
                uiState.isSearchFocused ||
                hasActiveInlinePdf ||
                isOnWebPage ||
                canGoBackInWeb
    ) {
        if (uiState.isTabSwitcherOpen) {
            viewModel.closeTabSwitcher()
            return@BackHandler
        }

        if (uiState.isSearchFocused) {
            focusManager.clearFocus()
            viewModel.onSearchFocusChanged(false)
            return@BackHandler
        }

        if (hasActiveInlinePdf) {
            viewModel.closeInlinePdf(uiState.activeTabId)
            return@BackHandler
        }

        if (webViewRef != null && webViewRef.canGoBack()) {
            webViewRef.goBack()
        } else {
            val activeId = uiState.activeTabId
            onClearWebViewRef()
            tabWebViewPool.resetTab(activeId)
            thumbnailCache.remove(activeId)
            viewModel.navigateHome()
        }
    }
}