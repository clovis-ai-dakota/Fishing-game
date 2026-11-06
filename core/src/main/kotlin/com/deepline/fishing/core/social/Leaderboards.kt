package com.deepline.fishing.core.social

import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.PlayerId
import kotlinx.serialization.Serializable

/** The weekly leaderboards. Each ranks one number per player, highest first. */
@Serializable
enum class BoardKind(val displayName: String, val description: String) {
    BEST_CAST("Best cast", "The highest-scoring single cast of the week."),
    DEEPEST("Deepest catch", "The deepest fish landed this week."),
    RAREST("Rarest catch", "The rarest species landed this week; the deeper catch wins a tie."),
}

/** Who a leaderboard is compared with. */
@Serializable
enum class BoardView(val displayName: String) {
    /** Everyone: the top [com.deepline.fishing.core.GameConfig.LEADERBOARD_TOP]. */
    GLOBAL("Everyone"),
    /** The player's league: about [com.deepline.fishing.core.GameConfig.LEAGUE_SIZE] anglers grouped for the week. */
    LEAGUE("League"),
    FRIENDS("Friends"),
}

/** What a cast is worth on one board, and the species behind it (deepest and rarest boards). */
data class BoardValue(val value: Long, val speciesId: String? = null)

/**
 * Weekly leaderboards are **server-authoritative**: only casts the server validated itself count,
 * the weekly tournament's attempts and online battle casts (both on the standard rope with the
 * online rules, so everyone competes on equal terms). They reset every Monday 00:00 UTC ([Weeks]).
 */
object Leaderboards {
    /** Species spawn weights are at most this; a lower weight is a rarer species. */
    private const val MAX_RARITY_WEIGHT = 100L

    /** The cast's value on each board it counts for. */
    fun valuesOf(result: CastResult): Map<BoardKind, BoardValue> {
        val out = HashMap<BoardKind, BoardValue>()
        out[BoardKind.BEST_CAST] = BoardValue(result.totalPoints.toLong())
        result.catches.maxByOrNull { it.depth }?.let { out[BoardKind.DEEPEST] = BoardValue(depthValue(it.depth), it.species.id) }
        result.catches.maxByOrNull { rarityValue(it.species, it.depth) }?.let {
            out[BoardKind.RAREST] = BoardValue(rarityValue(it.species, it.depth), it.species.id)
        }
        return out
    }

    /** Depths are ranked in centimetres. */
    fun depthValue(depth: Float): Long = (depth.coerceAtLeast(0f) * 100f).toLong()

    fun depthOf(value: Long): Float = value / 100f

    /** Rarer species first, then the deeper catch (in centimetres, up to 999 m). */
    fun rarityValue(species: FishSpecies, depth: Float): Long =
        (MAX_RARITY_WEIGHT - species.rarity).coerceAtLeast(0L) * 100_000L + depthValue(depth).coerceIn(0L, 99_999L)

    // ---- Public names --------------------------------------------------------------------------

    private val adjectives = listOf(
        "Brave", "Bright", "Calm", "Clever", "Coral", "Daring", "Deep", "Drifting", "Eager", "Gentle", "Golden", "Happy",
        "Jolly", "Lucky", "Merry", "Misty", "Mighty", "Nimble", "Northern", "Plucky", "Quiet", "Rapid", "Salty", "Silver",
        "Steady", "Stormy", "Sunny", "Swift", "Tidal", "Wild", "Windy", "Brisk",
    )
    private val nouns = listOf(
        "Marlin", "Otter", "Puffin", "Heron", "Pelican", "Seal", "Dolphin", "Gull", "Sailor", "Skipper", "Mariner", "Drifter",
        "Pike", "Bass", "Bream", "Mackerel", "Barracuda", "Stingray", "Whale", "Squid", "Captain", "Deckhand", "Navigator", "Corsair",
        "Trawler", "Current", "Wave", "Tide", "Reel", "Float", "Harpoon", "Oyster",
    )

    /**
     * The name strangers see on the global and league boards, made from the public id ("Swift
     * Marlin 7Q"): what a player types as their name is only shown to their friends (and to online
     * opponents), so public boards never need moderating.
     */
    fun anglerName(playerId: String): String {
        fun at(i: Int) = PlayerId.ALPHABET.indexOf(playerId.getOrElse(i) { 'A' }).coerceAtLeast(0)
        val adjective = adjectives[at(0) % adjectives.size]
        val noun = nouns[at(1) % nouns.size]
        return "$adjective $noun ${playerId.takeLast(2)}"
    }
}
