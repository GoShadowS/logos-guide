package com.logos.navigator.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.logos.navigator.data.model.AppLanguage
import java.util.Locale

object AppLocaleController {
    fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> systemLocaleOverride()
            AppLanguage.RUSSIAN -> LocaleListCompat.forLanguageTags("ru")
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun applyStoredOverride(language: AppLanguage) {
        apply(language)
    }

    private fun systemLocaleOverride(): LocaleListCompat {
        val systemLanguage = LocaleListCompat.getDefault().get(0)?.language ?: Locale.getDefault().language
        return if (systemLanguage in SUPPORTED_LANGUAGES) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags("en")
        }
    }

    private val SUPPORTED_LANGUAGES = setOf("ru", "en")
}
