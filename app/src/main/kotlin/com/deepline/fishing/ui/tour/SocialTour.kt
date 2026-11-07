package com.deepline.fishing.ui.tour

import com.deepline.fishing.FeatureFlags
import com.deepline.fishing.R
import com.deepline.fishing.Screen
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.social.TournamentTier
import kotlin.math.roundToInt

/**
 * The tour's steps for friends, the weekly leaderboards, the weekly tournament and clubs. Club
 * steps are only in [Tour.steps] while [FeatureFlags.clubs] is on ([isShown]).
 *
 * Shipping clubs: bump [Tour.VERSION] and set [CLUB_STEPS_VERSION] to it, so returning players
 * (who never saw the club step while the flag was off) get it in "What's new".
 */
object SocialTour {
    /** The tour version that introduces the club steps: the new [Tour.VERSION] when clubs ship. */
    const val CLUB_STEPS_VERSION = Tour.VERSION

    /** Steps that explain clubs, shown only while clubs are switched on. */
    val clubStepIds = setOf("clubs")

    fun isShown(step: TourStep): Boolean = FeatureFlags.clubs || step.id !in clubStepIds

    fun steps(version: Int): List<TourStep> = listOf(
        TourStep(
            id = "social",
            title = text(R.string.tour_social_title),
            body = text(R.string.tour_social_body, OnlineProtocol.TOURNAMENT_ROPE_LEVEL),
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_SOCIAL,
            sinceVersion = version,
        ),
        TourStep(
            id = "friend_code",
            title = text(R.string.tour_friend_code_title),
            body = text(R.string.tour_friend_code_body),
            screen = Screen.SOCIAL,
            target = TourTarget.SOCIAL_ID,
            sinceVersion = version,
        ),
        TourStep(
            id = "friends",
            title = text(R.string.tour_friends_title),
            body = text(R.string.tour_friends_body),
            screen = Screen.FRIENDS,
            target = TourTarget.FRIENDS_ADD,
            sinceVersion = version,
        ),
        TourStep(
            id = "leaderboards",
            title = text(R.string.tour_leaderboards_title),
            body = text(R.string.tour_leaderboards_body, GameConfig.LEAGUE_SIZE),
            screen = Screen.LEADERBOARDS,
            target = TourTarget.BOARD_KINDS,
            sinceVersion = version,
        ),
        TourStep(
            id = "tournament",
            title = text(R.string.tour_tournament_title),
            body = text(
                R.string.tour_tournament_body,
                GameConfig.DESCENT_TOUCH_LIMIT,
                GameConfig.TOURNAMENT_ATTEMPTS,
                (TournamentTier.GOLD.topFraction * 100).roundToInt(),
            ),
            screen = Screen.TOURNAMENT,
            target = TourTarget.TOURNAMENT_ENTER,
            sinceVersion = version,
        ),
        TourStep(
            id = "clubs",
            title = text(R.string.tour_clubs_title),
            body = text(R.string.tour_clubs_body, GameConfig.CLUB_MAX_MEMBERS),
            screen = Screen.CLUBS,
            target = TourTarget.CLUBS_JOIN,
            sinceVersion = CLUB_STEPS_VERSION,
        ),
    )
}
