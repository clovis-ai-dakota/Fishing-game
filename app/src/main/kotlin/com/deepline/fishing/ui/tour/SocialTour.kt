package com.deepline.fishing.ui.tour

import com.deepline.fishing.FeatureFlags
import com.deepline.fishing.Screen
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardView

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

    private val boards = BoardKind.entries.map { it.displayName.lowercase() }.let { it.dropLast(1).joinToString(", ") + " and " + it.last() }

    fun steps(version: Int): List<TourStep> = listOf(
        TourStep(
            id = "social",
            title = "Friends and rankings",
            body = "Friends, the weekly leaderboards and the weekly tournament open from here. Everything there is fished " +
                "on the standard rope of online battles (level ${OnlineProtocol.TOURNAMENT_ROPE_LEVEL} in every stat), so " +
                "skill is what counts.",
            screen = Screen.BATTLE_SETUP,
            target = TourTarget.BATTLE_SOCIAL,
            sinceVersion = version,
        ),
        TourStep(
            id = "friend_code",
            title = "Your friend code",
            body = "Your player id is your friend code: share it so friends can add you. Nobody can find you by name, " +
                "and strangers on the leaderboards only see a name made from your id.",
            screen = Screen.SOCIAL,
            target = TourTarget.SOCIAL_ID,
            sinceVersion = version,
        ),
        TourStep(
            id = "friends",
            title = "Add friends",
            body = "Type a friend's player id to send a request: you're friends once they accept. Their cards show " +
                "whether they're online and their best catches of the week, and Challenge invites them into an online " +
                "battle room.",
            screen = Screen.FRIENDS,
            target = TourTarget.FRIENDS_ADD,
            sinceVersion = version,
        ),
        TourStep(
            id = "leaderboards",
            title = "Weekly leaderboards",
            body = "Three boards, $boards, for ${BoardView.GLOBAL.displayName.lowercase()}, your league of about " +
                "${GameConfig.LEAGUE_SIZE} anglers, or your friends. Only casts the server checks itself count: the weekly " +
                "tournament and online battles. Every board resets on Monday at 00:00 UTC.",
            screen = Screen.LEADERBOARDS,
            target = TourTarget.BOARD_KINDS,
            sinceVersion = version,
        ),
        TourStep(
            id = "tournament",
            title = "The weekly tournament",
            body = "All week, everyone fishes the very same water with ${GameConfig.DESCENT_TOUCH_LIMIT} bumps. You get " +
                "${GameConfig.TOURNAMENT_ATTEMPTS} attempts and your best one counts; starting an attempt uses it. When " +
                "the week ends, rewards go by your final rank, and the top 10% win the Tournament title badge.",
            screen = Screen.TOURNAMENT,
            target = TourTarget.TOURNAMENT_ENTER,
            sinceVersion = version,
        ),
        TourStep(
            id = "clubs",
            title = "Clubs",
            body = "Join a club with its code, or start one with a name made from fishing words. Up to " +
                "${GameConfig.CLUB_MAX_MEMBERS} anglers fish for a weekly goal together, and everyone who helped gets " +
                "each reward reached. There's no chat.",
            screen = Screen.CLUBS,
            target = TourTarget.CLUBS_JOIN,
            sinceVersion = CLUB_STEPS_VERSION,
        ),
    )
}
