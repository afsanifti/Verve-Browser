package com.verve.browser.domain.model

sealed interface UrlCheckResult {
    data object Allow : UrlCheckResult
    data class Block(val reason: String = "Ad or tracker blocked") : UrlCheckResult
    data class Redirect(val dataUri: String) : UrlCheckResult
    data class RewriteUrl(val cleanedUrl: String) : UrlCheckResult
}

data class CosmeticResources(
    val hideSelectors: List<String> = emptyList(),
    val exceptions: List<String> = emptyList(),
    val injectedScript: String = "",
    val genericHide: Boolean = false
)