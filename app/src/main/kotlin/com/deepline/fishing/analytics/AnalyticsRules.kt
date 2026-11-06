package com.deepline.fishing.analytics

/**
 * Google Analytics for Firebase's collection limits, so a bad name is caught by a test instead of
 * being silently dropped in production (see docs/research/analytics-privacy.md for the sources).
 */
object AnalyticsRules {
    const val MAX_NAME = 40
    const val MAX_PARAMS = 25
    const val MAX_STRING_VALUE = 100
    const val MAX_USER_PROPERTY_NAME = 24
    const val MAX_USER_PROPERTY_VALUE = 36
    const val MAX_USER_PROPERTIES = 25

    private val NAME = Regex("[a-zA-Z][a-zA-Z0-9_]*")

    /** Names starting with these belong to Google. */
    val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")

    /**
     * Event names Firebase reserves for its own automatic events. `screen_view` is logged through
     * [Analytics.screen] only.
     */
    val RESERVED_EVENTS = setOf(
        "ad_activeview", "ad_click", "ad_exposure", "ad_impression", "ad_query", "ad_reward",
        "adunit_exposure", "app_background", "app_clear_data", "app_exception", "app_remove",
        "app_store_refund", "app_store_subscription_cancel", "app_store_subscription_convert",
        "app_store_subscription_renew", "app_update", "app_upgrade", "dynamic_link_app_open",
        "dynamic_link_app_update", "dynamic_link_first_open", "error", "first_open", "first_visit",
        "in_app_purchase", "notification_dismiss", "notification_foreground", "notification_open",
        "notification_receive", "os_update", "screen_view", "session_start",
        "session_start_with_rollout", "user_engagement",
    )

    val RESERVED_USER_PROPERTIES = setOf(
        "first_open_time", "first_visit_time", "last_deep_link_referrer", "user_id", "first_open_after_install",
    )

    /** Why Firebase would refuse (or cut) this event, if anything. */
    fun eventProblems(name: String, params: Map<String, Any?>): List<String> {
        val problems = ArrayList<String>()
        nameProblem(name, MAX_NAME)?.let { problems += "event '$name': $it" }
        if (name in RESERVED_EVENTS) problems += "event '$name' is reserved by Firebase"
        if (params.size > MAX_PARAMS) problems += "event '$name' has ${params.size} parameters (max $MAX_PARAMS)"
        for ((key, value) in params) {
            nameProblem(key, MAX_NAME)?.let { problems += "event '$name', parameter '$key': $it" }
            when (value) {
                null, is Boolean, is Int, is Long, is Float, is Double -> Unit
                is String -> if (value.length > MAX_STRING_VALUE) {
                    problems += "event '$name', parameter '$key': value longer than $MAX_STRING_VALUE characters"
                }
                else -> problems += "event '$name', parameter '$key': unsupported type ${value::class.simpleName}"
            }
        }
        return problems
    }

    fun userPropertyProblems(name: String, value: String?): List<String> {
        val problems = ArrayList<String>()
        nameProblem(name, MAX_USER_PROPERTY_NAME)?.let { problems += "user property '$name': $it" }
        if (name in RESERVED_USER_PROPERTIES) problems += "user property '$name' is reserved by Firebase"
        if (value != null && value.length > MAX_USER_PROPERTY_VALUE) {
            problems += "user property '$name': value longer than $MAX_USER_PROPERTY_VALUE characters"
        }
        return problems
    }

    private fun nameProblem(name: String, max: Int): String? = when {
        name.isEmpty() || name.length > max -> "names have 1 to $max characters"
        !NAME.matches(name) -> "names are letters, digits and underscores, starting with a letter"
        RESERVED_PREFIXES.any { name.startsWith(it) } -> "the prefixes ${RESERVED_PREFIXES.joinToString()} are reserved"
        else -> null
    }
}
