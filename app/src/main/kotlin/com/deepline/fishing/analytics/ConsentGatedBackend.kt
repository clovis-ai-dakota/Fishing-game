package com.deepline.fishing.analytics

import com.deepline.fishing.privacy.PrivacyChoices

/**
 * Sits between [Analytics] and whatever collects ([inner]): events, screens and user properties
 * only pass while the player allows usage statistics, errors only while they allow crash
 * reports. Until [allow] is called, nothing passes.
 *
 * User properties are remembered (in memory only) so they reach the collector as soon as the
 * player says yes, without waiting for each of them to change again.
 */
class ConsentGatedBackend(@Volatile var inner: AnalyticsBackend = AnalyticsBackend.None) : AnalyticsBackend {
    @Volatile
    var analyticsAllowed = false
        private set

    @Volatile
    var crashReportsAllowed = false
        private set

    private val userProperties = LinkedHashMap<String, String?>()

    fun allow(choices: PrivacyChoices) {
        val newlyAllowed = choices.analytics && !analyticsAllowed
        analyticsAllowed = choices.analytics
        crashReportsAllowed = choices.crashReports
        if (newlyAllowed) {
            val known = synchronized(userProperties) { userProperties.toMap() }
            for ((name, value) in known) inner.setUserProperty(name, value)
        }
    }

    override fun event(name: String, params: Map<String, Any?>) {
        if (analyticsAllowed) inner.event(name, params)
    }

    override fun screen(name: String) {
        if (analyticsAllowed) inner.screen(name)
        // Where the player was is part of a crash report's context.
        if (crashReportsAllowed) (inner as? TelemetrySink)?.setCrashKey("screen", name)
    }

    override fun recordError(error: Throwable, message: String?) {
        if (crashReportsAllowed) inner.recordError(error, message)
    }

    override fun setUserProperty(name: String, value: String?) {
        synchronized(userProperties) { userProperties[name] = value }
        if (analyticsAllowed) inner.setUserProperty(name, value)
    }
}
