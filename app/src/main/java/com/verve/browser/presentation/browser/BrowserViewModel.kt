package com.verve.browser.presentation.browser

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase
import com.verve.browser.presentation.browser.viewmodel.PageAppearanceAndProgressController
import com.verve.browser.presentation.browser.viewmodel.SearchSuggestionsController
import com.verve.browser.presentation.browser.viewmodel.TabSessionController
import com.verve.browser.presentation.browser.viewmodel.ToolbarAndCurtainController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val shouldBlockUrlUseCase: ShouldBlockUrlUseCase
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

    fun openTabSwitcher() = tabSessionController.openTabSwitcher()

    fun closeTabSwitcher() = tabSessionController.closeTabSwitcher()

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

    fun onSnapshotColorReady(tabId: String, color: Color?) =
        pageAppearanceController.onSnapshotColorReady(tabId = tabId, color = color)

    fun onRevealTimeout() = pageAppearanceController.onRevealTimeout()

    fun updateSiteSeedColor(tabId: String, color: Color?) =
        pageAppearanceController.updateSiteSeedColor(tabId = tabId, color = color)

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