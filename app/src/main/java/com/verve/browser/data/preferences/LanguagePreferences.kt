package com.verve.browser.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.verve.browser.presentation.browser.util.PageTranslationManager
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

object LanguagePreferences {
    private const val PREFS_NAME = "verve_language_prefs"
    private const val KEY_APP_LANGUAGE = "app_language" // null = follow device language
    private const val KEY_PREFERRED_LANGUAGES = "preferred_languages"
    private const val KEY_OFFER_TRANSLATE = "offer_to_translate"
    private const val KEY_DEFAULT_LANGUAGE = "default_language"
    private const val KEY_AUTO_TRANSLATE_LANGUAGES = "auto_translate_languages"
    private const val KEY_NEVER_TRANSLATE_LANGUAGES = "never_translate_languages"

    const val FALLBACK_LANGUAGE = "English"

    private val _appLanguage = MutableStateFlow<String?>(null)
    val appLanguage: StateFlow<String?> = _appLanguage.asStateFlow()

    private val _preferredLanguages = MutableStateFlow<List<String>>(listOf(FALLBACK_LANGUAGE))
    val preferredLanguages: StateFlow<List<String>> = _preferredLanguages.asStateFlow()

    private val _offerToTranslate = MutableStateFlow(true)
    val offerToTranslate: StateFlow<Boolean> = _offerToTranslate.asStateFlow()

    private val _defaultLanguage = MutableStateFlow(FALLBACK_LANGUAGE)
    val defaultLanguage: StateFlow<String> = _defaultLanguage.asStateFlow()

    private val _autoTranslateLanguages = MutableStateFlow<List<String>>(emptyList())
    val autoTranslateLanguages: StateFlow<List<String>> = _autoTranslateLanguages.asStateFlow()

    private val _neverTranslateLanguages = MutableStateFlow<List<String>>(listOf(FALLBACK_LANGUAGE))
    val neverTranslateLanguages: StateFlow<List<String>> = _neverTranslateLanguages.asStateFlow()

    @Volatile
    private var initialized = false

    fun deviceLanguageDisplayName(): String {
        val locale = Locale.getDefault()
        val matched = PageTranslationManager.resolveLanguageDisplayName(locale.toLanguageTag())
            ?: PageTranslationManager.resolveLanguageDisplayName(locale.language)
        return matched ?: locale.displayLanguage.replaceFirstChar { it.uppercase() }.ifBlank { FALLBACK_LANGUAGE }
    }

    fun deviceLanguageWithRegionLabel(): String {
        val locale = Locale.getDefault()
        val name = locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
        return name.ifBlank { deviceLanguageDisplayName() }
    }

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val deviceLang = deviceLanguageDisplayName()

            _appLanguage.value = prefs.getString(KEY_APP_LANGUAGE, null)?.takeIf { it.isNotBlank() }

            val defaultTarget = prefs.getString(KEY_DEFAULT_LANGUAGE, null)
                ?.takeIf { it.isNotBlank() }
                ?: deviceLang
            _defaultLanguage.value = defaultTarget

            _offerToTranslate.value = prefs.getBoolean(KEY_OFFER_TRANSLATE, true)

            _preferredLanguages.value = readStringList(prefs, KEY_PREFERRED_LANGUAGES)
                ?: listOf(deviceLang)

            _autoTranslateLanguages.value = readStringList(prefs, KEY_AUTO_TRANSLATE_LANGUAGES)
                ?: emptyList()

            _neverTranslateLanguages.value = readStringList(prefs, KEY_NEVER_TRANSLATE_LANGUAGES)
                ?: listOf(defaultTarget)

            initialized = true
        }
    }

    fun setAppLanguage(context: Context, languageDisplayName: String?) {
        initialize(context)
        val normalized = languageDisplayName?.trim()?.takeIf { it.isNotBlank() }
        _appLanguage.value = normalized
        prefs(context).edit().apply {
            if (normalized == null) remove(KEY_APP_LANGUAGE) else putString(KEY_APP_LANGUAGE, normalized)
        }.apply()
    }

    fun addPreferredLanguage(context: Context, languageDisplayName: String) {
        initialize(context)
        val current = _preferredLanguages.value
        if (current.any { it.equals(languageDisplayName, ignoreCase = true) }) return
        val updated = current + languageDisplayName
        _preferredLanguages.value = updated
        writeStringList(prefs(context), KEY_PREFERRED_LANGUAGES, updated)
    }

    fun removePreferredLanguage(context: Context, languageDisplayName: String) {
        initialize(context)
        val current = _preferredLanguages.value
        if (current.size <= 1) return
        val updated = current.filterNot { it.equals(languageDisplayName, ignoreCase = true) }
        if (updated.isNotEmpty()) {
            _preferredLanguages.value = updated
            writeStringList(prefs(context), KEY_PREFERRED_LANGUAGES, updated)
        }
    }

    fun movePreferredLanguageUp(context: Context, index: Int) {
        initialize(context)
        val current = _preferredLanguages.value.toMutableList()
        if (index !in 1 until current.size) return
        val item = current.removeAt(index)
        current.add(index - 1, item)
        _preferredLanguages.value = current
        writeStringList(prefs(context), KEY_PREFERRED_LANGUAGES, current)
    }

    fun movePreferredLanguageDown(context: Context, index: Int) {
        initialize(context)
        val current = _preferredLanguages.value.toMutableList()
        if (index !in 0 until current.size - 1) return
        val item = current.removeAt(index)
        current.add(index + 1, item)
        _preferredLanguages.value = current
        writeStringList(prefs(context), KEY_PREFERRED_LANGUAGES, current)
    }

    fun setOfferToTranslate(context: Context, enabled: Boolean) {
        initialize(context)
        _offerToTranslate.value = enabled
        prefs(context).edit().putBoolean(KEY_OFFER_TRANSLATE, enabled).apply()
    }

    fun setDefaultLanguage(context: Context, languageDisplayName: String) {
        initialize(context)
        val normalized = languageDisplayName.ifBlank { FALLBACK_LANGUAGE }
        _defaultLanguage.value = normalized
        prefs(context).edit().putString(KEY_DEFAULT_LANGUAGE, normalized).apply()
    }

    fun setAutoTranslateLanguages(context: Context, languages: List<String>) {
        initialize(context)
        val distinct = languages.distinctBy { it.lowercase() }
        _autoTranslateLanguages.value = distinct
        writeStringList(prefs(context), KEY_AUTO_TRANSLATE_LANGUAGES, distinct)
        // Ensure a language isn't simultaneously in "Don't offer"
        if (distinct.isNotEmpty()) {
            val filteredNever = _neverTranslateLanguages.value.filterNot { never ->
                distinct.any { it.equals(never, ignoreCase = true) }
            }
            if (filteredNever.size != _neverTranslateLanguages.value.size) {
                _neverTranslateLanguages.value = filteredNever
                writeStringList(prefs(context), KEY_NEVER_TRANSLATE_LANGUAGES, filteredNever)
            }
        }
    }

    fun setNeverTranslateLanguages(context: Context, languages: List<String>) {
        initialize(context)
        val distinct = languages.distinctBy { it.lowercase() }
        _neverTranslateLanguages.value = distinct
        writeStringList(prefs(context), KEY_NEVER_TRANSLATE_LANGUAGES, distinct)
        // Ensure a language isn't simultaneously in "Automatically translate"
        if (distinct.isNotEmpty()) {
            val filteredAuto = _autoTranslateLanguages.value.filterNot { auto ->
                distinct.any { it.equals(auto, ignoreCase = true) }
            }
            if (filteredAuto.size != _autoTranslateLanguages.value.size) {
                _autoTranslateLanguages.value = filteredAuto
                writeStringList(prefs(context), KEY_AUTO_TRANSLATE_LANGUAGES, filteredAuto)
            }
        }
    }

    fun buildAcceptLanguageHeader(): String {
        val codes = _preferredLanguages.value
            .map { PageTranslationManager.resolveLanguageCode(it) }
            .distinct()
        if (codes.isEmpty()) return "en-US,en;q=0.9"
        return codes.mapIndexed { index, code ->
            if (index == 0) {
                code
            } else {
                val q = (1.0 - index * 0.1).coerceAtLeast(0.1)
                String.format(Locale.US, "%s;q=%.1f", code, q)
            }
        }.joinToString(",")
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readStringList(prefs: SharedPreferences, key: String): List<String>? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { idx -> array.getString(idx) }.filter { it.isNotBlank() }
        }.getOrNull()
    }

    private fun writeStringList(prefs: SharedPreferences, key: String, list: List<String>) {
        val array = JSONArray().apply { list.forEach { put(it) } }
        prefs.edit().putString(key, array.toString()).apply()
    }
}