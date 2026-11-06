package com.deepline.fishing

/**
 * Features that are built and testable but ship in a later update. Each defaults to its build
 * type's value (on in debug builds, off in release; see `app/build.gradle.kts`). A disabled
 * feature shows no screen, tour step, button or notification, and the app never calls its
 * server endpoints. Tests may switch a flag for their own run.
 */
object FeatureFlags {
    /** Player clubs (create or join by code, a weekly club goal, a club leaderboard). */
    @Volatile
    var clubs: Boolean = BuildConfig.CLUBS_ENABLED
}
