package com.verve.browser.presentation.browser.viewmodel

import java.net.URLEncoder

internal object UrlDestinationResolver {

    private val DOMAIN_REGEX = Regex(
        "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?$"
    )
    private val IPV4_REGEX = Regex(
        "^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$"
    )
    private val LOCALHOST_REGEX = Regex(
        "^localhost(:\\d+)?(/.*)?$",
        RegexOption.IGNORE_CASE
    )

    fun resolve(input: String): String {
        if (input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true) ||
            input.startsWith("file://", ignoreCase = true) ||
            input.startsWith("about:", ignoreCase = true)
        ) {
            return input
        }

        if (input.any { it.isWhitespace() }) {
            return buildGoogleSearchUrl(input)
        }

        if (DOMAIN_REGEX.matches(input)) {
            return "https://$input"
        }

        if (IPV4_REGEX.matches(input) || LOCALHOST_REGEX.matches(input)) {
            return "http://$input"
        }

        return buildGoogleSearchUrl(input)
    }

    private fun buildGoogleSearchUrl(query: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return "https://www.google.com/search?q=$encodedQuery"
    }
}