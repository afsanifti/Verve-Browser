package com.verve.browser.core.adblock

import androidx.annotation.Keep

@Keep
class NativeAdBlocker {

    companion object {
        @Volatile
        private var libraryLoaded = false

        @Volatile
        private var v2SymbolsAvailable = true

        init {
            libraryLoaded = runCatching {
                System.loadLibrary("verve_adblock")
                true
            }.getOrDefault(false)
        }
    }

    val isLibraryLoaded: Boolean
        get() = libraryLoaded

    val supportsV2Engine: Boolean
        get() = libraryLoaded && v2SymbolsAvailable

    external fun initEngine(rules: String): Boolean

    external fun initEngineFromLists(rules: String, resourcesJson: String): ByteArray?

    external fun loadSerializedEngine(serializedData: ByteArray, resourcesJson: String): Boolean

    external fun shouldBlock(url: String, sourceUrl: String, resourceType: String): Boolean

    external fun evaluateRequest(
        url: String,
        sourceUrl: String,
        resourceType: String,
        method: String
    ): Array<String>?

    external fun getCosmeticResources(url: String): String?

    external fun getHiddenClassIdSelectors(
        classes: Array<String>,
        ids: Array<String>,
        exceptions: Array<String>
    ): Array<String>?

    fun safeInitEngineFromLists(rules: String, resourcesJson: String): ByteArray? {
        if (!libraryLoaded) return null
        if (v2SymbolsAvailable) {
            try {
                return initEngineFromLists(rules, resourcesJson)
            } catch (_: UnsatisfiedLinkError) {
                v2SymbolsAvailable = false
            }
        }
        initEngine(rules)
        return null
    }

    fun safeLoadSerializedEngine(serializedData: ByteArray, resourcesJson: String): Boolean {
        if (!libraryLoaded || !v2SymbolsAvailable) return false
        return try {
            loadSerializedEngine(serializedData, resourcesJson)
        } catch (_: UnsatisfiedLinkError) {
            v2SymbolsAvailable = false
            false
        }
    }

    fun safeEvaluateRequest(
        url: String,
        sourceUrl: String,
        resourceType: String,
        method: String
    ): Array<String>? {
        if (!libraryLoaded) return null
        if (v2SymbolsAvailable) {
            try {
                return evaluateRequest(url, sourceUrl, resourceType, method)
            } catch (_: UnsatisfiedLinkError) {
                v2SymbolsAvailable = false
            }
        }
        val blocked = try {
            shouldBlock(url, sourceUrl, resourceType)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
        return if (blocked) arrayOf("BLOCK", "", "") else arrayOf("ALLOW", "", "")
    }

    fun safeGetCosmeticResources(url: String): String? {
        if (!libraryLoaded || !v2SymbolsAvailable) return null
        return try {
            getCosmeticResources(url)
        } catch (_: UnsatisfiedLinkError) {
            v2SymbolsAvailable = false
            null
        }
    }

    fun safeGetHiddenClassIdSelectors(
        classes: Array<String>,
        ids: Array<String>,
        exceptions: Array<String>
    ): Array<String>? {
        if (!libraryLoaded || !v2SymbolsAvailable) return null
        return try {
            getHiddenClassIdSelectors(classes, ids, exceptions)
        } catch (_: UnsatisfiedLinkError) {
            v2SymbolsAvailable = false
            null
        }
    }

    fun initEngineSafe(rules: String): Boolean {
        if (!libraryLoaded) return false
        return try {
            initEngine(rules)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun shouldBlockSafe(url: String, sourceUrl: String, resourceType: String): Boolean {
        if (!libraryLoaded) return false
        return try {
            shouldBlock(url, sourceUrl, resourceType)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }
}