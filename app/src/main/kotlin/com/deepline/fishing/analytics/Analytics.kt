package com.deepline.fishing.analytics

/**
 * Product analytics and crash reports. Every feature logs its key moments here; the backend
 * (Firebase, once configured and only after the player consents) is plugged in behind it, and
 * tests can install a recording one. Until then every call does nothing.
 *
 * Naming (Firebase's limits): event and parameter names are snake_case, start with a letter and
 * are at most 40 characters; at most 25 parameters per event; string values at most 100
 * characters. Prefer Firebase's recommended game events where one fits (`level_start`,
 * `level_end`, `post_score`, `earn_virtual_currency`, `spend_virtual_currency`,
 * `tutorial_begin`, `tutorial_complete`, `unlock_achievement`, `join_group`, `select_content`).
 * Never log personal data (no recovery codes, no free text typed by the player).
 *
 * Every event is listed in `docs/analytics-events.md` (a test checks it); [AnalyticsRules] has
 * the limits. Consent is handled behind this facade ([ConsentGatedBackend], see [Telemetry]):
 * callers never check it themselves.
 */
object Analytics {
    @Volatile
    var backend: AnalyticsBackend = AnalyticsBackend.None

    fun event(name: String, params: Map<String, Any?> = emptyMap()) = backend.event(name, params)

    fun screen(name: String) = backend.screen(name)

    fun recordError(error: Throwable, message: String? = null) = backend.recordError(error, message)

    /**
     * Describes the player for segmenting (a rope band, the theme...): at most 25 names of up to
     * 24 characters, values up to 36 characters; null clears it. Never personal data.
     */
    fun setUserProperty(name: String, value: String?) = backend.setUserProperty(name, value)
}

/** Where analytics go. [None] drops everything. */
interface AnalyticsBackend {
    fun event(name: String, params: Map<String, Any?>)
    fun screen(name: String)
    fun recordError(error: Throwable, message: String?)
    fun setUserProperty(name: String, value: String?) = Unit

    object None : AnalyticsBackend {
        override fun event(name: String, params: Map<String, Any?>) = Unit
        override fun screen(name: String) = Unit
        override fun recordError(error: Throwable, message: String?) = Unit
    }
}
