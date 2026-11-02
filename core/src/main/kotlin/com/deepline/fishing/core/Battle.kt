package com.deepline.fishing.core

import kotlinx.serialization.Serializable

/** The ways two players can compete. */
enum class BattleMode(
    val title: String,
    val summary: String,
    /** Fixed number of casts per player, or null for round-based / timed modes. */
    val castsPerPlayer: Int?,
    /** Total fishing time per player in seconds, or null when untimed. */
    val timeLimitSec: Float?,
) {
    BEST_OF_FIVE(
        "Best of 5",
        "One cast each per round. The higher score takes the round. First to 3 rounds wins.",
        castsPerPlayer = null,
        timeLimitSec = null,
    ),
    TIME_ATTACK(
        "3-Minute Rush",
        "3 minutes of fishing each. Only your single best cast counts. A cast still running when time is up ends on the spot.",
        castsPerPlayer = null,
        timeLimitSec = 180f,
    ),
    HEAVY_HAUL(
        "Heavy Haul",
        "3 casts each. Most total kilograms landed wins.",
        castsPerPlayer = 3,
        timeLimitSec = null,
    ),
    SPECIES_HUNT(
        "Species Hunt",
        "3 casts each. Most different species wins. Total points break ties.",
        castsPerPlayer = 3,
        timeLimitSec = null,
    ),
    DEEP_DIVE(
        "Deep Dive",
        "3 casts each. The single deepest catch wins. Total points break ties.",
        castsPerPlayer = 3,
        timeLimitSec = null,
    ),
}

/** A participant. [bot] is null for a human player. */
data class BattlePlayer(val name: String, val bot: BotDifficulty? = null) {
    val isBot: Boolean get() = bot != null
}

data class BattleCast(val result: CastResult, val durationSec: Float) {
    val points: Int get() = result.totalPoints
}

/**
 * Whose cast comes next. [seed] generates the water for that cast: both players get the same
 * water for the same cast number (or round), so battles are fair.
 */
@Serializable
data class BattleTurn(
    val playerIndex: Int,
    /** 1-based cast number for this player. */
    val castNumber: Int,
    /** 1-based round number in round-based modes, 0 otherwise. */
    val round: Int,
    val seed: Long,
    /** Seconds this player has left in timed modes, null otherwise. */
    val timeLimitSec: Float?,
)

data class BattleRound(val number: Int, val points: List<Int>, val winner: Int?)

data class BattleScore(val primary: Double, val secondary: Double, val label: String)

/** [winner] is null for a draw. */
data class BattleOutcome(val winner: Int?, val byForfeit: Boolean)

class Battle(
    val mode: BattleMode,
    val players: List<BattlePlayer>,
    val seed: Long,
) {
    init {
        require(players.size == 2) { "A battle needs exactly two players" }
    }

    private val casts = List(2) { ArrayList<BattleCast>() }
    private val _rounds = ArrayList<BattleRound>()
    val rounds: List<BattleRound> get() = _rounds

    var forfeitedBy: Int? = null
        private set

    fun castsOf(player: Int): List<BattleCast> = casts[player]

    fun timeUsed(player: Int): Float = casts[player].sumOf { it.durationSec.toDouble() }.toFloat()

    fun roundWins(player: Int): Int = _rounds.count { it.winner == player }

    fun totalPoints(player: Int): Int = casts[player].sumOf { it.points }

    /** Seed of the water for the [index]-th cast (0-based) of each player / [index]-th round. */
    fun castSeed(index: Int): Long = seed * 1_000_003L + index * 7_919L

    /**
     * The next cast [player] may fish, independently of the other player (used for online
     * battles, where both fish at the same time). In Best of 5 a player waits for the opponent
     * to finish the current round before starting the next one; null then means "waiting".
     */
    fun nextTurnFor(player: Int): BattleTurn? {
        if (forfeitedBy != null) return null
        val c = casts[player]
        val limit = mode.timeLimitSec
        val perPlayer = mode.castsPerPlayer
        return when {
            mode == BattleMode.BEST_OF_FIVE -> {
                if (roundWins(0) >= ROUNDS_TO_WIN || roundWins(1) >= ROUNDS_TO_WIN) return null
                if (_rounds.size >= MAX_ROUNDS) return null
                if (c.size > _rounds.size) return null
                BattleTurn(player, c.size + 1, c.size + 1, castSeed(c.size), null)
            }
            limit != null -> {
                val left = limit - timeUsed(player)
                if (left > MIN_TIME_LEFT) BattleTurn(player, c.size + 1, 0, castSeed(c.size), left) else null
            }
            perPlayer != null ->
                if (c.size < perPlayer) BattleTurn(player, c.size + 1, 0, castSeed(c.size), null) else null
            else -> null
        }
    }

    /** The next cast when players take turns on one device: player 1 goes first. */
    fun nextTurn(): BattleTurn? = nextTurnFor(0) ?: nextTurnFor(1)

    val isOver: Boolean get() = nextTurn() == null

    fun record(turn: BattleTurn, cast: BattleCast) {
        check(turn == nextTurnFor(turn.playerIndex)) { "Out-of-order battle turn: $turn" }
        casts[turn.playerIndex] += cast
        if (mode == BattleMode.BEST_OF_FIVE) {
            val round = _rounds.size
            if (casts[0].size > round && casts[1].size > round) {
                val a = casts[0][round].points
                val b = casts[1][round].points
                val winner = when {
                    a > b -> 0
                    b > a -> 1
                    else -> null
                }
                _rounds += BattleRound(round + 1, listOf(a, b), winner)
            }
        }
    }

    fun forfeit(player: Int) {
        if (!isOver) forfeitedBy = player
    }

    fun score(player: Int): BattleScore {
        val c = casts[player]
        val total = totalPoints(player).toDouble()
        return when (mode) {
            BattleMode.BEST_OF_FIVE -> {
                val wins = roundWins(player)
                BattleScore(wins.toDouble(), total, if (wins == 1) "1 round" else "$wins rounds")
            }
            BattleMode.TIME_ATTACK -> {
                val best = c.maxOfOrNull { it.points } ?: 0
                BattleScore(best.toDouble(), total, "$best pts best cast")
            }
            BattleMode.HEAVY_HAUL -> {
                val kg = c.sumOf { it.result.totalWeightKg.toDouble() }
                BattleScore(kg, total, "%.1f kg".format(kg))
            }
            BattleMode.SPECIES_HUNT -> {
                val n = c.flatMap { cast -> cast.result.catches.map { it.species.id } }.toSet().size
                BattleScore(n.toDouble(), total, if (n == 1) "1 species" else "$n species")
            }
            BattleMode.DEEP_DIVE -> {
                val deepest = c.flatMap { it.result.catches }.maxOfOrNull { it.depth } ?: 0f
                BattleScore(deepest.toDouble(), total, "deepest catch %.0f m".format(deepest))
            }
        }
    }

    /** The result once the battle is over, null while it is still going. */
    fun outcome(): BattleOutcome? {
        forfeitedBy?.let { return BattleOutcome(winner = 1 - it, byForfeit = true) }
        if (!isOver) return null
        val a = score(0)
        val b = score(1)
        val cmp = compareValuesBy(a, b, { it.primary }, { it.secondary })
        val winner = when {
            cmp > 0 -> 0
            cmp < 0 -> 1
            else -> null
        }
        return BattleOutcome(winner, byForfeit = false)
    }

    companion object {
        const val ROUNDS_TO_WIN = 3
        /** Tied rounds don't count, so cap the length; after that the score decides. */
        const val MAX_ROUNDS = 9
        private const val MIN_TIME_LEFT = 0.05f
    }
}
