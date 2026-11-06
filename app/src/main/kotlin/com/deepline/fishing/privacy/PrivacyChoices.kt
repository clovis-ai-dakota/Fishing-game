package com.deepline.fishing.privacy

import com.deepline.fishing.core.KeyValueStore

/**
 * What the player allows the game to send. Both are off until the player says yes: nothing is
 * pre-ticked, and refusing is one tap like accepting (see docs/research/analytics-privacy.md).
 */
data class PrivacyChoices(
    /** Usage statistics (Google Analytics for Firebase). */
    val analytics: Boolean = false,
    /** Crash reports and caught errors (Firebase Crashlytics). */
    val crashReports: Boolean = false,
) {
    val any: Boolean get() = analytics || crashReports

    companion object {
        val NONE = PrivacyChoices()
        val ALL = PrivacyChoices(analytics = true, crashReports = true)
    }
}

/**
 * The player's answer, kept as a device preference (in the app's own key-value store, never in
 * the game's progress, cloud saves or anything sent to a server).
 */
class ConsentStore(private val store: KeyValueStore, private val clock: () -> Long = System::currentTimeMillis) {
    /** The answer, or null while the current question ([CONSENT_VERSION]) hasn't been answered. */
    fun load(): PrivacyChoices? {
        val version = store.getString(KEY_VERSION)?.toIntOrNull() ?: return null
        if (version < CONSENT_VERSION) return null
        return PrivacyChoices(
            analytics = store.getString(KEY_ANALYTICS) == "true",
            crashReports = store.getString(KEY_CRASH_REPORTS) == "true",
        )
    }

    fun save(choices: PrivacyChoices) {
        store.putString(KEY_ANALYTICS, choices.analytics.toString())
        store.putString(KEY_CRASH_REPORTS, choices.crashReports.toString())
        // When the player chose (proof of consent, kept on the device only).
        store.putString(KEY_ANSWERED_AT, clock().toString())
        store.putString(KEY_VERSION, CONSENT_VERSION.toString())
    }

    companion object {
        /**
         * Bump this when the consent sheet asks about something new (another purpose, another
         * company receiving data): every player is then asked again, with both choices off.
         */
        const val CONSENT_VERSION = 1

        const val KEY_VERSION = "privacy.consentVersion"
        const val KEY_ANALYTICS = "privacy.analytics"
        const val KEY_CRASH_REPORTS = "privacy.crashReports"
        const val KEY_ANSWERED_AT = "privacy.answeredAtMs"
    }
}
