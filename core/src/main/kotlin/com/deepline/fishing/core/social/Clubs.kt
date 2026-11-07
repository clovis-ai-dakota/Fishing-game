package com.deepline.fishing.core.social

import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.SeasonGrant
import com.deepline.fishing.core.Seasons
import com.deepline.fishing.core.Tackle
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class ClubRole(val displayName: String) { OWNER("Owner"), MEMBER("Member") }

/**
 * Clubs (built now, shipped in a later update behind the app's `FeatureFlags.clubs`): a group of
 * up to [GameConfig.CLUB_MAX_MEMBERS] anglers who join with the club's code. One owner (who can
 * remove members, rename the club and hand it over) and members; anyone can leave, and when the
 * owner leaves the longest-standing member takes over. There is no chat, and club names are
 * generated from the word lists below, so nothing typed by players is shown to the club.
 *
 * Every week the club has a shared goal: fish caught together in solo casts. Each tier of
 * [GameConfig.CLUB_GOAL_TIERS] reached gives every member who helped that week a small reward.
 */
object Clubs {
    const val CODE_LENGTH = 6
    const val CODE_ALPHABET = PlayerId.ALPHABET

    val adjectives = listOf(
        "Brave", "Bright", "Calm", "Clever", "Coral", "Daring", "Deep", "Drifting", "Eager", "Gentle", "Golden", "Happy",
        "Jolly", "Lucky", "Merry", "Misty", "Mighty", "Nimble", "Northern", "Plucky", "Quiet", "Rapid", "Salty", "Silver",
        "Steady", "Stormy", "Sunny", "Swift", "Tidal", "Wild", "Windy", "Brisk",
    )
    val nouns = listOf(
        "Anglers", "Barracudas", "Captains", "Corsairs", "Currents", "Deckhands", "Dolphins", "Drifters", "Gulls", "Harpoons",
        "Herons", "Hooks", "Mackerels", "Mariners", "Marlins", "Navigators", "Otters", "Pelicans", "Pikes", "Puffins",
        "Reels", "Sailors", "Seals", "Skippers", "Stingrays", "Tides", "Trawlers", "Waves", "Whales", "Oysters", "Floats", "Squids",
    )

    /** "Salty Otters", or null for indexes outside the word lists. */
    fun name(adjective: Int, noun: Int): String? {
        val a = adjectives.getOrNull(adjective) ?: return null
        val n = nouns.getOrNull(noun) ?: return null
        return "$a $n"
    }

    /** A few different names to pick from when creating or renaming a club. */
    fun suggestions(random: Random, count: Int = 3): List<Pair<Int, Int>> {
        val out = LinkedHashSet<Pair<Int, Int>>()
        while (out.size < count) out += random.nextInt(adjectives.size) to random.nextInt(nouns.size)
        return out.toList()
    }

    /** A code as typed by a player: case, spaces and dashes don't matter. */
    fun normalizeCode(code: String): String = code.uppercase().filter { it in CODE_ALPHABET }

    fun isValidCode(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in CODE_ALPHABET }

    fun randomCode(random: Random): String = String(CharArray(CODE_LENGTH) { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] })

    /** Tiers of the weekly goal reached with [fish] caught together (0 to 3). */
    fun tiersReached(fish: Int): Int = GameConfig.CLUB_GOAL_TIERS.count { fish >= it }

    /**
     * What each tier of the weekly goal gives every member who helped. Like the tournament's,
     * these are small (a week's best is under a weekend event's reward), so the economy's pacing
     * holds for club members.
     */
    fun rewardOf(tier: Int): List<SeasonGrant> = when (tier) {
        1 -> listOf(SeasonGrant.Tackle(Tackle.LANTERN, 1))
        2 -> listOf(SeasonGrant.Tackle(Tackle.LANTERN, 1), SeasonGrant.Tackle(Tackle.DETECTOR, 1))
        3 -> listOf(SeasonGrant.Tackle(Tackle.LANTERN, 2), SeasonGrant.Points(2))
        else -> emptyList()
    }

    fun claimKey(week: Long, tier: Int): String = "$week:$tier"

    /** Gives a club goal tier's reward once per phone; null if it was already given. */
    fun grant(p: PlayerProgress, week: Long, tier: Int, now: Long): PlayerProgress? {
        val key = claimKey(week, tier)
        if (key in p.clubClaims || tier !in 1..GameConfig.CLUB_GOAL_TIERS.size) return null
        // Only recent weeks matter: older keys can't be claimed any more.
        val kept = (p.clubClaims + key).filter { k -> k.substringBefore(':').toLongOrNull()?.let { it >= week - 2 } ?: false }.toSet()
        return Seasons.give(p, rewardOf(tier), now).copy(clubClaims = kept)
    }
}
