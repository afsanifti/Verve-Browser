package com.verve.browser.presentation.browser.screen

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.verve.browser.data.download.VerveDownloadManager
import com.verve.browser.data.network.QuicNetworkManager
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.browser.components.searchpill.ExpressiveBottomToolbar
import com.verve.browser.presentation.browser.components.searchpill.PageActionsBottomSheet
import com.verve.browser.presentation.browser.components.searchpill.PageZoomBottomBar
import com.verve.browser.presentation.browser.components.searchpill.SearchSuggestionsList
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.downloads.DownloadsRoute
import com.verve.browser.presentation.downloads.TopDownloadSnackbar
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private const val DesktopUserAgent =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val ZoomBarAutoDismissDuration = 3.5.seconds
private val DownloadSnackbarDuration = 4.seconds

private val BottomBarSlideSpring = spring<IntOffset>(
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
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var isPageActionsSheetOpen by remember { mutableStateOf(false) }
    var isDownloadsScreenOpen by rememberSaveable { mutableStateOf(false) }

    // Top Chrome-style download snackbar state
    val allDownloads by VerveDownloadManager.downloads.collectAsState()
    var activeBannerDownloadId by remember { mutableStateOf<String?>(null) }
    var bannerDisplayTick by remember { mutableIntStateOf(0) }
    val activeBannerItem = remember(allDownloads, activeBannerDownloadId) {
        activeBannerDownloadId?.let { id -> allDownloads.find { it.id == id } }
    }

    // QUIC / HTTP/3 state
    val isQuicEnabled by QuicNetworkManager.isQuicEnabled.collectAsState()
    var isQuicConnection by remember(uiState.activeTabId, uiState.currentUrl) {
        mutableStateOf(false)
    }

    // Floating Zoom Bar state (replaces bottom search pill while active)
    var isZoomBarVisible by remember { mutableStateOf(false) }
    var isInteractingWithZoom by remember { mutableStateOf(false) }
    var zoomInteractionTick by remember { mutableIntStateOf(0) }
    val tabZoomLevels = remember { mutableStateMapOf<String, Int>() }
    val currentZoomPercent = tabZoomLevels[uiState.activeTabId] ?: 100

    // Page action sheet toggle states
    var isTranslated by remember(uiState.activeTabId, uiState.currentUrl) { mutableStateOf(false) }
    var translateLanguage by rememberSaveable { mutableStateOf("English") }
    var isShieldEnabled by rememberSaveable { mutableStateOf(true) }
    var isBookmarked by remember(uiState.currentUrl) { mutableStateOf(false) }
    var isDesktopSite by remember(uiState.activeTabId) { mutableStateOf(false) }
    var isReaderMode by remember(uiState.activeTabId, uiState.currentUrl) { mutableStateOf(false) }

    BackHandler(enabled = isDownloadsScreenOpen) {
        isDownloadsScreenOpen = false
    }

    LaunchedEffect(Unit) {
        QuicNetworkManager.initialize(context)
    }

    // Show top download snackbar whenever a new file download starts
    LaunchedEffect(Unit) {
        VerveDownloadManager.downloadStartedEvents.collect { startedItem ->
            if (!isDownloadsScreenOpen) {
                activeBannerDownloadId = startedItem.id
                bannerDisplayTick++
            }
        }
    }

    // Auto-dismiss top download snackbar after 4 seconds
    LaunchedEffect(activeBannerDownloadId, bannerDisplayTick) {
        if (activeBannerDownloadId != null) {
            delay(DownloadSnackbarDuration)
            activeBannerDownloadId = null
        }
    }

    // Prime Cronet QUIC session and inspect active WebView protocol
    LaunchedEffect(webViewRef, uiState.currentUrl, uiState.isLoading, isQuicEnabled, isPageActionsSheetOpen) {
        if (!isQuicEnabled) {
            isQuicConnection = false
            return@LaunchedEffect
        }

        if (uiState.currentUrl.startsWith("https://", ignoreCase = true)) {
            QuicNetworkManager.warmUpQuicSession(context, uiState.currentUrl)
        }

        val activeWebView = webViewRef ?: return@LaunchedEffect
        if (!uiState.isLoading || isPageActionsSheetOpen) {
            QuicNetworkManager.detectActiveProtocol(activeWebView) { _, isQuic ->
                isQuicConnection = isQuic
            }
        }
    }

    // Keep active WebView textZoom synchronized with tab zoom state
    LaunchedEffect(webViewRef, uiState.activeTabId, currentZoomPercent) {
        webViewRef?.settings?.textZoom = currentZoomPercent
    }

    // Hide zoom bar if user switches tabs, leaves web mode, or focuses search
    LaunchedEffect(uiState.activeTabId, isOnWebPage, uiState.isSearchFocused) {
        if (!isOnWebPage || uiState.isSearchFocused) {
            isZoomBarVisible = false
        }
    }

    // Auto-dismiss zoom bar after inactivity so search pill returns from bottom
    LaunchedEffect(isZoomBarVisible, isInteractingWithZoom, zoomInteractionTick) {
        if (isZoomBarVisible && !isInteractingWithZoom) {
            delay(ZoomBarAutoDismissDuration)
            isZoomBarVisible = false
        }
    }

    val activeTabIndex = remember(uiState.tabs, uiState.activeTabId) {
        uiState.tabs.indexOfFirst { it.id == uiState.activeTabId }
    }
    val leftTab = if (activeTabIndex > 0) uiState.tabs[activeTabIndex - 1] else null
    val rightTab = if (activeTabIndex != -1 && activeTabIndex < uiState.tabs.lastIndex) {
        uiState.tabs[activeTabIndex + 1]
    } else {
        null
    }

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

        // Suggestions list + floating bottom pill toolbar (hidden while zoom bar is active)
        AnimatedVisibility(
            visible = !isZoomBarVisible,
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
                    isPageLoading = uiState.isLoading,
                    isIncognito = uiState.isIncognito,
                    canSwipeToLeftTab = leftTab != null,
                    canSwipeToRightTab = rightTab != null,
                    bottomWindowInsets = bottomNavInsets,
                    onSearchFocusChanged = viewModel::onSearchFocusChanged,
                    onCollapsedChange = { collapsed ->
                        viewModel.setToolbarCollapsed(collapsed = collapsed, isManual = true)
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
                        isPageActionsSheetOpen = true
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
            visible = isZoomBarVisible,
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
                zoomPercent = currentZoomPercent,
                bottomWindowInsets = bottomNavInsets,
                onZoomPercentChange = { updatedPercent ->
                    tabZoomLevels[uiState.activeTabId] = updatedPercent
                    webViewRef?.settings?.textZoom = updatedPercent
                    zoomInteractionTick++
                },
                onInteractionStateChange = { interacting ->
                    isInteractingWithZoom = interacting
                    if (!interacting) {
                        zoomInteractionTick++
                    }
                }
            )
        }

        // Top Chrome-style floating download banner
        TopDownloadSnackbar(
            item = activeBannerItem,
            onOpenDownloadsClick = {
                activeBannerDownloadId = null
                isDownloadsScreenOpen = true
                onDownloadsClick()
            },
            onDismissClick = {
                activeBannerDownloadId = null
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (isPageActionsSheetOpen) {
            PageActionsBottomSheet(
                currentUrl = uiState.currentUrl,
                isPageLoading = uiState.isLoading,
                canGoForward = uiState.activeTab?.canGoForward == true || (webViewRef?.canGoForward() == true),
                isTranslated = isTranslated,
                translateLanguage = translateLanguage,
                isShieldEnabled = isShieldEnabled,
                isBookmarked = isBookmarked,
                isDesktopSite = isDesktopSite,
                isReaderMode = isReaderMode,
                isQuicConnection = isQuicEnabled && isQuicConnection,
                onDismissRequest = { isPageActionsSheetOpen = false },
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
                    isTranslated = enabled
                },
                onTranslateSettingsClick = {
                    translateLanguage = if (translateLanguage == "English") "Bengali" else "English"
                },
                onSiteControlsClick = onSiteControlsClick,
                onFindOnPageClick = onFindOnPageClick,
                onDownloadsClick = {
                    activeBannerDownloadId = null
                    isDownloadsScreenOpen = true
                    onDownloadsClick()
                },
                onZoomClick = {
                    isInteractingWithZoom = false
                    zoomInteractionTick++
                    isZoomBarVisible = true
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
                    isShieldEnabled = enabled
                },
                onBookmarkToggle = { bookmarked ->
                    isBookmarked = bookmarked
                },
                onDesktopSiteToggle = { desktop ->
                    isDesktopSite = desktop
                    webViewRef?.apply {
                        settings.userAgentString = if (desktop) DesktopUserAgent else null
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        reload()
                    }
                },
                onReaderModeToggle = { reader ->
                    isReaderMode = reader
                }
            )
        }

        // Full-screen Downloads Manager overlay
        AnimatedVisibility(
            visible = isDownloadsScreenOpen,
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
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 1f)
            ) {
                DownloadsRoute(
                    onNavigateBack = { isDownloadsScreenOpen = false },
                    onOpenUrl = { url ->
                        isDownloadsScreenOpen = false
                        viewModel.onUrlSubmitted(url)
                    }
                )
            }
        }
    }
}