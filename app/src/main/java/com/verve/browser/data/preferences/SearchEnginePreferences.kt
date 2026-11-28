package com.verve.browser.data.preferences

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

data class SearchEngineOption(
    val id: String,
    val name: String,
    val shortCode: String,
    val description: String,
    val searchUrlTemplate: String,
    val homeUrl: String
) {
    fun buildQueryUrl(query: String): String {
        val encoded = Uri.encode(query.trim())
        return searchUrlTemplate.replace("{query}", encoded)
    }
}

object SearchEnginePreferences {
    private const val PREFS_NAME = "verve_search_engine_prefs"
    private const val KEY_SELECTED_ENGINE_ID = "selected_search_engine_id"

    val supportedEngines: List<SearchEngineOption> = listOf(
        SearchEngineOption(
            id = "google",
            name = "Google",
            shortCode = "GO",
            description = "google.com",
            searchUrlTemplate = "https://www.google.com/search?q={query}",
            homeUrl = "https://www.google.com"
        ),
        SearchEngineOption(
            id = "bing",
            name = "Microsoft Bing",
            shortCode = "BI",
            description = "bing.com",
            searchUrlTemplate = "https://www.bing.com/search?q={query}",
            homeUrl = "https://www.bing.com"
        ),
        SearchEngineOption(
            id = "duckduckgo",
            name = "DuckDuckGo",
            shortCode = "DD",
            description = "Privacy-first search • duckduckgo.com",
            searchUrlTemplate = "https://duckduckgo.com/?q={query}",
            homeUrl = "https://duckduckgo.com"
        ),
        SearchEngineOption(
            id = "brave",
            name = "Brave Search",
            shortCode = "BR",
            description = "Independent private index • search.brave.com",
            searchUrlTemplate = "https://search.brave.com/search?q={query}",
            homeUrl = "https://search.brave.com"
        ),
        SearchEngineOption(
            id = "startpage",
            name = "Startpage",
            shortCode = "SP",
            description = "Private Google results • startpage.com",
            searchUrlTemplate = "https://www.startpage.com/sp/search?query={query}",
            homeUrl = "https://www.startpage.com"
        ),
        SearchEngineOption(
            id = "ecosia",
            name = "Ecosia",
            shortCode = "EC",
            description = "Plants trees with searches • ecosia.org",
            searchUrlTemplate = "https://www.ecosia.org/search?q={query}",
            homeUrl = "https://www.ecosia.org"
        ),
        SearchEngineOption(
            id = "qwant",
            name = "Qwant",
            shortCode = "QW",
            description = "European private search • qwant.com",
            searchUrlTemplate = "https://www.qwant.com/?q={query}",
            homeUrl = "https://www.qwant.com"
        ),
        SearchEngineOption(
            id = "yahoo",
            name = "Yahoo!",
            shortCode = "YH",
            description = "search.yahoo.com",
            searchUrlTemplate = "https://search.yahoo.com/search?p={query}",
            homeUrl = "https://search.yahoo.com"
        ),
        SearchEngineOption(
            id = "yandex",
            name = "Yandex",
            shortCode = "YA",
            description = "yandex.com",
            searchUrlTemplate = "https://yandex.com/search/?text={query}",
            homeUrl = "https://yandex.com"
        ),
        SearchEngineOption(
            id = "kagi",
            name = "Kagi",
            shortCode = "KG",
            description = "Ad-free paid search • kagi.com",
            searchUrlTemplate = "https://kagi.com/search?q={query}",
            homeUrl = "https://kagi.com"
        )
    )

    private val defaultEngine: SearchEngineOption = supportedEngines.first()

    private val _selectedEngine = MutableStateFlow(defaultEngine)
    val selectedEngine: StateFlow<SearchEngineOption> = _selectedEngine.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedId = prefs.getString(KEY_SELECTED_ENGINE_ID, defaultEngine.id)
            _selectedEngine.value = findById(savedId)
            initialized = true
        }
    }

    fun setSearchEngine(context: Context, engine: SearchEngineOption) {
        _selectedEngine.value = engine
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_SELECTED_ENGINE_ID, engine.id)
            }
    }

    fun buildSearchUrl(query: String): String {
        return _selectedEngine.value.buildQueryUrl(query)
    }

    private fun findById(id: String?): SearchEngineOption {
        return supportedEngines.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: defaultEngine
    }
}