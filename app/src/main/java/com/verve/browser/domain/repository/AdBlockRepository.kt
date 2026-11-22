// AdBlockRepository.kt
package com.verve.browser.domain.repository

import com.verve.browser.domain.model.CosmeticResources
import com.verve.browser.domain.model.UrlCheckResult

interface AdBlockRepository {
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