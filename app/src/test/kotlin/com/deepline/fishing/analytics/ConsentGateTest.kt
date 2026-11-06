package com.deepline.fishing.analytics

import com.deepline.fishing.core.KeyValueStore
import com.deepline.fishing.privacy.ConsentStore
import com.deepline.fishing.privacy.PrivacyChoices
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nothing is sent before the player agrees, nor after they change their mind. */
class ConsentGateTest {
    private val sink = RecordingTelemetry()

    @After
    fun tearDown() = Telemetry.uninstall()

    private fun sendOneOfEach() {
        Analytics.event("level_start", mapOf("level_name" to "solo"))
        Analytics.screen("fish")
        Analytics.recordError(IllegalStateException("boom"), "test")
        Analytics.setUserProperty("rope_band", "1-24")
    }

    @Test
    fun nothingPassesBeforeAnAnswer() {
        Telemetry.install(sink)
        // A player who hasn't answered: the SDK is told "nothing", the gate drops everything.
        Telemetry.applyChoices(null)
        sendOneOfEach()
        assertTrue(sink.isEmpty)
        assertEquals(listOf(PrivacyChoices.NONE), sink.consents)
    }

    @Test
    fun refusingKeepsEverythingOut() {
        Telemetry.install(sink)
        Telemetry.applyChoices(PrivacyChoices.NONE)
        sendOneOfEach()
        assertTrue(sink.isEmpty)
        assertEquals(0, sink.resets)
    }

    @Test
    fun eachChoiceOpensOnlyItsOwnDoor() {
        Telemetry.install(sink)
        Telemetry.applyChoices(PrivacyChoices(analytics = false, crashReports = true))
        sendOneOfEach()
        assertTrue(sink.events.isEmpty() && sink.screens.isEmpty() && sink.userProperties.isEmpty())
        assertEquals(1, sink.errors.size)
        assertEquals("fish", sink.crashKeys["screen"])

        Telemetry.uninstall()
        val other = RecordingTelemetry()
        Telemetry.install(other)
        Telemetry.applyChoices(PrivacyChoices(analytics = true, crashReports = false))
        sendOneOfEach()
        assertEquals(listOf("level_start"), other.names())
        assertEquals(listOf("fish"), other.screens)
        assertTrue(other.errors.isEmpty() && other.crashKeys.isEmpty())
    }

    @Test
    fun withdrawingStopsCollectionAndClearsTheAnalyticsData() {
        Telemetry.install(sink)
        Telemetry.applyChoices(PrivacyChoices.ALL)
        sendOneOfEach()
        assertEquals(1, sink.events.size)
        assertEquals(1, sink.errors.size)

        Telemetry.applyChoices(PrivacyChoices.NONE)
        sendOneOfEach()
        assertEquals("nothing more after withdrawal", 1, sink.events.size)
        assertEquals(1, sink.screens.size)
        assertEquals(1, sink.errors.size)
        assertEquals("the device's analytics data is cleared", 1, sink.resets)
        assertEquals(PrivacyChoices.NONE, sink.consents.last())
    }

    @Test
    fun userPropertiesSetBeforeConsentArriveOnceAllowed() {
        Telemetry.install(sink)
        Telemetry.applyChoices(null)
        Analytics.setUserProperty("menu_theme", "lagoon")
        Analytics.setUserProperty("menu_theme", "kelp_forest")
        assertTrue(sink.userProperties.isEmpty())
        Telemetry.applyChoices(PrivacyChoices(analytics = true))
        assertEquals(mapOf("menu_theme" to "kelp_forest"), sink.userProperties)
    }

    @Test
    fun theSdkHearsAboutAGrantBeforeAnythingIsSent() {
        // A sink that logs an event while being told about consent sees the gate still closed.
        val order = ArrayList<String>()
        val probe = object : TelemetrySink by sink {
            override fun applyConsent(choices: PrivacyChoices) {
                order += "consent:${choices.analytics}"
                Analytics.event("probe")
            }

            override fun event(name: String, params: Map<String, Any?>) {
                order += "event:$name"
            }
        }
        Telemetry.install(probe)
        Telemetry.applyChoices(PrivacyChoices.ALL)
        Analytics.event("after")
        assertEquals(listOf("consent:true", "event:after"), order)
        // ...and a withdrawal closes the gate before the SDK is told.
        order.clear()
        Telemetry.applyChoices(PrivacyChoices.NONE)
        assertEquals(listOf("consent:false"), order)
    }

    @Test
    fun consentIsStoredAsADevicePreferenceAndAskedAgainForANewQuestion() {
        val map = HashMap<String, String>()
        val store = object : KeyValueStore {
            override fun getString(key: String): String? = map[key]
            override fun putString(key: String, value: String) {
                map[key] = value
            }
        }
        val consent = ConsentStore(store) { 1234L }
        assertNull("not answered yet", consent.load())
        consent.save(PrivacyChoices(analytics = true, crashReports = false))
        assertEquals(PrivacyChoices(analytics = true, crashReports = false), consent.load())
        assertEquals("1234", map[ConsentStore.KEY_ANSWERED_AT])
        // An answer to an older question doesn't count: the player is asked again.
        map[ConsentStore.KEY_VERSION] = (ConsentStore.CONSENT_VERSION - 1).toString()
        assertNull(consent.load())
    }

    @Test
    fun problemsAreReportedOncePerSession() {
        Telemetry.install(sink)
        Telemetry.applyChoices(PrivacyChoices(crashReports = true))
        repeat(3) { GameAnalytics.problem("gate_test", "the same failure") }
        GameAnalytics.problem("gate_test", "another failure")
        assertEquals(2, sink.errors.size)
        assertEquals("gate_test", sink.errors.first().second)
    }
}
