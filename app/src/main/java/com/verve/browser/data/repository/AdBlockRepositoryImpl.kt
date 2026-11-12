package com.verve.browser.data.repository

import com.verve.browser.core.adblock.NativeAdBlocker
import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.repository.AdBlockRepository

class AdBlockRepositoryImpl(
    private val nativeBlocker: NativeAdBlocker
) : AdBlockRepository {

    override fun initializeEngine(rules: List<String>) {
        val combinedRules = rules.joinToString("\n")
        nativeBlocker.initEngineSafe(combinedRules)
    }

    override fun shouldBlockUrl(targetUrl: String, pageUrl: String, resourceType: String): UrlCheckResult {
        val isBlocked = nativeBlocker.shouldBlockSafe(targetUrl, pageUrl, resourceType)
        return if (isBlocked) {
            UrlCheckResult.Block()
        } else {
            UrlCheckResult.Allow
        }
    }
}