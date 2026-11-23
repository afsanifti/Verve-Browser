package com.verve.browser.presentation.browser.screen.overlay

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.verve.browser.data.download.VerveDownloadManager
import com.verve.browser.data.network.QuicNetworkManager
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.BrowserViewModel
import com.verve.browser.presentation.downloads.DownloadItem
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

internal val ZoomBarAutoDismissDuration = 3.5.seconds
internal val DownloadSnackbarDuration = 4.seconds

@Stable
internal class BrowserOverlayState(
    initialTranslateLanguage: String = "English",
    initialShieldEnabled: Boolean = true
) {
    var currentWebView by mutableStateOf<WebView?>(null)

    var isPageActionsSheetOpen by mutableStateOf(false)
    var isSiteControlsSheetOpen by mutableStateOf(false)
    var isDownloadsScreenOpen by mutableStateOf(false)

    // Top Chrome-style download snackbar state
    var activeBannerDownloadId by mutableStateOf<String?>(null)
    var bannerDisplayTick by mutableIntStateOf(0)
    var activeBannerItem by mutableStateOf<DownloadItem?>(null)

    // QUIC / HTTP/3 state
    var isQuicEnabled by mutableStateOf(false)
    var isQuicConnection by mutableStateOf(false)

    // Floating Zoom Bar state
    var isZoomBarVisible by mutableStateOf(false)
    var isInteractingWithZoom by mutableStateOf(false)
    var zoomInteractionTick by mutableIntStateOf(0)
    val tabZoomLevels = mutableStateMapOf<String, Int>()

    // Find in Page Bar state
    var isFindInPageVisible by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var findActiveMatchOrdinal by mutableIntStateOf(0)
    var findTotalMatches by mutableIntStateOf(0)

    // Page action sheet toggle states
    var isTranslated by mutableStateOf(false)
    var translateLanguage by mutableStateOf(initialTranslateLanguage)
    var isShieldEnabled by mutableStateOf(initialShieldEnabled)
    var isBookmarked by mutableStateOf(false)
    var isDesktopSite by mutableStateOf(false)
    var isReaderMode by mutableStateOf(false)

    fun zoomPercentFor(tabId: String): Int = tabZoomLevels[tabId] ?: 100

    fun openFindInPage() {
        isZoomBarVisible = false
        findQuery = ""
        findActiveMatchOrdinal = 0
        findTotalMatches = 0
        isFindInPageVisible = true
    }

    fun closeFindInPage() {
        currentWebView?.clearMatches()
        currentWebView?.setFindListener(null)
        isFindInPageVisible = false
        findQuery = ""
        findActiveMatchOrdinal = 0
        findTotalMatches = 0
    }

    fun openZoomBar() {
        if (isFindInPageVisible) {
            closeFindInPage()
        }
        isInteractingWithZoom = false
        zoomInteractionTick++
        isZoomBarVisible = true
    }

    fun openDownloads() {
        activeBannerDownloadId = null
        isDownloadsScreenOpen = true
    }
}

@Composable
internal fun rememberBrowserOverlayState(
    uiState: BrowserUiState,
    isOnWebPage: Boolean,
    webViewRef: WebView?,
    viewModel: BrowserViewModel
): BrowserOverlayState {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var savedDownloadsOpen by rememberSaveable { mutableStateOf(false) }
    var savedTranslateLanguage by rememberSaveable { mutableStateOf("English") }
    var savedShieldEnabled by rememberSaveable { mutableStateOf(true) }

    val state = remember {
        BrowserOverlayState(
            initialTranslateLanguage = savedTranslateLanguage,
            initialShieldEnabled = savedShieldEnabled
        ).apply {
            isDownloadsScreenOpen = savedDownloadsOpen
        }
    }

    state.currentWebView = webViewRef

    // Keep saveable mirrors synchronized
    LaunchedEffect(state.isDownloadsScreenOpen) {
        savedDownloadsOpen = state.isDownloadsScreenOpen
    }
    LaunchedEffect(state.translateLanguage) {
        savedTranslateLanguage = state.translateLanguage
    }
    LaunchedEffect(state.isShieldEnabled) {
        savedShieldEnabled = state.isShieldEnabled
    }

    // Reset per-tab / per-URL transient flags
    LaunchedEffect(uiState.activeTabId, uiState.currentUrl) {
        state.isQuicConnection = false
        state.isTranslated = false
        state.isReaderMode = false
        state.isSiteControlsSheetOpen = false
        state.isShieldEnabled = !viewModel.isSiteWhitelisted(uiState.currentUrl)
    }
    LaunchedEffect(uiState.currentUrl) {
        state.isBookmarked = false
    }
    LaunchedEffect(uiState.activeTabId) {
        state.isDesktopSite = false
        if (state.isFindInPageVisible) {
            state.closeFindInPage()
        }
    }

    val allDownloads by VerveDownloadManager.downloads.collectAsState()
    state.activeBannerItem = remember(allDownloads, state.activeBannerDownloadId) {
        state.activeBannerDownloadId?.let { id -> allDownloads.find { it.id == id } }
    }

    val isQuicEnabled by QuicNetworkManager.isQuicEnabled.collectAsState()
    state.isQuicEnabled = isQuicEnabled

    val currentZoomPercent = state.zoomPercentFor(uiState.activeTabId)

    BackHandler(enabled = state.isDownloadsScreenOpen) {
        state.isDownloadsScreenOpen = false
    }

    BackHandler(enabled = state.isFindInPageVisible && !state.isDownloadsScreenOpen) {
        focusManager.clearFocus()
        state.closeFindInPage()
    }

    LaunchedEffect(Unit) {
        QuicNetworkManager.initialize(context)
    }

    // Show top download snackbar whenever a new file download starts
    LaunchedEffect(Unit) {
        VerveDownloadManager.downloadStartedEvents.collect { startedItem ->
            if (!state.isDownloadsScreenOpen) {
                state.activeBannerDownloadId = startedItem.id
                state.bannerDisplayTick++
            }
        }
    }

    // Auto-dismiss top download snackbar after 4 seconds
    LaunchedEffect(state.activeBannerDownloadId, state.bannerDisplayTick) {
        if (state.activeBannerDownloadId != null) {
            delay(DownloadSnackbarDuration)
            state.activeBannerDownloadId = null
        }
    }

    // Prime Cronet QUIC session and inspect active WebView protocol
    LaunchedEffect(
        webViewRef,
        uiState.currentUrl,
        uiState.isLoading,
        isQuicEnabled,
        state.isPageActionsSheetOpen
    ) {
        if (!isQuicEnabled) {
            state.isQuicConnection = false
            return@LaunchedEffect
        }

        if (uiState.currentUrl.startsWith("https://", ignoreCase = true)) {
            QuicNetworkManager.warmUpQuicSession(context, uiState.currentUrl)
        }

        val activeWebView = webViewRef ?: return@LaunchedEffect
        if (!uiState.isLoading || state.isPageActionsSheetOpen) {
            QuicNetworkManager.detectActiveProtocol(activeWebView) { _, isQuic ->
                state.isQuicConnection = isQuic
            }
        }
    }

    // Keep active WebView textZoom synchronized with tab zoom state
    LaunchedEffect(webViewRef, uiState.activeTabId, currentZoomPercent) {
        webViewRef?.settings?.textZoom = currentZoomPercent
    }

    // Attach WebView find listener and synchronize query matches while Find in Page is active
    LaunchedEffect(webViewRef, state.isFindInPageVisible, state.findQuery, uiState.isLoading) {
        val activeWebView = webViewRef ?: return@LaunchedEffect
        if (!state.isFindInPageVisible) return@LaunchedEffect

        activeWebView.setFindListener { activeOrdinal, numberOfMatches, _ ->
            state.findActiveMatchOrdinal = activeOrdinal
            state.findTotalMatches = numberOfMatches
        }

        if (state.findQuery.isBlank()) {
            activeWebView.clearMatches()
            state.findActiveMatchOrdinal = 0
            state.findTotalMatches = 0
        } else {
            activeWebView.findAllAsync(state.findQuery)
        }
    }

    // Hide zoom & find bars if user leaves web mode or focuses search
    LaunchedEffect(uiState.activeTabId, isOnWebPage, uiState.isSearchFocused) {
        if (!isOnWebPage || uiState.isSearchFocused) {
            state.isZoomBarVisible = false
            if (state.isFindInPageVisible) {
                state.closeFindInPage()
            }
        }
    }

    // Auto-dismiss zoom bar after inactivity so search pill returns from bottom
    LaunchedEffect(state.isZoomBarVisible, state.isInteractingWithZoom, state.zoomInteractionTick) {
        if (state.isZoomBarVisible && !state.isInteractingWithZoom) {
            delay(ZoomBarAutoDismissDuration)
            state.isZoomBarVisible = false
        }
    }

    return state
}