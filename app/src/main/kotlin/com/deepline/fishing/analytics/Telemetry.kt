package com.deepline.fishing.analytics

import android.content.Context
import android.os.Build
import com.deepline.fishing.SharedPrefsStore
import com.deepline.fishing.privacy.ConsentStore
import com.deepline.fishing.privacy.PrivacyChoices

/**
 * What actually collects: Firebase in the app ([FirebaseTelemetry]), a recorder in tests. It is
 * only reached through [Telemetry] and the [ConsentGatedBackend].
 */
interface TelemetrySink : AnalyticsBackend {
    /**
     * Switches the SDK's own collection on or off to match [choices] (including Consent Mode).
     * Called at start-up with the stored answer and every time the player changes it.
     */
    fun applyConsent(choices: PrivacyChoices)

    /** Clears the analytics data kept on this device and starts a new random app instance id. */
    fun resetAnalyticsData()

    /** Context attached to the next crash reports (the current screen). */
    fun setCrashKey(key: String, value: String) = Unit
}

/**
 * Wires analytics and crash reports to the player's privacy choices.
 *
 * - [start] (from `MainActivity`) installs Firebase when this build has a `google-services.json`;
 *   otherwise nothing is installed, [available] stays false and no consent is asked for.
 * - Nothing collects before the player answers: the [gate] drops everything and Firebase isn't
 *   even started (see the manifest) until the first "yes".
 * - [applyChoices] follows every answer: what is withdrawn is closed first, then the SDK is told,
 *   then what is granted opens. Withdrawing usage statistics also clears the analytics data on
 *   the device.
 *
 * Unit tests (Robolectric) never start Firebase: they [install] a recording sink when they need one.
 */
object Telemetry {
    /** True when something can be collected in this build: only then is the player asked. */
    @Volatile
    var available = false
        private set

    val gate = ConsentGatedBackend()

    private var sink: TelemetrySink? = null
    private var started = false

    /** Starts telemetry once per process. Safe to call again (activity recreated). */
    fun start(context: Context) {
        if (started) return
        started = true
        // Robolectric (the UI tests) installs its own sink when a test needs one.
        if (Build.FINGERPRINT == "robolectric") return
        val app = context.applicationContext
        if (!FirebaseTelemetry.isConfigured(app)) return
        install(FirebaseTelemetry(app))
        // Crash reports start as early as possible for players who allowed them.
        applyChoices(ConsentStore(SharedPrefsStore(app)).load())
    }

    /** Makes [sink] the collector behind [Analytics] (the app's Firebase, or a test recorder). */
    fun install(sink: TelemetrySink) {
        this.sink = sink
        gate.allow(PrivacyChoices.NONE)
        gate.inner = sink
        available = true
        Analytics.backend = gate
    }

    /** Follows the player's answer; null (not answered yet) means nothing is allowed. */
    fun applyChoices(choices: PrivacyChoices?) {
        val s = sink ?: return
        val c = choices ?: PrivacyChoices.NONE
        val hadAnalytics = gate.analyticsAllowed
        // Close what is withdrawn before the SDK hears about it, open what is granted after.
        gate.allow(
            PrivacyChoices(
                analytics = c.analytics && gate.analyticsAllowed,
                crashReports = c.crashReports && gate.crashReportsAllowed,
            ),
        )
        s.applyConsent(c)
        if (hadAnalytics && !c.analytics) s.resetAnalyticsData()
        gate.allow(c)
    }

    /** Settings > Privacy: forget the analytics data on this device and get a new random id. */
    fun resetAnalyticsData() {
        sink?.resetAnalyticsData()
    }

    /** Tests: back to a build that collects nothing. */
    fun uninstall() {
        gate.allow(PrivacyChoices.NONE)
        gate.inner = AnalyticsBackend.None
        sink = null
        available = false
        if (Analytics.backend === gate) Analytics.backend = AnalyticsBackend.None
    }
}
