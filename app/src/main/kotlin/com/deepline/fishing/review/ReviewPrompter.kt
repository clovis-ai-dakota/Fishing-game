package com.deepline.fishing.review

import android.app.Activity
import android.content.Context
import android.os.Build
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview

/**
 * Shows Google Play's in-app review card. Play decides whether the card actually appears (it has
 * its own quota) and never tells the app whether the player rated, or even whether the card
 * showed: [launch] just returns when the flow is over, and the game carries on the same way.
 */
interface ReviewPrompter {
    /** Asks Play for its review card. May throw when Play is unavailable: callers carry on silently. */
    suspend fun launch(activity: Activity)
}

/** Google Play In-App Review API (play review-ktx). */
class PlayReviewPrompter(context: Context) : ReviewPrompter {
    private val manager by lazy { ReviewManagerFactory.create(context.applicationContext) }

    override suspend fun launch(activity: Activity) {
        // ReviewInfo is short-lived: request it only now that the game has decided to ask.
        val info = manager.requestReview()
        manager.launchReview(activity, info)
    }
}

/** Does nothing (Robolectric, where Play isn't there). */
object NoReviewPrompter : ReviewPrompter {
    override suspend fun launch(activity: Activity) = Unit
}

object ReviewPrompters {
    /** Tests may replace this before the app starts (e.g. with a recording fake). */
    @Volatile
    var factory: ((Context) -> ReviewPrompter)? = null

    fun create(context: Context): ReviewPrompter {
        factory?.let { return it(context) }
        // Robolectric (the UI tests) has no Play Store.
        if (Build.FINGERPRINT == "robolectric") return NoReviewPrompter
        return PlayReviewPrompter(context)
    }
}
