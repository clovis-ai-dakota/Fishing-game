package com.deepline.fishing.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the event taxonomy honest: every event and user property the app logs is within
 * Firebase's limits and listed in docs/analytics-events.md. When you add an event (through
 * [GameAnalytics] or a direct `Analytics.event("...")` call), document it there.
 */
class AnalyticsTaxonomyTest {
    private fun repoFile(path: String): File =
        listOf(File("../$path"), File(path)).firstOrNull { it.exists() } ?: error("Can't find $path")

    private val doc by lazy { repoFile("docs/analytics-events.md").readText() }

    /** Event names passed as literals to Analytics.event anywhere in the app (other features log their own). */
    private val literalEvents: Set<String> by lazy {
        val call = Regex("""Analytics\.event\(\s*"([^"]+)"""")
        repoFile("app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> call.findAll(f.readText()).map { it.groupValues[1] } }
            .toSet()
    }

    private val literalUserProperties: Set<String> by lazy {
        val call = Regex("""Analytics\.setUserProperty\(\s*"([^"]+)"""")
        repoFile("app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> call.findAll(f.readText()).map { it.groupValues[1] } }
            .toSet()
    }

    @Test
    fun everyEventIsValidAndDocumented() {
        val events = GameAnalytics.EVENTS + literalEvents
        for (name in events) {
            assertEquals("$name breaks Firebase's rules", emptyList<String>(), AnalyticsRules.eventProblems(name, emptyMap()))
            assertTrue("$name is missing from docs/analytics-events.md", "`$name`" in doc)
        }
        assertEquals("event names are unique", GameAnalytics.EVENTS.size, GameAnalytics.EVENTS.toSet().size)
    }

    @Test
    fun everyUserPropertyIsValidAndDocumented() {
        val properties = GameAnalytics.USER_PROPERTIES + literalUserProperties
        assertTrue(properties.toSet().size <= AnalyticsRules.MAX_USER_PROPERTIES)
        for (name in properties) {
            assertEquals(emptyList<String>(), AnalyticsRules.userPropertyProblems(name, "x"))
            assertTrue("user property $name is missing from docs/analytics-events.md", "`$name`" in doc)
        }
    }

    @Test
    fun theRulesCatchWhatFirebaseRefuses() {
        assertTrue(AnalyticsRules.eventProblems("Level-Start", emptyMap()).isNotEmpty())
        assertTrue(AnalyticsRules.eventProblems("x".repeat(41), emptyMap()).isNotEmpty())
        assertTrue(AnalyticsRules.eventProblems("firebase_thing", emptyMap()).isNotEmpty())
        assertTrue(AnalyticsRules.eventProblems("session_start", emptyMap()).isNotEmpty())
        assertTrue(AnalyticsRules.eventProblems("ok", mapOf("v" to "y".repeat(101))).isNotEmpty())
        assertTrue(AnalyticsRules.eventProblems("ok", (1..26).associate { "p$it" to it }).isNotEmpty())
        assertTrue(AnalyticsRules.eventProblems("ok", mapOf("list" to listOf(1))).isNotEmpty())
        assertTrue(AnalyticsRules.userPropertyProblems("a_very_long_user_property_name", "v").isNotEmpty())
        assertTrue(AnalyticsRules.userPropertyProblems("theme", "v".repeat(37)).isNotEmpty())
        assertEquals(emptyList<String>(), AnalyticsRules.eventProblems("level_end", mapOf("success" to true, "depth_m" to 120, "zone" to "twilight")))
    }

    @Test
    fun screenNamesAreShortSnakeCase() {
        for (screen in com.deepline.fishing.Screen.entries) {
            for (dex in com.deepline.fishing.DexSection.entries) for (page in com.deepline.fishing.ShopPage.entries) {
                val name = screenName(screen, dex, page)
                assertTrue(name, Regex("[a-z][a-z0-9_]*").matches(name) && name.length <= AnalyticsRules.MAX_STRING_VALUE)
            }
        }
        assertTrue("screen views are documented", "`screen_view`" in doc)
    }
}
