package com.verve.browser.presentation.browser.util

import android.webkit.WebView

object WebThemeExtractor {
    private const val THEME_COLOR_SCRIPT = """
        (function() {
            var meta = document.querySelector('meta[name="theme-color"]');
            return meta ? meta.getAttribute('content') : '';
        })();
    """

    fun extract(webView: WebView?, onColorExtracted: (String?) -> Unit) {
        webView?.evaluateJavascript(THEME_COLOR_SCRIPT) { hexColor ->
            onColorExtracted(hexColor)
        }
    }
}