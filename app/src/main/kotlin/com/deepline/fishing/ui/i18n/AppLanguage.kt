package com.deepline.fishing.ui.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.deepline.fishing.BuildConfig
import java.util.Locale

/**
 * The languages the game speaks, as offered in Settings > Language. Each is shown in its own
 * language ([endonym], never translated). [SYSTEM] follows the phone. The pseudo-locales are
 * debug-only test languages (accented and longer English, and right-to-left), see
 * docs/localization.md. `res/xml/locales_config.xml` lists exactly the real ones (a test checks).
 */
enum class AppLanguage(val tag: String?, val endonym: String, val debugOnly: Boolean = false) {
    SYSTEM(null, ""),
    ENGLISH("en", "English"), // i18n-ignore: each language's own name
    FRENCH("fr", "Français"), // i18n-ignore
    SPANISH("es", "Español"), // i18n-ignore
    GERMAN("de", "Deutsch"), // i18n-ignore
    PORTUGUESE_BR("pt-BR", "Português (Brasil)"), // i18n-ignore
    TURKISH("tr", "Türkçe"), // i18n-ignore
    PSEUDO_ACCENTS("en-XA", "[Ëñĝļîšĥ] (en-XA)", debugOnly = true), // i18n-ignore: debug-only test language
    PSEUDO_BIDI("ar-XB", "Bidi (ar-XB)", debugOnly = true), // i18n-ignore: debug-only test language
    ;

    val locale: Locale? get() = tag?.let(Locale::forLanguageTag)

    companion object {
        /** The real languages (what the locale config lists and translators provide). */
        val supported: List<AppLanguage> get() = entries.filter { it.tag != null && !it.debugOnly }

        fun ofTag(tag: String?): AppLanguage =
            if (tag.isNullOrBlank()) SYSTEM else entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: of(Locale.forLanguageTag(tag))

        /** The offered language that best matches [locale] (same tag, else same language), or [SYSTEM]. */
        fun of(locale: Locale): AppLanguage {
            entries.firstOrNull { it.locale == locale }?.let { return it }
            if (locale.language == "pt") return PORTUGUESE_BR
            return entries.firstOrNull { it.tag != null && !it.debugOnly && it.locale?.language == locale.language } ?: SYSTEM
        }
    }
}

/**
 * Per-app language: Settings > Language in the game, and on Android 13+ also the system's
 * per-app language settings (`android:localeConfig`).
 *
 * - Android 13 and up: the system keeps the choice ([LocaleManager]); setting it there or here
 *   is the same thing, and the system recreates the activity with the new configuration.
 * - Android 12 and lower: the choice is a device preference ([KEY], like the sound settings),
 *   applied by [wrap] in `MainActivity.attachBaseContext`, and [set] recreates the activity.
 *
 * The game doesn't use AppCompat, so it calls the framework API directly (see
 * docs/research/localization.md for why).
 */
object AppLanguages {
    /** The chosen language's tag ("" for the phone's language), in the app's preferences. */
    const val KEY = "app.language"

    /** Set once the Android 12 choice has been handed to the system after an upgrade to 13+. */
    private const val KEY_HANDED_TO_SYSTEM = "app.language.handedToSystem"

    /** What Settings > Language offers: the phone's language, the real languages, and in debug builds the pseudo-locales. */
    val choices: List<AppLanguage>
        get() = AppLanguage.entries.filter { !it.debugOnly || BuildConfig.DEBUG }

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("deepline", Context.MODE_PRIVATE)

    /** The player's choice ([AppLanguage.SYSTEM] when the game follows the phone). */
    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = localeManager(context).applicationLocales
            if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.of(locales[0])
        } else {
            AppLanguage.ofTag(prefs(context).getString(KEY, null))
        }

    /**
     * Switches the game to [language]. On Android 13+ the system applies it (and recreates the
     * activity); below, the activity is recreated here.
     */
    fun set(activity: Activity, language: AppLanguage) {
        prefs(activity).edit().putString(KEY, language.tag ?: "").commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager(activity).applicationLocales = language.locale?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.recreate()
        }
    }

    /**
     * After an upgrade from Android 12 or lower, hands the choice saved here to the system once,
     * so it shows in the system settings and keeps applying.
     */
    fun handOverToSystem(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_HANDED_TO_SYSTEM, false)) return
        val saved = AppLanguage.ofTag(prefs.getString(KEY, null))
        val manager = localeManager(context)
        if (saved != AppLanguage.SYSTEM && manager.applicationLocales.isEmpty) {
            manager.applicationLocales = LocaleList(saved.locale!!)
        }
        prefs.edit().putBoolean(KEY_HANDED_TO_SYSTEM, true).apply()
    }

    /**
     * [base] showing the chosen language: used by `MainActivity.attachBaseContext`, and by code
     * that builds text outside the activity (a reminder notification) on Android 12 and lower.
     * When the resources already use that language (Android 13+, where the system applies it)
     * this returns [base] itself.
     */
    fun wrap(base: Context): Context {
        val language = current(base)
        val locale = language.locale
        if (locale == null) {
            // The phone's language: below Android 13, undo a default locale set by an earlier choice.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Locale.setDefault(Resources.getSystem().configuration.locales[0])
            return base
        }
        // Formatting (numbers, String.format) follows the game's language too.
        Locale.setDefault(locale)
        if (base.resources.configuration.locales[0] == locale) return base
        val config = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /** A context whose resources are in the game's language (for notifications and workers). */
    fun localized(context: Context): Context = wrap(context)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(context: Context): LocaleManager = context.getSystemService(LocaleManager::class.java)
}
