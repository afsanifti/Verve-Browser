package com.verve.browser.domain.model

sealed interface UrlCheckResult {
    data object Allow : UrlCheckResult
    data class Block(val reason: String = "Ad or tracker blocked") : UrlCheckResult
}