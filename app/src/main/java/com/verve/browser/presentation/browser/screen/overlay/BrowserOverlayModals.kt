package com.verve.browser.presentation.browser.screen.overlay

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.searchpill.PageActionsBottomSheet
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.downloads.DownloadsRoute

private const val DesktopUserAgent =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@Composable
internal fun BrowserOverlayModals(
    uiState: BrowserUiState,
    overlayState: BrowserOverlayState,
    webViewRef: WebView?,
    thumbnailCache: TabThumbnailCache,
    viewModel: BrowserViewModel,
    onSiteControlsClick: () -> Unit,
    onFindOnPageClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onZoomClick: () -> Unit
) {
    if (overlayState.isPageActionsSheetOpen) {
        PageActionsBottomSheet(
            currentUrl = uiState.currentUrl,
            isPageLoading = uiState.isLoading,
            canGoForward = uiState.activeTab?.canGoForward == true || (webViewRef?.canGoForward() == true),
            isTranslated = overlayState.isTranslated,
            translateLanguage = overlayState.translateLanguage,
            isShieldEnabled = overlayState.isShieldEnabled,
            isBookmarked = overlayState.isBookmarked,
            isDesktopSite = overlayState.isDesktopSite,
            isReaderMode = overlayState.isReaderMode,
            isQuicConnection = overlayState.isQuicEnabled && overlayState.isQuicConnection,
            onDismissRequest = { overlayState.isPageActionsSheetOpen = false },
            onRefreshClick = { webViewRef?.reload() },
            onStopLoadingClick = {
                webViewRef?.stopLoading()
                viewModel.onPageLoadFinished(uiState.activeTabId)
                viewModel.onRevealTimeout()
            },
            onForwardClick = {
                if (webViewRef?.canGoForward() == true) {
                    webViewRef.goForward()
                }
            },
            onTranslateToggle = { enabled ->
                overlayState.isTranslated = enabled
            },
            onTranslateSettingsClick = {
                overlayState.translateLanguage =
                    if (overlayState.translateLanguage == "English") "Bengali" else "English"
            },
            onSiteControlsClick = onSiteControlsClick,
            onFindOnPageClick = {
                overlayState.openFindInPage()
                onFindOnPageClick()
            },
            onDownloadsClick = {
                overlayState.openDownloads()
                onDownloadsClick()
            },
            onZoomClick = {
                overlayState.openZoomBar()
                onZoomClick()
            },
            onNewTabClick = {
                webViewRef?.let { activeView ->
                    thumbnailCache.captureFromWebView(
                        tabId = uiState.activeTabId,
                        webView = activeView,
                        isIncognito = uiState.isIncognito
                    )
                }
                viewModel.createNewTab(isIncognito = uiState.isIncognito)
            },
            onShieldToggle = { enabled ->
                overlayState.isShieldEnabled = enabled
            },
            onBookmarkToggle = { bookmarked ->
                overlayState.isBookmarked = bookmarked
            },
            onDesktopSiteToggle = { desktop ->
                overlayState.isDesktopSite = desktop
                webViewRef?.apply {
                    settings.userAgentString = if (desktop) DesktopUserAgent else null
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    reload()
                }
            },
            onReaderModeToggle = { reader ->
                overlayState.isReaderMode = reader
            }
        )
    }

    // Full-screen Downloads Manager overlay
    AnimatedVisibility(
        visible = overlayState.isDownloadsScreenOpen,
        enter = slideInVertically(
            animationSpec = BottomBarSlideSpring,
            initialOffsetY = { fullHeight -> fullHeight }
        ),
        exit = slideOutVertically(
            animationSpec = BottomBarSlideSpring,
            targetOffsetY = { fullHeight -> fullHeight }
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 1f)
        ) {
            DownloadsRoute(
                onNavigateBack = { overlayState.isDownloadsScreenOpen = false },
                onOpenUrl = { url ->
                    overlayState.isDownloadsScreenOpen = false
                    viewModel.onUrlSubmitted(url)
                }
            )
        }
    }
}