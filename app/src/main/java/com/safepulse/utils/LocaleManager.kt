package com.safepulse.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

data class AppLanguage(
    val code: String,
    val label: String
)

object LocaleManager {
    const val SYSTEM_LANGUAGE_CODE = "system"

    val supportedLanguages: List<AppLanguage> = listOf(
        AppLanguage(SYSTEM_LANGUAGE_CODE, "System default"),
        AppLanguage("en", "English"),
        AppLanguage("hi", "Hindi - हिन्दी"),
        AppLanguage("mr", "Marathi - मराठी"),
        AppLanguage("bn", "Bengali - বাংলা"),
        AppLanguage("ta", "Tamil - தமிழ்"),
        AppLanguage("te", "Telugu - తెలుగు"),
        AppLanguage("kn", "Kannada - ಕನ್ನಡ"),
        AppLanguage("gu", "Gujarati - ગુજરાતી"),
        AppLanguage("pa", "Punjabi - ਪੰਜਾਬੀ"),
        AppLanguage("ml", "Malayalam - മലയാളം"),
        AppLanguage("or", "Odia - ଓଡ଼ିଆ")
    )

    fun isSupported(languageCode: String): Boolean {
        return supportedLanguages.any { it.code == languageCode }
    }

    fun normalize(languageCode: String): String {
        return if (isSupported(languageCode)) languageCode else SYSTEM_LANGUAGE_CODE
    }

    fun applyLanguage(languageCode: String) {
        val normalized = normalize(languageCode)
        val locales = if (normalized == SYSTEM_LANGUAGE_CODE) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(normalized)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun hasActiveAppLocale(): Boolean {
        return !AppCompatDelegate.getApplicationLocales().isEmpty
    }
}
