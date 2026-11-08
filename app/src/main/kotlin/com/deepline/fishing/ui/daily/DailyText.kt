package com.deepline.fishing.ui.daily

import com.deepline.fishing.R
import com.deepline.fishing.core.ChallengeKind
import com.deepline.fishing.core.DailyChallenge
import com.deepline.fishing.core.DailyGrant
import com.deepline.fishing.core.DailyRewards
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.ui.compactPointsText
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.labelText
import com.deepline.fishing.ui.i18n.nameText
import com.deepline.fishing.ui.pointsText
import com.deepline.fishing.ui.shop.ShopText

/** What the daily loop says: rewards, challenges and the outcome of a claim (strings_daily.xml). */
object DailyText {
    fun grant(g: DailyGrant, p: PlayerProgress): UiText = when (g) {
        is DailyGrant.Points -> pointsText(DailyRewards.pointsFor(p, g.casts))
        is DailyGrant.Tackle -> ShopText.tackleCount(g.tackle, g.count)
        DailyGrant.Shield -> UiText.res(R.string.daily_grant_shield)
    }

    /** "200 pts, 1 Lantern and a streak shield". */
    fun grants(list: List<DailyGrant>, p: PlayerProgress): UiText = UiText.list(list.map { grant(it, p) })

    /** A short label for a calendar tile ("200 pts", "Detector", "Shield"). */
    fun tile(g: DailyGrant, p: PlayerProgress): UiText = when (g) {
        is DailyGrant.Points -> UiText.res(R.string.common_points, compactPointsText(DailyRewards.pointsFor(p, g.casts)))
        is DailyGrant.Tackle -> when (g.tackle) {
            Tackle.LANTERN -> UiText.res(R.string.daily_tile_lanterns, g.count)
            Tackle.DETECTOR -> UiText.res(R.string.daily_tile_detector)
            Tackle.LUCKY_CHARM -> UiText.res(R.string.daily_tile_charm)
            Tackle.SPARKLE_LURE -> UiText.res(R.string.daily_tile_lure)
        }
        DailyGrant.Shield -> UiText.res(R.string.daily_tile_shield)
    }

    fun challenge(c: DailyChallenge): UiText = when (c.kind) {
        ChallengeKind.CASTS -> UiText.plural(R.plurals.daily_challenge_casts, c.target)
        ChallengeKind.CATCH_FISH -> UiText.plural(R.plurals.daily_challenge_catch_fish, c.target)
        ChallengeKind.CATCH_ZONE -> UiText.plural(R.plurals.daily_challenge_catch_zone, c.target, c.target, c.zone?.labelText() ?: UiText.raw("?"))
        ChallengeKind.REACH_DEPTH -> UiText.res(R.string.daily_challenge_reach_depth, c.target)
        ChallengeKind.CATCH_SPECIES -> {
            val name = c.species?.nameText() ?: UiText.raw("?")
            if (c.target == 1) UiText.res(R.string.daily_challenge_catch_species_one, name) else UiText.res(R.string.daily_challenge_catch_species, c.target, name)
        }
        ChallengeKind.EARN_POINTS -> UiText.res(R.string.daily_challenge_earn_points, UiText.number(c.target))
        ChallengeKind.LAND_WEIGHT -> UiText.plural(R.plurals.daily_challenge_land_weight, c.target)
        ChallengeKind.SPARKLING -> UiText.plural(R.plurals.daily_challenge_sparkling, c.target)
        ChallengeKind.BIG_CAST -> UiText.plural(R.plurals.daily_challenge_big_cast, c.target)
        ChallengeKind.NEW_SPECIES -> UiText.plural(R.plurals.daily_challenge_new_species, c.target)
    }

    /** "2 / 5", or the best so far for single-cast goals. */
    fun progress(c: DailyChallenge): UiText = when (c.kind) {
        ChallengeKind.REACH_DEPTH -> UiText.res(R.string.daily_progress_depth, c.progress, c.target)
        ChallengeKind.BIG_CAST -> UiText.res(R.string.daily_progress_big_cast, c.progress, c.target)
        else -> UiText.res(R.string.common_progress_of, UiText.number(c.progress), UiText.number(c.target))
    }

    fun claimed(day: Int, rewards: UiText, streak: Int, shieldsUsed: Int, streakRestarted: Boolean): UiText {
        val streakDays = UiText.plural(R.plurals.common_days, streak)
        return when {
            shieldsUsed == 1 -> UiText.res(R.string.daily_claimed_one_shield, streakDays, day, rewards)
            shieldsUsed > 1 -> UiText.res(R.string.daily_claimed_shields, shieldsUsed, streakDays, day, rewards)
            streakRestarted -> UiText.res(R.string.daily_claimed_restarted, day, rewards)
            streak > 1 -> UiText.res(R.string.daily_claimed_streak, streak, day, rewards)
            else -> UiText.res(R.string.daily_claimed, day, rewards)
        }
    }

    fun collected(rewards: UiText): UiText = UiText.res(R.string.daily_collected, rewards)
}
