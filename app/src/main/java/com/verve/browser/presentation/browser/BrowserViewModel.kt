package com.verve.browser.presentation.browser

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verve.browser.data.local.PersistedSessionSnapshot
import com.verve.browser.data.local.TabSessionStorage
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase
import com.verve.browser.presentation.browser.util.IncognitoSessionManager
import com.verve.browser.presentation.browser.viewmodel.PageAppearanceAndProgressController
import com.verve.browser.presentation.browser.viewmodel.SearchSuggestionsController
import com.verve.browser.presentation.browser.viewmodel.TabSessionController
import com.verve.browser.presentation.browser.viewmodel.ToolbarAndCurtainController
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class BrowserViewModel(
    private val shouldBlockUrlUseCase: ShouldBlockUrlUseCase,
    private val tabSessionStorage: TabSessionStorage,
    private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val searchSuggestionsController = SearchSuggestionsController(
        uiState = _uiState,
        scope = viewModelScope
    )
    private val toolbarAndCurtainController = ToolbarAndCurtainController(uiState = _uiState)
    private val pageAppearanceController = PageAppearanceAndProgressController(uiState = _uiState)
    private val tabSessionController = TabSessionController(uiState = _uiState)

    private var quickCloseJob: Job? = null

    init {
        tabSessionStorage.loadSync()?.let { restored ->
            val nonIncognitoTabs = restored.tabs.filterNot { it.isIncognito }
            if (nonIncognitoTabs.isNotEmpty()) {
                val activeTab = nonIncognitoTabs.firstOrNull { it.id == restored.activeTabId }
                    ?: nonIncognitoTabs.first()
                _uiState.update { state ->
                    state.copy(
                        tabs = nonIncognitoTabs,
                        tabStacks = restored.tabStacks,
                        activeTabId = activeTab.id,
                        currentUrl = activeTab.url,
                        searchInput = activeTab.url,
                        siteSeedColor = activeTab.siteSeedColor,
                        pendingSiteSeedColor = activeTab.siteSeedColor,
                        topRegionColor = activeTab.topRegionColor,
                        bottomRegionColor = activeTab.bottomRegionColor,
                        blockedAdsCount = activeTab.blockedAdsCount
                    )
                }
            }
        }

        viewModelScope.launch {
            _uiState
                .map { state ->
                    val nonIncognitoTabs = state.tabs
                        .filter { !it.isIncognito }
                        .filterNot { tab ->
                            tab.url.startsWith("file://", ignoreCase = true) ||
                                    (tab.activePdfUriString != null &&
                                            !tab.url.startsWith("http://", ignoreCase = true) &&
                                            !tab.url.startsWith("https://", ignoreCase = true))
                        }
                        .map { tab ->
                            tab.copy(
                                isMainFrameLoading = false,
                                isLoading = false,
                                loadingProgress = 0,
                                activePdfUriString = null,
                                activePdfTitle = null
                            )
                        }
                    val resolvedActiveId = if (nonIncognitoTabs.any { it.id == state.activeTabId }) {
                        state.activeTabId
                    } else {
                        nonIncognitoTabs.firstOrNull()?.id ?: BrowserUiState.INITIAL_TAB_ID
                    }
                    PersistedSessionSnapshot(
                        tabs = nonIncognitoTabs,
                        tabStacks = state.tabStacks,
                        activeTabId = resolvedActiveId
                    )
                }
                .distinctUntilChanged()
                .debounce(400L.milliseconds)
                .collectLatest { snapshot ->
                    tabSessionStorage.save(snapshot)
                }
        }
    }

    private fun syncIncognitoEnvironment() {
        val ctx = appContext ?: return
        val state = _uiState.value
        IncognitoSessionManager.syncIncognitoTabsState(
            context = ctx,
            hasAnyIncognitoTab = state.tabs.any { it.isIncognito },
            activeTabIsIncognito = state.isIncognito
        )
    }

    fun openTabSwitcher() {
        dismissQuickClosePrompt()
        tabSessionController.openTabSwitcher()
    }

    fun closeTabSwitcher() = tabSessionController.closeTabSwitcher()

    fun createNewTab(
        initialUrl: String = "",
        isIncognito: Boolean = false,
        stackId: String? = null
    ): String {
        dismissQuickClosePrompt()
        searchSuggestionsController.cancelPendingSuggestions()
        val id = tabSessionController.createNewTab(
            initialUrl = initialUrl,
            isIncognito = isIncognito,
            stackId = stackId
        )
        syncIncognitoEnvironment()
        return id
    }

    fun openBlankTargetUrl(url: String, sourceTabId: String? = null) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty() || trimmedUrl == "about:blank") return

        val openerTab = _uiState.value.tabs.firstOrNull {
            it.id == (sourceTabId ?: _uiState.value.activeTabId)
        }
        val newTabId = createNewTab(
            initialUrl = trimmedUrl,
            isIncognito = openerTab?.isIncognito ?: false,
            stackId = openerTab?.stackId
        )
        triggerQuickClosePrompt(newTabId)
    }

    private fun triggerQuickClosePrompt(tabId: String) {
        quickCloseJob?.cancel()
        _uiState.update { it.copy(quickCloseTabId = tabId) }
        quickCloseJob = viewModelScope.launch {
            delay(5000L.milliseconds)
            _uiState.update { state ->
                if (state.quickCloseTabId == tabId) {
                    state.copy(quickCloseTabId = null)
                } else {
                    state
                }
            }
        }
    }

    fun confirmQuickCloseTab(onTabClosed: (String) -> Unit = {}) {
        val targetTabId = _uiState.value.quickCloseTabId ?: return
        dismissQuickClosePrompt()
        closeTab(tabId = targetTabId, onTabClosed = onTabClosed)
    }

    fun dismissQuickClosePrompt() {
        quickCloseJob?.cancel()
        quickCloseJob = null
        if (_uiState.value.quickCloseTabId != null) {
            _uiState.update { it.copy(quickCloseTabId = null) }
        }
    }

    fun switchToTab(tabId: String) {
        dismissQuickClosePrompt()
        searchSuggestionsController.cancelPendingSuggestions()
        tabSessionController.switchToTab(tabId)
        syncIncognitoEnvironment()
    }

    fun closeTab(tabId: String, onTabClosed: (String) -> Unit = {}) {
        if (_uiState.value.quickCloseTabId == tabId) {
            dismissQuickClosePrompt()
        }
        tabSessionController.closeTab(tabId = tabId, onTabClosed = onTabClosed)
        syncIncognitoEnvironment()
    }

    fun createTabStack(
        tabIds: List<String>,
        title: String,
        seedColor: Color? = null
    ): String? = tabSessionController.createTabStack(
        tabIds = tabIds,
        title = title,
        seedColor = seedColor
    )

    fun addTabToStack(tabId: String, stackId: String) =
        tabSessionController.addTabToStack(tabId = tabId, stackId = stackId)

    fun removeTabFromStack(tabId: String) =
        tabSessionController.removeTabFromStack(tabId = tabId)

    fun toggleStackCollapsed(stackId: String) =
        tabSessionController.toggleStackCollapsed(stackId = stackId)

    fun toggleIncognito() {
        searchSuggestionsController.cancelPendingSuggestions()
        _uiState.update { state ->
            val currentTab = state.activeTab ?: return@update state
            val nextIncognito = !currentTab.isIncognito
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == currentTab.id) {
                    tab.copy(
                        isIncognito = nextIncognito,
                        stackId = if (nextIncognito) null else tab.stackId,
                        siteSeedColor = if (nextIncognito) null else tab.siteSeedColor
                    )
                } else {
                    tab
                }
            }
            val tabCountsByStack = updatedTabs.mapNotNull { it.stackId }.groupingBy { it }.eachCount()
            val validStackIds = tabCountsByStack.filterValues { it >= 2 }.keys
            val normalizedTabs = updatedTabs.map { tab ->
                if (tab.stackId != null && tab.stackId !in validStackIds) {
                    tab.copy(stackId = null)
                } else {
                    tab
                }
            }
            val normalizedStacks = state.tabStacks.filter { it.id in validStackIds }

            state.copy(
                tabs = normalizedTabs,
                tabStacks = normalizedStacks,
                searchSuggestions = if (nextIncognito) emptyList() else state.recentSearches.take(4),
                siteSeedColor = if (nextIncognito) null else state.siteSeedColor,
                pendingSiteSeedColor = if (nextIncognito) null else state.pendingSiteSeedColor,
                isThemeExtracting = !nextIncognito && state.isThemeExtracting
            )
        }
        syncIncognitoEnvironment()
    }

    fun onSearchFocusChanged(focused: Boolean) =
        searchSuggestionsController.onSearchFocusChanged(focused)

    fun onSearchInputChanged(input: String) =
        searchSuggestionsController.onSearchInputChanged(input)

    fun setToolbarCollapsed(collapsed: Boolean, isManual: Boolean = false) =
        toolbarAndCurtainController.setToolbarCollapsed(collapsed = collapsed, isManual = isManual)

    fun updateBottomCurtainProgress(progress: Float) =
        toolbarAndCurtainController.updateBottomCurtainProgress(progress)

    fun updateHasFixedBottomBar(hasBar: Boolean) =
        toolbarAndCurtainController.updateHasFixedBottomBar(hasBar)

    fun onPageUrlUpdated(tabId: String, url: String) =
        pageAppearanceController.onPageUrlUpdated(tabId = tabId, url = url)

    fun onPageTitleUpdated(tabId: String, title: String) =
        pageAppearanceController.onPageTitleUpdated(tabId = tabId, title = title)

    fun onPageLoadStarted(tabId: String) =
        pageAppearanceController.onPageLoadStarted(tabId = tabId)

    fun onPageLoadFinished(tabId: String) =
        pageAppearanceController.onPageLoadFinished(tabId = tabId)

    fun onSnapshotColorReady(tabId: String, color: Color?) {
        if (_uiState.value.tabs.firstOrNull { it.id == tabId }?.isIncognito == true) {
            pageAppearanceController.onSnapshotColorReady(tabId = tabId, color = null)
            return
        }
        pageAppearanceController.onSnapshotColorReady(tabId = tabId, color = color)
    }

    fun onThemeGradientAnimationCompleted() =
        pageAppearanceController.onThemeGradientAnimationCompleted()

    fun onRevealTimeout() = pageAppearanceController.onRevealTimeout()

    fun updateTopRegionColor(tabId: String, color: Color?) =
        pageAppearanceController.updateTopRegionColor(tabId = tabId, color = color)

    fun updateBottomRegionColor(tabId: String, color: Color?) =
        pageAppearanceController.updateBottomRegionColor(tabId = tabId, color = color)

    fun openInlinePdf(
        tabId: String,
        uri: Uri,
        title: String,
        sourceUrl: String? = null,
        openInNewTab: Boolean = true
    ) {
        dismissQuickClosePrompt()
        searchSuggestionsController.cancelPendingSuggestions()

        val cleanTitle = title.ifBlank { "document.pdf" }
        val resolvedTabUrl = sourceUrl
            ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            ?: uri.toString()

        if (!openInNewTab) {
            tabSessionController.openInlinePdf(
                tabId = tabId,
                pdfUriString = uri.toString(),
                pdfTitle = cleanTitle
            )
            pageAppearanceController.onPageTitleUpdated(tabId = tabId, title = cleanTitle)
            return
        }

        val openerTab = _uiState.value.tabs.firstOrNull {
            it.id == tabId.ifBlank { _uiState.value.activeTabId }
        }
        val targetTabId = if (openerTab != null && openerTab.url.isEmpty() && openerTab.activePdfUriString == null) {
            pageAppearanceController.onPageUrlUpdated(tabId = openerTab.id, url = resolvedTabUrl)
            openerTab.id
        } else {
            createNewTab(
                initialUrl = resolvedTabUrl,
                isIncognito = openerTab?.isIncognito ?: false,
                stackId = openerTab?.stackId
            )
        }

        tabSessionController.openInlinePdf(
            tabId = targetTabId,
            pdfUriString = uri.toString(),
            pdfTitle = cleanTitle
        )
        pageAppearanceController.onPageTitleUpdated(tabId = targetTabId, title = cleanTitle)
    }

    fun closeInlinePdf(tabId: String = _uiState.value.activeTabId) {
        tabSessionController.closeInlinePdf(tabId)
    }

    fun navigateHome() {
        dismissQuickClosePrompt()
        searchSuggestionsController.cancelPendingSuggestions()
        tabSessionController.navigateHome()
    }

    fun onUrlSubmitted(rawInput: String) {
        if (rawInput.trim().isEmpty()) return
        searchSuggestionsController.cancelPendingSuggestions()
        if (_uiState.value.activeTab?.activePdfUriString != null) {
            tabSessionController.closeInlinePdf(_uiState.value.activeTabId)
        }
        tabSessionController.onUrlSubmitted(rawInput)
    }

    fun onProgressChanged(tabId: String, progress: Int) =
        pageAppearanceController.onProgressChanged(tabId = tabId, progress = progress)

    fun updateNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) =
        pageAppearanceController.updateNavigationState(
            tabId = tabId,
            canGoBack = canGoBack,
            canGoForward = canGoForward
        )

    fun evaluateResourceRequest(
        targetUrl: String,
        pageUrl: String,
        resourceType: String,
        method: String = "GET",
        tabId: String = _uiState.value.activeTabId
    ): UrlCheckResult {
        val result = shouldBlockUrlUseCase(targetUrl, pageUrl, resourceType, method)
        if (result is UrlCheckResult.Block || result is UrlCheckResult.Redirect) {
            _uiState.update { state ->
                val resolvedTabId = tabId.ifBlank { state.activeTabId }
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == resolvedTabId) {
                        tab.copy(blockedAdsCount = tab.blockedAdsCount + 1)
                    } else {
                        tab
                    }
                }
                val activeCount = if (resolvedTabId == state.activeTabId) {
                    state.blockedAdsCount + 1
                } else {
                    state.blockedAdsCount
                }
                state.copy(
                    tabs = updatedTabs,
                    blockedAdsCount = activeCount
                )
            }
        }
        return result
    }

    fun checkResourceBlocking(
        targetUrl: String,
        pageUrl: String,
        resourceType: String,
        tabId: String = _uiState.value.activeTabId
    ): Boolean {
        return when (evaluateResourceRequest(targetUrl, pageUrl, resourceType, tabId = tabId)) {
            is UrlCheckResult.Block, is UrlCheckResult.Redirect -> true
            UrlCheckResult.Allow, is UrlCheckResult.RewriteUrl -> false
        }
    }

    fun getCosmeticResources(pageUrl: String) =
        shouldBlockUrlUseCase.getCosmeticResources(pageUrl)

    fun getHiddenClassIdSelectors(
        pageUrl: String,
        classes: List<String>,
        ids: List<String>,
        exceptions: List<String>
    ): List<String> = shouldBlockUrlUseCase.getHiddenClassIdSelectors(
        pageUrl = pageUrl,
        classes = classes,
        ids = ids,
        exceptions = exceptions
    )

    fun isSiteWhitelisted(pageUrl: String): Boolean =
        shouldBlockUrlUseCase.isSiteWhitelisted(pageUrl)

    fun toggleSiteWhitelist(pageUrl: String): Boolean =
        shouldBlockUrlUseCase.toggleSiteWhitelist(pageUrl)

    val adBlockMode: StateFlow<com.verve.browser.domain.model.AdBlockMode>
        get() = shouldBlockUrlUseCase.adBlockMode

    fun setAdBlockMode(mode: com.verve.browser.domain.model.AdBlockMode) {
        shouldBlockUrlUseCase.setAdBlockMode(mode)
    }
}