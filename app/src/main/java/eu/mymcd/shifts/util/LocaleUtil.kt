package eu.mymcd.shifts.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtil {

    const val LANG_AUTO = "auto"
    const val LANG_CS = "cs"
    const val LANG_EN = "en"

    /**
     * Auto resolution: system locale country/language first, then English fallback.
     * CZ (and cs language) → Czech, otherwise English.
     */
    fun resolveLanguage(pref: String, systemLocale: Locale = Locale.getDefault()): String {
        if (pref == LANG_CS || pref == LANG_EN) return pref
        val country = systemLocale.country.uppercase(Locale.ROOT)
        val lang = systemLocale.language.lowercase(Locale.ROOT)
        if (lang == "cs" || country == "CZ") return LANG_CS
        if (lang == "sk" || country == "SK") return LANG_CS
        return LANG_EN
    }

    fun localeFor(language: String): Locale =
        when (language) {
            LANG_CS -> Locale("cs", "CZ")
            else -> Locale("en", "US")
        }

    fun wrap(base: Context, language: String): Context {
        val locale = localeFor(resolveLanguage(language, base.resources.configuration.locales[0]))
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun localizedContext(base: Context, language: String): Context =
        wrap(base, language)
}
