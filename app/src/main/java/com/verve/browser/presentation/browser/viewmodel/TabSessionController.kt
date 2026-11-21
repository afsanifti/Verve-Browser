package com.verve.browser.presentation.browser.viewmodel

import androidx.compose.ui.graphics.Color
import com.verve.browser.presentation.browser.BrowserTab
import com.verve.browser.presentation.browser.BrowserUiState
import com.verve.browser.presentation.browser.TabStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

internal class TabSessionController(
    private val uiState: MutableStateFlow<BrowserUiState>
) {

    fun openTabSwitcher() {
        uiState.update {
            it.copy(
                isTabSwitcherOpen = true,
                isSearchFocused = false
            )
        }
    }

    fun closeTabSwitcher() {
        uiState.update {
            it.copy(isTabSwitcherOpen = false)
        }
    }

    fun createNewTab(
        initialUrl: String = "",
        isIncognito: Boolean = false,
        stackId: String? = null
    ): String {
        val newTabId = "tab_${UUID.randomUUID()}"
        val resolvedUrl = if (initialUrl.isBlank()) "" else UrlDestinationResolver.resolve(initialUrl)

        uiState.update { state ->
            val validStackId = if (!isIncognito && stackId != null && state.tabStacks.any { it.id == stackId }) {
                stackId
            } else {
                null
            }
            val newTab = BrowserTab(
                id = newTabId,
                url = resolvedUrl,
                isIncognito = isIncognito,
                stackId = validStackId,
                isMainFrameLoading = resolvedUrl.isNotBlank(),
                isLoading = resolvedUrl.isNotBlank()
            )
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTabId,
                currentUrl = resolvedUrl,
                searchInput = resolvedUrl,
                isSearchFocused = resolvedUrl.isBlank(),
                isTabSwitcherOpen = false,
                siteSeedColor = null,
                pendingSiteSeedColor = null,
                isThemeExtracting = resolvedUrl.isNotBlank() && !isIncognito,
                topRegionColor = null,
                bottomRegionColor = null,
                bottomCurtainProgress = 0f,
                hasFixedBottomBar = false,
                isPageReadyToReveal = false,
                isToolbarCollapsed = false,
                isToolbarManuallyCollapsed = false,
                isLoading = resolvedUrl.isNotBlank(),
                loadingProgress = 0,
                canGoBack = false,
                canGoForward = false
            )
        }
        return newTabId
    }

    fun switchToTab(tabId: String) {
        uiState.update { state ->
            val target = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            val now = System.currentTimeMillis()
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(lastAccessedAt = now) else tab
            }
            val effectiveSeedColor = if (target.isIncognito) null else target.siteSeedColor

            state.copy(
                tabs = updatedTabs,
                activeTabId = target.id,
                currentUrl = target.url,
                searchInput = target.url,
                isSearchFocused = false,
                isTabSwitcherOpen = false,
                siteSeedColor = effectiveSeedColor,
                pendingSiteSeedColor = effectiveSeedColor,
                isThemeExtracting = false,
                topRegionColor = target.topRegionColor,
                bottomRegionColor = target.bottomRegionColor,
                bottomCurtainProgress = 0f,
                hasFixedBottomBar = false,
                isToolbarCollapsed = false,
                isToolbarManuallyCollapsed = false,
                isLoading = target.isLoading,
                loadingProgress = target.loadingProgress,
                canGoBack = target.canGoBack,
                canGoForward = target.canGoForward
            )
        }
    }

    fun closeTab(tabId: String, onTabClosed: (String) -> Unit = {}) {
        uiState.update { state ->
            val remainingTabs = state.tabs.filterNot { it.id == tabId }
            val nextTabs = remainingTabs.ifEmpty {
                listOf(BrowserTab(id = BrowserUiState.INITIAL_TAB_ID))
            }

            // Dissolve stacks that drop below 2 tabs
            val tabCountsByStack = nextTabs.mapNotNull { it.stackId }.groupingBy { it }.eachCount()
            val validStackIds = tabCountsByStack.filterValues { it >= 2 }.keys
            val cleanedTabs = nextTabs.map { tab ->
                if (tab.stackId != null && tab.stackId !in validStackIds) {
                    tab.copy(stackId = null)
                } else {
                    tab
                }
            }
            val cleanedStacks = state.tabStacks.filter { it.id in validStackIds }

            val nextActiveTab = if (state.activeTabId == tabId) {
                cleanedTabs.maxByOrNull { it.lastAccessedAt } ?: cleanedTabs.first()
            } else {
                cleanedTabs.firstOrNull { it.id == state.activeTabId } ?: cleanedTabs.first()
            }

            val effectiveSeedColor = if (nextActiveTab.isIncognito) null else nextActiveTab.siteSeedColor

            state.copy(
                tabs = cleanedTabs,
                tabStacks = cleanedStacks,
                activeTabId = nextActiveTab.id,
                currentUrl = nextActiveTab.url,
                searchInput = nextActiveTab.url,
                siteSeedColor = effectiveSeedColor,
                pendingSiteSeedColor = effectiveSeedColor,
                topRegionColor = nextActiveTab.topRegionColor,
                bottomRegionColor = nextActiveTab.bottomRegionColor,
                isLoading = nextActiveTab.isLoading,
                loadingProgress = nextActiveTab.loadingProgress,
                canGoBack = nextActiveTab.canGoBack,
                canGoForward = nextActiveTab.canGoForward
            )
        }
        onTabClosed(tabId)
    }

    fun createTabStack(
        tabIds: List<String>,
        title: String,
        seedColor: Color? = null
    ): String? {
        val uniqueIds = tabIds.distinct()
        if (uniqueIds.size < 2) return null

        val newStackId = "stack_${UUID.randomUUID()}"
        uiState.update { state ->
            val eligibleIds = state.tabs
                .filter { it.id in uniqueIds && !it.isIncognito }
                .map { it.id }
                .toSet()

            if (eligibleIds.size < 2) return@update state

            val resolvedTitle = title.trim().ifBlank { "Tab Stack" }
            val resolvedColor = seedColor
                ?: state.tabs.firstOrNull { it.id in eligibleIds }?.siteSeedColor

            val newStack = TabStack(
                id = newStackId,
                title = resolvedTitle,
                seedColor = resolvedColor
            )
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id in eligibleIds) tab.copy(stackId = newStackId) else tab
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
            val normalizedStacks = (state.tabStacks + newStack).filter { it.id in validStackIds }

            state.copy(
                tabs = normalizedTabs,
                tabStacks = normalizedStacks
            )
        }
        return newStackId
    }

    fun addTabToStack(tabId: String, stackId: String) {
        uiState.update { state ->
            if (state.tabStacks.none { it.id == stackId }) return@update state
            val targetTab = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            if (targetTab.isIncognito) return@update state

            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(stackId = stackId) else tab
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun removeTabFromStack(tabId: String) {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(stackId = null) else tab
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
                tabStacks = normalizedStacks
            )
        }
    }

    fun toggleStackCollapsed(stackId: String) {
        uiState.update { state ->
            state.copy(
                tabStacks = state.tabStacks.map { stack ->
                    if (stack.id == stackId) stack.copy(isCollapsed = !stack.isCollapsed) else stack
                }
            )
        }
    }

    fun openInlinePdf(tabId: String, pdfUriString: String, pdfTitle: String) {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        activePdfUriString = pdfUriString,
                        activePdfTitle = pdfTitle,
                        isMainFrameLoading = false,
                        isLoading = false,
                        loadingProgress = 100
                    )
                } else {
                    tab
                }
            }
            if (state.activeTabId == tabId) {
                state.copy(
                    tabs = updatedTabs,
                    isSearchFocused = false,
                    isTabSwitcherOpen = false,
                    isToolbarCollapsed = false,
                    isToolbarManuallyCollapsed = false,
                    isLoading = false,
                    loadingProgress = 100
                )
            } else {
                state.copy(tabs = updatedTabs)
            }
        }
    }

    fun closeInlinePdf(tabId: String) {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        activePdfUriString = null,
                        activePdfTitle = null
                    )
                } else {
                    tab
                }
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun navigateHome() {
        uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) {
                    tab.copy(
                        url = "",
                        title = "",
                        siteSeedColor = null,
                        topRegionColor = null,
                        bottomRegionColor = null,
                        isMainFrameLoading = false,
                        isLoading = false,
                        loadingProgress = 0,
                        canGoBack = false,
                        canGoForward = false,
                        activePdfUriString = null,
                        activePdfTitle = null
                    )
                } else {
                    tab
                }
            }
            state.copy(
                tabs = updatedTabs,
                currentUrl = "",
                searchInput = "",
                isSearchFocused = false,
                isTabSwitcherOpen = false,
                siteSeedColor = null,
                pendingSiteSeedColor = null,
                topRegionColor = null,
                bottomRegionColor = null,
                bottomCurtainProgress = 0f,
                hasFixedBottomBar = false,
                isPageReadyToReveal = false,
                isToolbarCollapsed = false,
                isToolbarManuallyCollapsed = false,
                isLoading = false,
                loadingProgress = 0,
                canGoBack = false,
                canGoForward = false
            )
        }
    }

    fun onUrlSubmitted(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val destinationUrl = UrlDestinationResolver.resolve(trimmed)

        uiState.update { state ->
            val isIncognito = state.isIncognito

            val updatedRecents = if (isIncognito) {
                state.recentSearches
            } else {
                (listOf(trimmed) + state.recentSearches)
                    .distinctBy { it.lowercase() }
                    .take(8)
            }

            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) {
                    tab.copy(
                        url = destinationUrl,
                        title = "",
                        siteSeedColor = if (isIncognito) null else tab.siteSeedColor,
                        isMainFrameLoading = true,
                        isLoading = true,
                        loadingProgress = 0,
                        lastAccessedAt = System.currentTimeMillis(),
                        activePdfUriString = null,
                        activePdfTitle = null
                    )
                } else {
                    tab
                }
            }

            state.copy(
                tabs = updatedTabs,
                currentUrl = destinationUrl,
                searchInput = destinationUrl,
                isSearchFocused = false,
                isTabSwitcherOpen = false,
                recentSearches = updatedRecents,
                searchSuggestions = if (isIncognito) emptyList() else updatedRecents.take(4),
                siteSeedColor = if (isIncognito) null else state.siteSeedColor,
                pendingSiteSeedColor = null,
                isThemeExtracting = !isIncognito,
                bottomCurtainProgress = 0f,
                hasFixedBottomBar = false,
                isPageReadyToReveal = false,
                isToolbarCollapsed = false,
                isToolbarManuallyCollapsed = false,
                isLoading = true,
                loadingProgress = 0
            )
        }
    }
}