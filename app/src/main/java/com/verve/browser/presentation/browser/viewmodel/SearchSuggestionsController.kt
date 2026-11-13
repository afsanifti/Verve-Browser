package com.verve.browser.presentation.browser.viewmodel

import com.verve.browser.presentation.browser.BrowserUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal class SearchSuggestionsController(
    private val uiState: MutableStateFlow<BrowserUiState>,
    private val scope: CoroutineScope
) {
    private var suggestionJob: Job? = null

    fun cancelPendingSuggestions() {
        suggestionJob?.cancel()
    }

    fun onSearchFocusChanged(focused: Boolean) {
        uiState.update { state ->
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
        uiState.update { it.copy(searchInput = input) }
        updateSuggestionsForQuery(input)
    }

    private fun updateSuggestionsForQuery(rawQuery: String) {
        suggestionJob?.cancel()
        val query = rawQuery.trim()
        val currentState = uiState.value

        if (query.isEmpty() || query == currentState.currentUrl) {
            uiState.update {
                it.copy(searchSuggestions = it.recentSearches.take(4))
            }
            return
        }

        val localMatches = currentState.recentSearches
            .filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
            .take(4)

        uiState.update {
            it.copy(searchSuggestions = localMatches.ifEmpty { listOf(query) })
        }

        suggestionJob = scope.launch {
            delay(160L)
            val remoteSuggestions = fetchRemoteSuggestions(query)
            val combined = (localMatches + remoteSuggestions)
                .distinctBy { it.lowercase() }
                .take(4)

            if (combined.isNotEmpty()) {
                uiState.update { it.copy(searchSuggestions = combined) }
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
}