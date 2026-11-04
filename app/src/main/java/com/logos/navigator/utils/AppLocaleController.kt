package com.logos.navigator.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
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

private var cachedLocalizedContext: Pair<String, Context>? = null

/**
 * Application resources are not updated by [AppCompatDelegate.setApplicationLocales] before Android 13,
 * so strings produced by ViewModels would stay in the previous language. This returns a context whose
 * configuration follows the per-app language on every API level.
 */
fun Context.localizedContext(): Context {
    val base = applicationContext ?: this
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return base
    val tags = locales.toLanguageTags()
    cachedLocalizedContext?.let { (cachedTags, context) -> if (cachedTags == tags) return context }
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocales(LocaleList.forLanguageTags(tags))
    val localized = base.createConfigurationContext(configuration)
    cachedLocalizedContext = tags to localized
    return localized
}
