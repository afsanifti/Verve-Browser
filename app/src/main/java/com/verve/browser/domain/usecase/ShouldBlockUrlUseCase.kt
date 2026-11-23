// ShouldBlockUrlUseCase.kt
package com.verve.browser.domain.usecase

import com.verve.browser.domain.model.CosmeticResources
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.repository.AdBlockRepository

class ShouldBlockUrlUseCase(
    private val repository: AdBlockRepository
) {
    operator fun invoke(
        targetUrl: String,
        pageUrl: String,
        resourceType: String,
        method: String = "GET"
    ): UrlCheckResult {
        if (repository.isSiteWhitelisted(pageUrl)) {
            return UrlCheckResult.Allow
        }
        if (resourceType.equals("main_frame", ignoreCase = true)) {
            val decision = repository.shouldBlockUrl(targetUrl, pageUrl, resourceType, method)
            return if (decision is UrlCheckResult.RewriteUrl) decision else UrlCheckResult.Allow
        }
        return repository.shouldBlockUrl(targetUrl, pageUrl, resourceType, method)
    }

    fun getCosmeticResources(pageUrl: String): CosmeticResources {
        if (repository.isSiteWhitelisted(pageUrl)) {
            return CosmeticResources()
        }
        return repository.getCosmeticResources(pageUrl)
    }

    fun getHiddenClassIdSelectors(
        pageUrl: String,
        classes: List<String>,
        ids: List<String>,
        exceptions: List<String>
    ): List<String> {
        if (repository.isSiteWhitelisted(pageUrl)) {
            return emptyList()
        }
        return repository.getHiddenClassIdSelectors(classes, ids, exceptions)
    }

    fun isSiteWhitelisted(pageUrl: String): Boolean = repository.isSiteWhitelisted(pageUrl)

    fun toggleSiteWhitelist(pageUrl: String): Boolean = repository.toggleSiteWhitelist(pageUrl)

    val adBlockMode: kotlinx.coroutines.flow.StateFlow<com.verve.browser.domain.model.AdBlockMode>
        get() = repository.adBlockMode

    fun setAdBlockMode(mode: com.verve.browser.domain.model.AdBlockMode) {
        repository.setAdBlockMode(mode)
    }
}