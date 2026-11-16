package com.verve.browser.presentation.browser

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verve.browser.data.local.PersistedSessionSnapshot
import com.verve.browser.data.local.TabSessionStorage
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase
import com.verve.browser.presentation.browser.viewmodel.PageAppearanceAndProgressController
import com.verve.browser.presentation.browser.viewmodel.SearchSuggestionsController
import com.verve.browser.presentation.browser.viewmodel.TabSessionController
import com.verve.browser.presentation.browser.viewmodel.ToolbarAndCurtainController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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
    private val tabSessionStorage: TabSessionStorage
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

    init {
        tabSessionStorage.loadSync()?.let { restored ->
            val activeTab = restored.tabs.firstOrNull { it.id == restored.activeTabId } ?: restored.tabs.first()
            _uiState.update { state ->
                state.copy(
                    tabs = restored.tabs,
                    tabStacks = restored.tabStacks,
                    activeTabId = activeTab.id,
                    currentUrl = activeTab.url,
                    searchInput = activeTab.url,
                    siteSeedColor = activeTab.siteSeedColor,
                    pendingSiteSeedColor = activeTab.siteSeedColor,
                    topRegionColor = activeTab.topRegionColor,
                    bottomRegionColor = activeTab.bottomRegionColor
                )
            }
        }

        viewModelScope.launch {
            _uiState
                .map { state ->
                    PersistedSessionSnapshot(
                        tabs = state.tabs.filter { !it.isIncognito }.map { tab ->
                            tab.copy(
                                isMainFrameLoading = false,
                                isLoading = false,
                                loadingProgress = 0
                            )
                        },
                        tabStacks = state.tabStacks,
                        activeTabId = state.activeTabId
                    )
                }
                .distinctUntilChanged()
                .debounce(400L.milliseconds)
                .collectLatest { snapshot ->
                    tabSessionStorage.save(snapshot)
                }
        }
    }

    fun openTabSwitcher() = tabSessionController.openTabSwitcher()

    fun closeTabSwitcher() = tabSessionController.closeTabSwitcher()

    fun createNewTab(
        initialUrl: String = "",
        isIncognito: Boolean = false,
        stackId: String? = null
    ): String {
        searchSuggestionsController.cancelPendingSuggestions()
        return tabSessionController.createNewTab(
            initialUrl = initialUrl,
            isIncognito = isIncognito,
            stackId = stackId
        )
    }

    fun switchToTab(tabId: String) {
        searchSuggestionsController.cancelPendingSuggestions()
        tabSessionController.switchToTab(tabId)
    }

    fun closeTab(tabId: String, onTabClosed: (String) -> Unit = {}) {
        tabSessionController.closeTab(tabId = tabId, onTabClosed = onTabClosed)
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
                isThemeExtracting = if (nextIncognito) false else state.isThemeExtracting
            )
        }
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

    fun onThemeExtractionStarted(tabId: String) {
        if (_uiState.value.tabs.firstOrNull { it.id == tabId }?.isIncognito == true) return
        pageAppearanceController.onThemeExtractionStarted(tabId = tabId)
    }

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

    fun updateSiteSeedColor(tabId: String, color: Color?) {
        val resolvedColor = if (_uiState.value.tabs.firstOrNull { it.id == tabId }?.isIncognito == true) {
            null
        } else {
            color
        }
        pageAppearanceController.updateSiteSeedColor(tabId = tabId, color = resolvedColor)
    }

    fun updateTopRegionColor(tabId: String, color: Color?) =
        pageAppearanceController.updateTopRegionColor(tabId = tabId, color = color)

    fun updateBottomRegionColor(tabId: String, color: Color?) =
        pageAppearanceController.updateBottomRegionColor(tabId = tabId, color = color)

    fun navigateHome() {
        searchSuggestionsController.cancelPendingSuggestions()
        tabSessionController.navigateHome()
    }

    fun onUrlSubmitted(rawInput: String) {
        if (rawInput.trim().isEmpty()) return
        searchSuggestionsController.cancelPendingSuggestions()
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

    fun checkResourceBlocking(targetUrl: String, pageUrl: String, resourceType: String): Boolean {
        val result = shouldBlockUrlUseCase(targetUrl, pageUrl, resourceType)
        return when (result) {
            is UrlCheckResult.Block -> {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update { it.copy(blockedAdsCount = it.blockedAdsCount + 1) }
                }
                true
            }
            UrlCheckResult.Allow -> false
        }
    }
}