package com.logos.navigator.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.logos.navigator.data.model.AppLanguage

/** Применяет локаль приложения через штатный API AppCompat для Android 7+. */
object AppLocaleController {
    fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.RUSSIAN -> LocaleListCompat.forLanguageTags("ru")
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** При запуске не сбрасываем системный выбор языка, заданный пользователем в Android. */
    fun applyStoredOverride(language: AppLanguage) {
        if (language != AppLanguage.SYSTEM) apply(language)
    }
}
