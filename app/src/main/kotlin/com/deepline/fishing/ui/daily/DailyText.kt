package com.deepline.fishing.ui.daily

import com.deepline.fishing.core.ChallengeKind
import com.deepline.fishing.core.DailyChallenge
import com.deepline.fishing.core.DailyGrant
import com.deepline.fishing.core.DailyRewards
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.shop.ShopText

/** What the daily loop says: rewards, challenges and the outcome of a claim (whole sentences). */
object DailyText {
    fun grant(g: DailyGrant, p: PlayerProgress): String = when (g) {
        is DailyGrant.Points -> "${formatPoints(DailyRewards.pointsFor(p, g.casts))} pts"
        is DailyGrant.Tackle -> "${g.count} ${ShopText.tackleName(g.tackle, g.count)}"
        DailyGrant.Shield -> "a streak shield"
    }

    /** "200 pts, 1 Lantern and a streak shield". */
    fun grants(list: List<DailyGrant>, p: PlayerProgress): String {
        val parts = list.map { grant(it, p) }
        return if (parts.size <= 1) parts.joinToString() else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    /** A short label for a calendar tile ("200 pts", "Detector", "+ shield"). */
    fun tile(g: DailyGrant, p: PlayerProgress): String = when (g) {
        is DailyGrant.Points -> "${com.deepline.fishing.ui.compactPoints(DailyRewards.pointsFor(p, g.casts))} pts"
        is DailyGrant.Tackle -> when (g.tackle) {
            com.deepline.fishing.core.Tackle.LANTERN -> "${g.count} × Lantern"
            com.deepline.fishing.core.Tackle.DETECTOR -> "Detector"
            com.deepline.fishing.core.Tackle.LUCKY_CHARM -> "Lucky charm"
            com.deepline.fishing.core.Tackle.SPARKLE_LURE -> "Sparkle lure"
        }
        DailyGrant.Shield -> "Shield"
    }

    fun challenge(c: DailyChallenge): String = when (c.kind) {
        ChallengeKind.CASTS -> "Cast your line ${c.target} times."
        ChallengeKind.CATCH_FISH -> "Catch ${c.target} fish."
        ChallengeKind.CATCH_ZONE -> "Catch ${c.target} fish from the ${c.zone?.displayName}."
        ChallengeKind.REACH_DEPTH -> "Reach ${c.target} m in a cast."
        ChallengeKind.CATCH_SPECIES -> if (c.target == 1) "Catch a ${c.species?.name}." else "Catch ${c.target} ${c.species?.name}."
        ChallengeKind.EARN_POINTS -> "Score ${formatPoints(c.target)} points."
        ChallengeKind.LAND_WEIGHT -> "Land ${c.target} kg of fish."
        ChallengeKind.SPARKLING -> "Catch ${c.target} Sparkling fish."
        ChallengeKind.BIG_CAST -> "Land ${c.target} fish in a single cast."
        ChallengeKind.NEW_SPECIES -> if (c.target == 1) "Catch a species you've never caught." else "Catch ${c.target} species you've never caught."
    }

    /** "2 / 5", or the best so far for single-cast goals. */
    fun progress(c: DailyChallenge): String = when (c.kind) {
        ChallengeKind.REACH_DEPTH -> "Deepest: ${c.progress} of ${c.target} m"
        ChallengeKind.BIG_CAST -> "Best cast: ${c.progress} of ${c.target} fish"
        ChallengeKind.EARN_POINTS -> "${formatPoints(c.progress)} / ${formatPoints(c.target)}"
        else -> "${c.progress} / ${c.target}"
    }

    fun claimed(day: Int, rewards: String, streak: Int, shieldsUsed: Int, streakRestarted: Boolean): String = when {
        shieldsUsed == 1 -> "A streak shield covered the day you missed: your streak is $streak days. Day $day reward: $rewards."
        shieldsUsed > 1 -> "$shieldsUsed streak shields covered the days you missed: your streak is $streak days. Day $day reward: $rewards."
        streakRestarted -> "A new streak starts today. Day $day reward: $rewards."
        streak > 1 -> "$streak days in a row! Day $day reward: $rewards."
        else -> "Day $day reward: $rewards."
    }

    fun collected(rewards: String): String = "Your finished challenges from an earlier day were collected for you: $rewards."
}
