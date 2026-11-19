package com.verve.browser.presentation.browser.screen.overlay

import android.webkit.WebView
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.downloads.TopDownloadSnackbar

internal val BottomBarSlideSpring = spring<IntOffset>(
    dampingRatio = 0.96f,
    stiffness = 420f
)

@Composable
internal fun BrowserSearchOverlay(
    uiState: BrowserUiState,
    isOnWebPage: Boolean,
    bottomNavInsets: WindowInsets,
    webViewRef: WebView?,
    thumbnailCache: TabThumbnailCache,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
    onSwipeStarted: () -> Unit = {},
    onSwipeFractionChanged: (Float) -> Unit = {},
    onSwipeCommitted: (targetTabId: String, direction: Int, currentFraction: Float) -> Unit = { _, _, _ -> },
    onSwipeCancelled: () -> Unit = {},
    onSiteControlsClick: () -> Unit = {},
    onFindOnPageClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onZoomClick: () -> Unit = {}
) {
    val overlayState = rememberBrowserOverlayState(
        uiState = uiState,
        isOnWebPage = isOnWebPage,
        webViewRef = webViewRef,
        viewModel = viewModel
    )

    Box(modifier = modifier.fillMaxSize()) {
        BrowserBottomOverlayDock(
            uiState = uiState,
            overlayState = overlayState,
            isOnWebPage = isOnWebPage,
            bottomNavInsets = bottomNavInsets,
            webViewRef = webViewRef,
            thumbnailCache = thumbnailCache,
            viewModel = viewModel,
            onSwipeStarted = onSwipeStarted,
            onSwipeFractionChanged = onSwipeFractionChanged,
            onSwipeCommitted = onSwipeCommitted,
            onSwipeCancelled = onSwipeCancelled
        )

        TopDownloadSnackbar(
            item = overlayState.activeBannerItem,
            onOpenDownloadsClick = {
                overlayState.openDownloads()
                onDownloadsClick()
            },
            onDismissClick = {
                overlayState.activeBannerDownloadId = null
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        BrowserOverlayModals(
            uiState = uiState,
            overlayState = overlayState,
            webViewRef = webViewRef,
            thumbnailCache = thumbnailCache,
            viewModel = viewModel,
            onSiteControlsClick = onSiteControlsClick,
            onFindOnPageClick = onFindOnPageClick,
            onDownloadsClick = onDownloadsClick,
            onZoomClick = onZoomClick
        )
    }
}