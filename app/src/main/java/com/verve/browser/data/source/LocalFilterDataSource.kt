package com.verve.browser.data.source

class LocalFilterDataSource {
    fun getDefaultRules(): List<String> {
        return listOf(
            "||doubleclick.net^",
            "||google-analytics.com^",
            "||googlesyndication.com^",
            "||adservice.google.com^",
            "||quantserve.com^"
        )
    }
}