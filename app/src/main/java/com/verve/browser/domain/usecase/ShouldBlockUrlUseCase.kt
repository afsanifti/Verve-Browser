package com.verve.browser.domain.usecase

import com.verve.browser.domain.model.UrlCheckResult
import com.verve.browser.domain.repository.AdBlockRepository

class ShouldBlockUrlUseCase(
    private val repository: AdBlockRepository
) {
    operator fun invoke(targetUrl: String, pageUrl: String, resourceType: String): UrlCheckResult {
        if (resourceType.equals("main_frame", ignoreCase = true)) {
            return UrlCheckResult.Allow
        }
        return repository.shouldBlockUrl(targetUrl, pageUrl, resourceType)
    }
}