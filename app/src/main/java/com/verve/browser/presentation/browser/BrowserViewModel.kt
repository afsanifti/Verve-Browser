package com.verve.browser.presentation.browser

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.usecase.ShouldBlockUrlUseCase
import com.verve.browser.presentation.browser.util.WebEdgeColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

class BrowserViewModel(
    private val shouldBlockUrlUseCase: ShouldBlockUrlUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private var suggestionJob: Job? = null

    fun onSearchFocusChanged(focused: Boolean) {
        _uiState.update { state ->
            val initialSuggestions = if (focused) {
                val query = state.searchInput.trim()
                if (query.isEmpty() || query == state.currentUrl) {
                    state.recentSearches.take(4)
                } else {
                    state.searchSuggestions
                }
            } else {
                state.searchSuggestions
            }
            state.copy(
                isSearchFocused = focused,
                searchSuggestions = initialSuggestions,
                isToolbarCollapsed = if (focused) false else state.isToolbarCollapsed,
                isToolbarManuallyCollapsed = if (focused) false else state.isToolbarManuallyCollapsed
            )
        }
    }

    fun onSearchInputChanged(input: String) {
        _uiState.update { it.copy(searchInput = input) }
        updateSuggestionsForQuery(input)
    }

    private fun updateSuggestionsForQuery(rawQuery: String) {
        suggestionJob?.cancel()
        val query = rawQuery.trim()
        val currentState = _uiState.value

        if (query.isEmpty() || query == currentState.currentUrl) {
            _uiState.update {
                it.copy(searchSuggestions = it.recentSearches.take(4))
            }
            return
        }

        val localMatches = currentState.recentSearches
            .filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
            .take(4)

        _uiState.update {
            it.copy(searchSuggestions = localMatches.ifEmpty { listOf(query) })
        }

        suggestionJob = viewModelScope.launch {
            delay(160L)
            val remoteSuggestions = fetchRemoteSuggestions(query)
            val combined = (localMatches + remoteSuggestions)
                .distinctBy { it.lowercase() }
                .take(4)

            if (combined.isNotEmpty()) {
                _uiState.update { it.copy(searchSuggestions = combined) }
            }
        }
    }

    private suspend fun fetchRemoteSuggestions(query: String): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 2500
                    readTimeout = 2500
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                }

                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val rootArray = JSONArray(body)
                        val suggestionsArray = rootArray.getJSONArray(1)
                        buildList {
                            for (i in 0 until minOf(suggestionsArray.length(), 4)) {
                                add(suggestionsArray.getString(i))
                            }
                        }
                    } else {
                        emptyList()
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
        }

    fun setToolbarCollapsed(collapsed: Boolean, isManual: Boolean = false) {
        _uiState.update { state ->
            if (state.currentUrl.isEmpty() || state.isSearchFocused) {
                if (!state.isToolbarCollapsed && !state.isToolbarManuallyCollapsed) {
                    state
                } else {
                    state.copy(
                        isToolbarCollapsed = false,
                        isToolbarManuallyCollapsed = false
                    )
                }
            } else if (isManual) {
                state.copy(
                    isToolbarCollapsed = collapsed,
                    isToolbarManuallyCollapsed = collapsed
                )
            } else {
                if (state.isToolbarManuallyCollapsed && !collapsed) {
                    state
                } else if (state.isToolbarCollapsed == collapsed) {
                    state
                } else {
                    state.copy(isToolbarCollapsed = collapsed)
                }
            }
        }
    }

    fun updateBottomCurtainProgress(progress: Float) {
        _uiState.update { state ->
            val clamped = if (state.currentUrl.isEmpty()) 0f else progress.coerceIn(0f, 1f)
            val normalized = when {
                clamped < 0.005f -> 0f
                clamped > 0.995f -> 1f
                else -> clamped
            }
            if (abs(state.bottomCurtainProgress - normalized) < 0.002f) {
                state
            } else {
                state.copy(bottomCurtainProgress = normalized)
            }
        }
    }

    fun updateHasFixedBottomBar(hasBar: Boolean) {
        _uiState.update { state ->
            val target = if (state.currentUrl.isEmpty()) false else hasBar
            if (state.hasFixedBottomBar == target) {
                state
            } else {
                state.copy(hasFixedBottomBar = target)
            }
        }
    }

    fun onPageUrlUpdated(url: String) {
        if (url.isNotBlank() && url != "about:blank") {
            _uiState.update {
                it.copy(
                    currentUrl = url,
                    searchInput = if (it.isSearchFocused) it.searchInput else url
                )
            }
        }
    }

    fun onSnapshotColorReady(color: Color?) {
        _uiState.update {
            it.copy(
                siteSeedColor = color ?: it.siteSeedColor,
                isPageReadyToReveal = true
            )
        }
    }

    fun onRevealTimeout() {
        _uiState.update { it.copy(isPageReadyToReveal = true) }
    }

    fun updateSiteSeedColor(color: Color?) {
        _uiState.update { it.copy(siteSeedColor = color) }
    }

    fun updateTopRegionColor(color: Color?) {
        if (color == null) return
        _uiState.update { state ->
            if (WebEdgeColorExtractor.isPerceptuallySimilar(state.topRegionColor, color)) {
                state
            } else {
                state.copy(topRegionColor = color)
            }
        }
    }

    fun updateBottomRegionColor(color: Color?) {
        if (color == null) return
        _uiState.update { state ->
            if (WebEdgeColorExtractor.isPerceptuallySimilar(state.bottomRegionColor, color)) {
                state
            } else {
                state.copy(bottomRegionColor = color)
            }
        }
    }

    fun navigateHome() {
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(
                currentUrl = "",
                searchInput = "",
                isSearchFocused = false,
                siteSeedColor = null,
                topRegionColor = null,
                bottomRegionColor = null,
                bottomCurtainProgress = 0f,
                hasFixedBottomBar = false,
                isPageReadyToReveal = false,
                isToolbarCollapsed = false,
                isToolbarManuallyCollapsed = false,
                canGoBack = false,
                canGoForward = false
            )
        }
    }

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

    fun onUrlSubmitted(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        suggestionJob?.cancel()
        val destinationUrl = resolveDestinationUrl(trimmed)

        _uiState.update { state ->
            val updatedRecents = (listOf(trimmed) + state.recentSearches)
                .distinctBy { it.lowercase() }
                .take(8)

            state.copy(
                currentUrl = destinationUrl,
                searchInput = destinationUrl,
                isSearchFocused = false,
                recentSearches = updatedRecents,
                searchSuggestions = updatedRecents.take(4),
                siteSeedColor = null,
                topRegionColor = null,
                bottomRegionColor = null,
                bottomCurtainProgress = 0f,
                hasFixedBottomBar = false,
                isPageReadyToReveal = false,
                isToolbarCollapsed = false,
                isToolbarManuallyCollapsed = false
            )
        }
    }

    private fun resolveDestinationUrl(input: String): String {
        if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true) ||
            input.startsWith("file://", ignoreCase = true) ||
            input.startsWith("about:", ignoreCase = true)
        ) {
            return input
        }

        if (input.any { it.isWhitespace() }) {
            return buildGoogleSearchUrl(input)
        }

        if (DOMAIN_REGEX.matches(input)) {
            return "https://$input"
        }

        if (IPV4_REGEX.matches(input) || LOCALHOST_REGEX.matches(input)) {
            return "http://$input"
        }

        return buildGoogleSearchUrl(input)
    }

    private fun buildGoogleSearchUrl(query: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return "https://www.google.com/search?q=$encodedQuery"
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update {
            it.copy(
                loadingProgress = progress,
                isLoading = progress in 1..99
            )
        }
    }

    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update {
            it.copy(canGoBack = canGoBack, canGoForward = canGoForward)
        }
    }

    companion object {
        private val DOMAIN_REGEX = Regex(
            "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?$"
        )
        private val IPV4_REGEX = Regex(
            "^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$"
        )
        private val LOCALHOST_REGEX = Regex(
            "^localhost(:\\d+)?(/.*)?$",
            RegexOption.IGNORE_CASE
        )
    }
}