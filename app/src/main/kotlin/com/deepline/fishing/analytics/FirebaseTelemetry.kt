package com.deepline.fishing.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.deepline.fishing.privacy.PrivacyChoices
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Google Analytics for Firebase and Crashlytics, behind the [ConsentGatedBackend].
 *
 * Firebase is started on the player's first "yes" (the manifest removes its auto-start), so a
 * player who never agrees never runs any Firebase code. Once started, each answer is applied
 * three ways: Consent Mode v2 (analytics storage granted or denied; ads always denied: the game
 * has no ads), Analytics collection on or off, Crashlytics collection on or off (refused crash
 * reports kept on the device are deleted, never sent).
 */
class FirebaseTelemetry(private val context: Context) : TelemetrySink {
    private var app: FirebaseApp? = null

    private fun started(): Boolean {
        if (app == null) {
            app = try {
                FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
            } catch (e: Exception) {
                Log.w(TAG, "Firebase didn't start", e)
                null
            }
        }
        return app != null
    }

    private val analytics: FirebaseAnalytics? get() = if (app != null) FirebaseAnalytics.getInstance(context) else null
    private val crashlytics: FirebaseCrashlytics? get() = if (app != null) FirebaseCrashlytics.getInstance() else null

    override fun applyConsent(choices: PrivacyChoices) {
        // Never start Firebase just to say no.
        if (app == null && !choices.any) return
        if (!started()) return
        analytics?.apply {
            val analyticsStatus = if (choices.analytics) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
            setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to analyticsStatus,
                    FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
                ),
            )
            setAnalyticsCollectionEnabled(choices.analytics)
        }
        crashlytics?.apply {
            setCrashlyticsCollectionEnabled(choices.crashReports)
            if (!choices.crashReports) deleteUnsentReports()
        }
    }

    override fun resetAnalyticsData() {
        analytics?.resetAnalyticsData()
    }

    override fun event(name: String, params: Map<String, Any?>) {
        analytics?.logEvent(name, bundleOf(params))
    }

    override fun screen(name: String) {
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            bundleOf(mapOf(FirebaseAnalytics.Param.SCREEN_NAME to name, FirebaseAnalytics.Param.SCREEN_CLASS to "MainActivity")),
        )
    }

    override fun recordError(error: Throwable, message: String?) {
        crashlytics?.apply {
            message?.let(::log)
            recordException(error)
        }
    }

    override fun setUserProperty(name: String, value: String?) {
        analytics?.setUserProperty(name, value)
    }

    override fun setCrashKey(key: String, value: String) {
        crashlytics?.setCustomKey(key, value)
    }

    companion object {
        private const val TAG = "DeepLineTelemetry"

        /** Whether this build has a Firebase config (from app/google-services.json). */
        fun isConfigured(context: Context): Boolean = FirebaseOptions.fromResource(context) != null

        /** Firebase takes text, whole numbers and decimals; true and false become 1 and 0. */
        fun bundleOf(params: Map<String, Any?>): Bundle = Bundle().apply {
            for ((key, value) in params) {
                when (value) {
                    null -> Unit
                    is String -> putString(key, value.take(AnalyticsRules.MAX_STRING_VALUE))
                    is Boolean -> putLong(key, if (value) 1L else 0L)
                    is Int -> putLong(key, value.toLong())
                    is Long -> putLong(key, value)
                    is Float -> putDouble(key, value.toDouble())
                    is Double -> putDouble(key, value)
                    else -> putString(key, value.toString().take(AnalyticsRules.MAX_STRING_VALUE))
                }
            }
        }
    }
}
