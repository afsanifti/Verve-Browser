package com.verve.browser.domain.model

enum class AdBlockMode {
    STANDARD,
    AGGRESSIVE;

    companion object {
        fun fromStorage(raw: String?): AdBlockMode {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STANDARD
        }
    }
}