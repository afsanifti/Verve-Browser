package com.verve.browser.presentation.browser.viewmodel

import com.verve.browser.data.preferences.SearchEnginePreferences
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
        suggestionJob = null
    }

    fun onSearchFocusChanged(focused: Boolean) {
        if (uiState.value.isIncognito) {
            cancelPendingSuggestions()
            uiState.update { state ->
                state.copy(
                    isSearchFocused = focused,
                    searchSuggestions = emptyList(),
                    isToolbarCollapsed = if (focused) false else state.isToolbarCollapsed,
                    isToolbarManuallyCollapsed = if (focused) false else state.isToolbarManuallyCollapsed
                )
            }
            return
        }

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
        if (uiState.value.isIncognito) {
            cancelPendingSuggestions()
            uiState.update {
                it.copy(
                    searchInput = input,
                    searchSuggestions = emptyList()
                )
            }
            return
        }

        uiState.update { it.copy(searchInput = input) }
        updateSuggestionsForQuery(input)
    }

    private fun updateSuggestionsForQuery(rawQuery: String) {
        cancelPendingSuggestions()
        val currentState = uiState.value

        if (currentState.isIncognito) {
            uiState.update { it.copy(searchSuggestions = emptyList()) }
            return
        }

        val query = rawQuery.trim()
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
            if (uiState.value.isIncognito) return@launch
            val remoteSuggestions = fetchRemoteSuggestions(query)
            if (uiState.value.isIncognito) return@launch

            val combined = (localMatches + remoteSuggestions)
                .distinctBy { it.lowercase() }
                .take(4)

            if (combined.isNotEmpty() && !uiState.value.isIncognito) {
                uiState.update { it.copy(searchSuggestions = combined) }
            }
        }
    }

    private suspend fun fetchRemoteSuggestions(query: String): List<String> =
        withContext(Dispatchers.IO) {
            if (uiState.value.isIncognito) return@withContext emptyList()

            runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val suggestUrl = buildSuggestionUrlForActiveEngine(encoded) ?: return@withContext emptyList()
                val url = URL(suggestUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 2500
                    readTimeout = 2500
                    useCaches = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                }

                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        parseOpenSearchSuggestions(body)
                    } else {
                        emptyList()
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
        }

    private fun buildSuggestionUrlForActiveEngine(encodedQuery: String): String? {
        return when (SearchEnginePreferences.selectedEngine.value.id.lowercase()) {
            "google" -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$encodedQuery"
            "bing" -> "https://api.bing.com/osjson.aspx?query=$encodedQuery"
            "duckduckgo" -> "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
            "brave" -> "https://search.brave.com/api/suggest?q=$encodedQuery"
            "startpage" -> "https://www.startpage.com/osuggestions?q=$encodedQuery"
            "ecosia" -> "https://ac.ecosia.org/autocomplete?q=$encodedQuery&type=list"
            "qwant" -> "https://api.qwant.com/v3/suggest?q=$encodedQuery"
            "yahoo" -> "https://search.yahoo.com/sugg/os?command=$encodedQuery&output=fxjson"
            "yandex" -> "https://suggest.yandex.com/suggest-ff.cgi?part=$encodedQuery"
            "kagi" -> "https://kagi.com/api/autosuggest?q=$encodedQuery"
            else -> null
        }
    }

    private fun parseOpenSearchSuggestions(body: String): List<String> {
        val rootArray = JSONArray(body)
        val suggestionsArray = rootArray.optJSONArray(1) ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(suggestionsArray.length(), 4)) {
                val item = suggestionsArray.optString(i).trim()
                if (item.isNotEmpty()) add(item)
            }
        }
    }
}