package com.verve.browser.domain.repository

import com.verve.browser.domain.model.AdBlockMode
import com.verve.browser.domain.model.CosmeticResources
import com.verve.browser.domain.model.UrlCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AdBlockRepository {
    val adBlockMode: StateFlow<AdBlockMode>
        get() = MutableStateFlow(AdBlockMode.STANDARD).asStateFlow()

    fun setAdBlockMode(mode: AdBlockMode) {}
    fun initializeEngine(initialRules: List<String>)
    fun prepareAsyncEngineInit() {}
    suspend fun initializeEngineAsync()
    suspend fun updateFilterListsFromNetwork(): Boolean
    fun shouldBlockUrl(
        targetUrl: String,
        pageUrl: String,
        resourceType: String,
        method: String = "GET"
    ): UrlCheckResult
    fun getCosmeticResources(pageUrl: String): CosmeticResources
    fun getHiddenClassIdSelectors(
        classes: List<String>,
        ids: List<String>,
        exceptions: List<String>
    ): List<String>
    fun isSiteWhitelisted(pageUrl: String): Boolean
    fun toggleSiteWhitelist(pageUrl: String): Boolean
}