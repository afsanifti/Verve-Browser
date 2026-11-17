package com.verve.browser.presentation.browser

import android.Manifest
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.verve.browser.data.update.UpdateNotificationHelper
import com.verve.browser.presentation.browser.components.QuickTabsScreen
import com.verve.browser.presentation.browser.screen.BrowserBackHandler
import com.verve.browser.presentation.browser.screen.BrowserBottomProgressiveCurtain
import com.verve.browser.presentation.browser.screen.BrowserSearchOverlay
import com.verve.browser.presentation.browser.screen.BrowserSystemBarSync
import com.verve.browser.presentation.browser.screen.BrowserTopSafeAreaCurtain
import com.verve.browser.presentation.browser.screen.BrowserWebViewport
import com.verve.browser.presentation.browser.screen.rememberBrowserCurtainMetrics
import com.verve.browser.presentation.browser.screen.rememberBrowserInsetsState
import com.verve.browser.presentation.browser.util.TabThumbnailCache
import com.verve.browser.presentation.browser.util.TabWebViewPool
import com.verve.browser.presentation.settings.SettingsFlow
import com.verve.browser.presentation.theme.VerveBrowserTheme
import com.verve.browser.presentation.update.AppUpdateController
import com.verve.browser.presentation.update.AppUpdateSheet
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    openUpdateSheetRequested: Boolean = false,
    onUpdateSheetRequestHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState: BrowserUiState by viewModel.uiState.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val tabWebViewPool = remember { TabWebViewPool() }
    val thumbnailCache = remember(context) { TabThumbnailCache(context) }

    DisposableEffect(tabWebViewPool, thumbnailCache) {
        onDispose {
            tabWebViewPool.destroyAll()
            thumbnailCache.clear()
        }
    }

    // Remove stale thumbnail files for tabs that were closed in prior sessions
    LaunchedEffect(Unit) {
        thumbnailCache.pruneOrphans(uiState.tabs.map { it.id }.toSet())
    }

    val updateController = remember { AppUpdateController(context, scope) }
    val updateState by updateController.uiState.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            updateState.availableUpdate?.let { manifest ->
                UpdateNotificationHelper.showUpdateAvailableNotification(
                    context = context,
                    manifest = manifest
                )
            }
        }
    }

    // Request notification permission on Android 13+ when opening the app
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !UpdateNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val latestWebViewRef by rememberUpdatedState(webViewRef)
    val latestActiveTabId by rememberUpdatedState(uiState.activeTabId)
    val latestCurrentUrl by rememberUpdatedState(uiState.currentUrl)
    val latestIsIncognito by rememberUpdatedState(uiState.isIncognito)
    val latestIsTabSwitcherOpen by rememberUpdatedState(uiState.isTabSwitcherOpen)
    val latestIsSettingsOpen by rememberUpdatedState(isSettingsOpen)

    // Check for updates on foreground and snapshot active tab thumbnail when backgrounding
    DisposableEffect(lifecycleOwner, updateController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    updateController.checkForUpdatesOnAppOpen()
                }
                Lifecycle.Event.ON_STOP -> {
                    val activeView = latestWebViewRef
                    if (!latestIsTabSwitcherOpen &&
                        !latestIsSettingsOpen &&
                        activeView != null &&
                        activeView.tag == latestActiveTabId &&
                        latestCurrentUrl.isNotEmpty()
                    ) {
                        thumbnailCache.captureFromWebView(
                            tabId = latestActiveTabId,
                            webView = activeView,
                            isIncognito = latestIsIncognito
                        )
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Open modal sheet immediately when user taps the system update notification
    LaunchedEffect(openUpdateSheetRequested) {
        if (openUpdateSheetRequested) {
            isSettingsOpen = false
            updateController.openModalSheet()
            onUpdateSheetRequestHandled()
        }
    }

    if (!isSettingsOpen) {
        AppUpdateSheet(
            state = updateState,
            onUpdateClick = updateController::startDownloadAndInstall,
            onDismiss = { updateController.dismissSheet() }
        )
    }

    val isOnWebPage = uiState.currentUrl.isNotEmpty()
    val defaultRegionColor = MaterialTheme.colorScheme.surfaceContainerLow
    val insetsState = rememberBrowserInsetsState()
    val curtainMetrics = rememberBrowserCurtainMetrics(
        uiState = uiState,
        isOnWebPage = isOnWebPage,
        bottomNavInsetHeightDp = insetsState.bottomNavInsetHeightDp,
        defaultRegionColor = defaultRegionColor
    )

    LaunchedEffect(isOnWebPage) {
        if (!isOnWebPage) {
            webViewRef = null
        }
    }

    LaunchedEffect(uiState.activeTabId, uiState.currentUrl) {
        if (uiState.currentUrl.isNotEmpty()) {
            delay(2500.milliseconds)
            viewModel.onRevealTimeout()
        }
    }

    // Capture fresh thumbnail only when actively viewing the webpage (never inside QuickTabs or Settings)
    LaunchedEffect(
        uiState.activeTabId,
        uiState.currentUrl,
        uiState.isLoading,
        uiState.isTabSwitcherOpen,
        isSettingsOpen
    ) {
        val activeView = webViewRef
        if (!uiState.isTabSwitcherOpen &&
            !isSettingsOpen &&
            !uiState.isLoading &&
            uiState.currentUrl.isNotEmpty() &&
            activeView != null &&
            activeView.tag == uiState.activeTabId
        ) {
            delay(300.milliseconds)
            if (!uiState.isTabSwitcherOpen &&
                !isSettingsOpen &&
                webViewRef?.tag == uiState.activeTabId
            ) {
                thumbnailCache.captureFromWebView(
                    tabId = uiState.activeTabId,
                    webView = activeView,
                    isIncognito = uiState.isIncognito
                )
            }
        }
    }

    BrowserSystemBarSync(
        isTabSwitcherOpen = uiState.isTabSwitcherOpen || isSettingsOpen,
        targetTopColor = curtainMetrics.targetTopColor,
        targetBottomColor = curtainMetrics.targetBottomColor
    )

    BrowserBackHandler(
        uiState = uiState,
        isOnWebPage = isOnWebPage,
        webViewRef = webViewRef,
        onClearWebViewRef = { webViewRef = null },
        tabWebViewPool = tabWebViewPool,
        thumbnailCache = thumbnailCache,
        viewModel = viewModel
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(defaultRegionColor)
    ) {
        // LAYER 1: Webpage Viewport
        BrowserWebViewport(
            uiState = uiState,
            isOnWebPage = isOnWebPage,
            insetsState = insetsState,
            curtainMetrics = curtainMetrics,
            viewModel = viewModel,
            tabWebViewPool = tabWebViewPool,
            thumbnailCache = thumbnailCache,
            onWebViewCreated = { webViewRef = it }
        )

        // LAYER 2A: Independent Adaptive Top Region (status bar + display cutout)
        BrowserTopSafeAreaCurtain(
            topSafeAreaHeightDp = insetsState.topSafeAreaHeightDp,
            defaultRegionColor = defaultRegionColor,
            animatedTopColor = curtainMetrics.animatedTopColor,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // LAYER 2B: Decoupled Progressive Bottom Curtain
        BrowserBottomProgressiveCurtain(
            curtainMetrics = curtainMetrics,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // LAYER 3: Search Backdrop, Suggestions & Floating Bottom Toolbar
        BrowserSearchOverlay(
            uiState = uiState,
            isOnWebPage = isOnWebPage,
            bottomNavInsets = insetsState.bottomNavInsets,
            webViewRef = webViewRef,
            tabWebViewPool = tabWebViewPool,
            thumbnailCache = thumbnailCache,
            viewModel = viewModel
        )

        // LAYER 4: Dedicated Card-Based Quick Tabs Switcher
        if (uiState.isTabSwitcherOpen) {
            QuickTabsScreen(
                tabs = uiState.tabs,
                tabStacks = uiState.tabStacks,
                activeTabId = uiState.activeTabId,
                thumbnailProvider = thumbnailCache::get,
                onBackClick = viewModel::closeTabSwitcher,
                onSelectTab = { tabId ->
                    viewModel.switchToTab(tabId)
                },
                onCloseTab = { tabId ->
                    if (webViewRef?.tag == tabId) {
                        webViewRef = null
                    }
                    viewModel.closeTab(tabId) { closedId ->
                        tabWebViewPool.resetTab(closedId)
                        thumbnailCache.remove(closedId)
                    }
                },
                onNewTabClick = {
                    webViewRef = null
                    viewModel.createNewTab()
                },
                onCreateTabStack = { tabIds, title ->
                    viewModel.createTabStack(tabIds = tabIds, title = title)
                },
                onAddTabToStack = viewModel::addTabToStack,
                onRemoveTabFromStack = viewModel::removeTabFromStack,
                onToggleStackCollapsed = viewModel::toggleStackCollapsed,
                onSettingsClick = { isSettingsOpen = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // LAYER 5: Settings & About Verve
        if (isSettingsOpen) {
            VerveBrowserTheme(siteSeedColor = null) {
                SettingsFlow(
                    updateController = updateController,
                    onExitSettings = { isSettingsOpen = false }
                )
            }
        }
    }
}