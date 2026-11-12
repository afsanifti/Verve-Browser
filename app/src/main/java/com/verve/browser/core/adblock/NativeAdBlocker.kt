package com.verve.browser.core.adblock

import android.util.Log

class NativeAdBlocker {

    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("verve_adblock")
            isLibraryLoaded = true
            Log.d("NativeAdBlocker", "Native library verve_adblock loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeAdBlocker", "Failed to load native library verve_adblock: ${e.message}", e)
        }
    }

    fun initEngineSafe(rulesData: String): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            initEngine(rulesData)
        } catch (e: Throwable) {
            Log.e("NativeAdBlocker", "Error executing initEngine: ${e.message}", e)
            false
        }
    }

    fun shouldBlockSafe(url: String, sourceUrl: String, resourceType: String): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            shouldBlock(url, sourceUrl, resourceType)
        } catch (e: Throwable) {
            Log.e("NativeAdBlocker", "Error executing shouldBlock: ${e.message}", e)
            false
        }
    }

    private external fun initEngine(rulesData: String): Boolean
    private external fun shouldBlock(url: String, sourceUrl: String, resourceType: String): Boolean
}