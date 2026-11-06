package com.deepline.fishing.analytics

import com.deepline.fishing.privacy.PrivacyChoices
import java.util.Collections

/**
 * Stands in for Firebase in tests: records everything that reaches the collector (i.e. what
 * got past the consent gate), and the consent the SDK was told about.
 */
class RecordingTelemetry : TelemetrySink {
    data class Event(val name: String, val params: Map<String, Any?>)

    val events: MutableList<Event> = Collections.synchronizedList(ArrayList())
    val screens: MutableList<String> = Collections.synchronizedList(ArrayList())
    val errors: MutableList<Pair<Throwable, String?>> = Collections.synchronizedList(ArrayList())
    val userProperties: MutableMap<String, String?> = Collections.synchronizedMap(LinkedHashMap())
    val crashKeys: MutableMap<String, String> = Collections.synchronizedMap(LinkedHashMap())
    val consents: MutableList<PrivacyChoices> = Collections.synchronizedList(ArrayList())

    @Volatile
    var resets = 0
        private set

    fun names(): List<String> = synchronized(events) { events.map { it.name } }

    fun last(name: String): Event? = synchronized(events) { events.lastOrNull { it.name == name } }

    /** Nothing at all reached the collector. */
    val isEmpty: Boolean get() = events.isEmpty() && screens.isEmpty() && errors.isEmpty() && userProperties.isEmpty() && crashKeys.isEmpty()

    /** Everything recorded that Firebase would refuse or cut. */
    fun problems(): List<String> = synchronized(events) {
        events.flatMap { AnalyticsRules.eventProblems(it.name, it.params) } +
            userProperties.flatMap { (k, v) -> AnalyticsRules.userPropertyProblems(k, v) } +
            screens.filter { it.length > AnalyticsRules.MAX_STRING_VALUE }.map { "screen name too long: $it" }
    }

    override fun applyConsent(choices: PrivacyChoices) {
        consents += choices
    }

    override fun resetAnalyticsData() {
        resets++
    }

    override fun event(name: String, params: Map<String, Any?>) {
        events += Event(name, params)
    }

    override fun screen(name: String) {
        screens += name
    }

    override fun recordError(error: Throwable, message: String?) {
        errors += error to message
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
    }

    override fun setCrashKey(key: String, value: String) {
        crashKeys[key] = value
    }
}
