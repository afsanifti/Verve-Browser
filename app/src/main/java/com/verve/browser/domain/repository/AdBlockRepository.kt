package com.verve.browser.domain.repository

import com.verve.browser.domain.model.UrlCheckResult

interface AdBlockRepository {
    fun initializeEngine(rules: List<String>)
    fun shouldBlockUrl(targetUrl: String, pageUrl: String, resourceType: String): UrlCheckResult
}