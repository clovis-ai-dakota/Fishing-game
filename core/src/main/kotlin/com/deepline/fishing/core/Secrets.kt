package com.deepline.fishing.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fishing secrets: hidden mechanics that reward players who understand the game well.
 *
 * Most secrets are pulled off within a cast: they add a **bonus** to that cast's score every time
 * (solo and in battles, for the computer rival too; online, the server recomputes it). Two are
 * about more than one cast: [SHUTOUT] (a battle result) and [MASTER_ANGLER] (the complete fish
 * log). Every secret also gives a one-time **discovery reward**. The app explains a secret the
 * first time it happens. Secrets are never spelled out in the tour.
 */
object Secrets {
    /** The last bump hooks whatever it hit, which is the only way to land objects. */
    const val LAST_BUMP = "last_bump"

    // Tier 1: curious
    const val FREE_PASS = "free_pass"
    const val SCHOOLING = "schooling"
    const val SURFACE_SKIM = "surface_skim"
    const val ZEN_CAST = "zen_cast"

    // Tier 2: skilled
    const val PINBALL = "pinball"
    const val SECOND_ENCOUNTER = "second_encounter"
    const val CLEAN_DROP = "clean_drop"
    const val ROCK_BOTTOM = "rock_bottom"
    const val BUZZER_BEATER = "buzzer_beater"

    // Tier 3: expert
    const val CORNER_POCKET = "corner_pocket"
    const val GRAND_TOUR = "grand_tour"
    const val SPARKLE_SNAG = "sparkle_snag"
    const val LADDER = "ladder"
    const val PERFECT_LOAD = "perfect_load"
    const val MOONLIT_ANGLER = "moonlit_angler"
    const val SHUTOUT = "shutout"

    // Tier 4: master
    const val WHILE_IT_FLEES = "while_it_flees"
    const val BLIND_DROP = "blind_drop"
    const val ZONE_SWEEP = "zone_sweep"
    const val GRAND_SLAM = "grand_slam"
    const val DOUBLE_HEADER = "double_header"
    const val CROWD_SURFER = "crowd_surfer"

    // Tier 5: legendary (each unlocks a set of cosmetics)
    const val DAVID_AND_GOLIATH = "david_and_goliath"
    const val PERFECT_CAST = "perfect_cast"
    const val MASTER_ANGLER = "master_angler"

    /** All secrets, in rough order of difficulty. */
    val all: List<String> = listOf(
        LAST_BUMP,
        FREE_PASS, SCHOOLING, SURFACE_SKIM, ZEN_CAST,
        PINBALL, SECOND_ENCOUNTER, CLEAN_DROP, ROCK_BOTTOM, BUZZER_BEATER,
        CORNER_POCKET, GRAND_TOUR, SPARKLE_SNAG, LADDER, PERFECT_LOAD, MOONLIT_ANGLER, SHUTOUT,
        WHILE_IT_FLEES, BLIND_DROP, ZONE_SWEEP, GRAND_SLAM, DOUBLE_HEADER, CROWD_SURFER,
        DAVID_AND_GOLIATH, PERFECT_CAST, MASTER_ANGLER,
    )

    /** Difficulty tier of each secret, 1 (easiest) to [MAX_TIER] (legendary). */
    private val tiers: Map<String, Int> = mapOf(
        LAST_BUMP to 1, FREE_PASS to 1, SCHOOLING to 1, SURFACE_SKIM to 1, ZEN_CAST to 1,
        PINBALL to 2, SECOND_ENCOUNTER to 2, CLEAN_DROP to 2, ROCK_BOTTOM to 2, BUZZER_BEATER to 2,
        CORNER_POCKET to 3, GRAND_TOUR to 3, SPARKLE_SNAG to 3, LADDER to 3, PERFECT_LOAD to 3, MOONLIT_ANGLER to 3, SHUTOUT to 3,
        WHILE_IT_FLEES to 4, BLIND_DROP to 4, ZONE_SWEEP to 4, GRAND_SLAM to 4, DOUBLE_HEADER to 4, CROWD_SURFER to 4,
        DAVID_AND_GOLIATH to 5, PERFECT_CAST to 5, MASTER_ANGLER to 5,
    )

    const val MAX_TIER = 5

    fun tierOf(id: String): Int = tiers[id] ?: 1

    /** Which secrets this cast revealed. */
    fun revealedBy(result: CastResult): Set<String> {
        val ids = result.secretBonuses.map { it.secretId }.toMutableSet()
        if (result.lastBumpHooked) ids += LAST_BUMP
        return ids
    }

    /** Shutout: beat the Hard rival 3-0 in Best of 5. */
    fun revealedByBattle(battle: Battle, outcome: BattleOutcome, playerIndex: Int, rival: BotDifficulty?): Set<String> {
        val shutout = battle.mode == BattleMode.BEST_OF_FIVE && rival == BotDifficulty.HARD &&
            !outcome.byForfeit && outcome.winner == playerIndex &&
            battle.roundWins(playerIndex) >= Battle.ROUNDS_TO_WIN && battle.roundWins(1 - playerIndex) == 0
        return if (shutout) setOf(SHUTOUT) else emptySet()
    }

    /** Master angler: every one of the 200 species in the fish log. */
    fun revealedByProgress(p: PlayerProgress): Set<String> =
        if (FishCatalog.all.all { p.isDiscovered(it) }) setOf(MASTER_ANGLER) else emptySet()
}

/** What a bump on the way down hit. */
enum class BumpKind { FISH_HEAD, DRIFTWOOD, ROCK, JELLY;

    companion object {
        fun of(kind: ObstacleKind): BumpKind = when (kind) {
            ObstacleKind.DRIFTWOOD -> DRIFTWOOD
            ObstacleKind.ROCK -> ROCK
            ObstacleKind.JELLY -> JELLY
        }
    }
}

/** Points a secret added to a cast. */
data class SecretBonus(val secretId: String, val points: Int)

/** The thresholds and bonuses of every secret. All numbers for secrets live here. */
object SecretRules {
    const val FREE_PASS_COUNT = 3
    const val FREE_PASS_BONUS = 50
    const val SCHOOLING_COUNT = 3
    const val SCHOOLING_BONUS = 100
    const val SURFACE_SKIM_DEPTH = 10f
    const val SURFACE_SKIM_BONUS = 50
    const val ZEN_CAST_COUNT = 3
    const val ZEN_CAST_BONUS = 150

    const val PINBALL_SECONDS = 2f
    const val PINBALL_BONUS = 200
    const val SECOND_ENCOUNTER_MULTIPLIER = 2.0
    const val CLEAN_DROP_MIN_ROPE_M = 300f
    const val CLEAN_DROP_MULTIPLIER = 1.5
    const val ROCK_BOTTOM_WITHIN_M = 2f
    const val ROCK_BOTTOM_MULTIPLIER = 2.0
    const val BUZZER_BEATER_SECONDS = 15f
    const val BUZZER_BEATER_BONUS = 300

    const val CORNER_POCKET_BONUS = 300
    const val GRAND_TOUR_MULTIPLIER = 2.0
    /** Sparkle snag: the Sparkling fish is worth ×6 in all (×3 for sparkling, then ×2). */
    const val SPARKLE_SNAG_MULTIPLIER = 2.0
    const val LADDER_COUNT = 4
    const val LADDER_MULTIPLIER = 1.5
    /** Weights come in 0.1 kg steps, so this means "exactly at the limit". */
    const val PERFECT_LOAD_TOLERANCE_KG = 0.05f
    const val PERFECT_LOAD_MIN_CATCHES = 5
    /** The catch that fills the line must weigh at least this: no topping off with tiny fish. */
    const val PERFECT_LOAD_FINAL_CATCH_KG = 1.5f
    const val PERFECT_LOAD_MULTIPLIER = 1.5
    const val MOONLIT_ANGLER_MULTIPLIER = 3.0
    /** Shutout pays into the player's points (battles score casts, not results). */
    const val SHUTOUT_BONUS = 5_000

    const val WHILE_IT_FLEES_MULTIPLIER = 4.0
    const val BLIND_DROP_MIN_DEPTH_M = 500f
    const val BLIND_DROP_MULTIPLIER = 2.0
    const val ZONE_SWEEP_MULTIPLIER = 2.0
    const val GRAND_SLAM_COUNT = 8
    const val GRAND_SLAM_MULTIPLIER = 1.5
    const val DOUBLE_HEADER_MULTIPLIER = 3.0
    /**
     * Crowd surfer: with a full line (nothing more can fit), brush this many different fish on
     * the way up. Even a bot that knows where every fish is only manages it in about a quarter
     * of max-rope casts, and players can barely see in the deep: tier 4.
     */
    const val CROWD_SURFER_COUNT = 50
    const val CROWD_SURFER_MULTIPLIER = 2.0

    const val GOLIATH_SPECIES_ID = "pale_leviathan"
    const val DAVID_AND_GOLIATH_MULTIPLIER = 3.0
    const val PERFECT_CAST_MULTIPLIER = 3.0

    private fun isRockBottom(r: CastResult) =
        r.snags.isNotEmpty() && r.reverseReason == ReverseReason.TOUCH_LIMIT &&
            r.ropeLengthM - r.maxDepth <= ROCK_BOTTOM_WITHIN_M

    private fun isPerfectLoad(r: CastResult): Boolean {
        val last = r.catches.lastOrNull { !it.byLastBump } ?: return false
        return r.catches.size + r.snags.size >= PERFECT_LOAD_MIN_CATCHES &&
            last.species.weightKg >= PERFECT_LOAD_FINAL_CATCH_KG &&
            abs(r.totalWeightKg - r.lineStrengthKg) <= PERFECT_LOAD_TOLERANCE_KG
    }

    private fun isCleanDrop(r: CastResult, minDepth: Float) =
        r.reverseReason == ReverseReason.ROPE_END && r.touchesUsed == 0 && r.maxDepth >= minDepth

    /**
     * The bonuses [r] earned. Cast-wide multipliers each add (multiplier - 1) × the cast's base
     * points (they add up rather than compound), and per-catch ones apply to that catch only.
     */
    fun bonuses(r: CastResult): List<SecretBonus> {
        val base = r.basePoints
        val out = ArrayList<SecretBonus>()
        fun add(id: String, points: Double) {
            val p = points.roundToInt()
            if (p > 0) out += SecretBonus(id, p)
        }
        val ascent = r.catches.filter { !it.byLastBump }
        val lastBumpFish = r.catches.firstOrNull { it.byLastBump }
        val lastBumpPoints = lastBumpFish?.points ?: r.snags.firstOrNull()?.points ?: 0

        // Tier 1
        if (r.freePasses >= FREE_PASS_COUNT) add(Secrets.FREE_PASS, FREE_PASS_BONUS.toDouble())
        if (r.catches.groupingBy { it.species.id }.eachCount().values.any { it >= SCHOOLING_COUNT }) {
            add(Secrets.SCHOOLING, SCHOOLING_BONUS.toDouble())
        }
        if (ascent.any { it.depth < SURFACE_SKIM_DEPTH }) add(Secrets.SURFACE_SKIM, SURFACE_SKIM_BONUS.toDouble())
        if (!r.steered && r.catches.size >= ZEN_CAST_COUNT) add(Secrets.ZEN_CAST, ZEN_CAST_BONUS.toDouble())

        // Tier 2
        if (r.bumpTimes.size == r.touchLimit && r.bumpTimes.last() - r.bumpTimes.first() <= PINBALL_SECONDS) {
            add(Secrets.PINBALL, PINBALL_BONUS.toDouble())
        }
        ascent.filter { it.bumpedOnDescent }.takeIf { it.isNotEmpty() }?.let { again ->
            add(Secrets.SECOND_ENCOUNTER, again.sumOf { it.points * (SECOND_ENCOUNTER_MULTIPLIER - 1.0) })
        }
        if (isCleanDrop(r, CLEAN_DROP_MIN_ROPE_M)) add(Secrets.CLEAN_DROP, base * (CLEAN_DROP_MULTIPLIER - 1.0))
        if (isRockBottom(r)) add(Secrets.ROCK_BOTTOM, r.snags.first().points * (ROCK_BOTTOM_MULTIPLIER - 1.0))
        val clock = r.timeLeftAtStart
        if (clock != null && clock < BUZZER_BEATER_SECONDS && r.catches.isNotEmpty()) {
            add(Secrets.BUZZER_BEATER, BUZZER_BEATER_BONUS.toDouble())
        }

        // Tier 3
        if (ascent.any { it.afterWallTurn }) add(Secrets.CORNER_POCKET, CORNER_POCKET_BONUS.toDouble())
        if (r.bumps.size == r.touchLimit && r.bumps.toSet() == BumpKind.entries.toSet()) {
            add(Secrets.GRAND_TOUR, lastBumpPoints * (GRAND_TOUR_MULTIPLIER - 1.0))
        }
        if (lastBumpFish != null && lastBumpFish.sparkling) {
            add(Secrets.SPARKLE_SNAG, lastBumpFish.points * (SPARKLE_SNAG_MULTIPLIER - 1.0))
        }
        if (ascent.size >= LADDER_COUNT && ascent.zipWithNext().all { (a, b) -> b.species.weightKg > a.species.weightKg }) {
            add(Secrets.LADDER, base * (LADDER_MULTIPLIER - 1.0))
        }
        if (isPerfectLoad(r)) add(Secrets.PERFECT_LOAD, base * (PERFECT_LOAD_MULTIPLIER - 1.0))
        if (!r.lanternUsed) {
            ascent.filter { it.species.glowing && it.depth > GameConfig.DARK_DEPTH }.takeIf { it.isNotEmpty() }?.let { lit ->
                add(Secrets.MOONLIT_ANGLER, lit.sumOf { it.points * (MOONLIT_ANGLER_MULTIPLIER - 1.0) })
            }
        }

        // Tier 4
        ascent.filter { it.whileFleeing }.takeIf { it.isNotEmpty() }?.let { fleeing ->
            add(Secrets.WHILE_IT_FLEES, fleeing.sumOf { it.points * (WHILE_IT_FLEES_MULTIPLIER - 1.0) })
        }
        if (isCleanDrop(r, BLIND_DROP_MIN_DEPTH_M) && !r.lanternUsed) add(Secrets.BLIND_DROP, base * (BLIND_DROP_MULTIPLIER - 1.0))
        if (r.catches.map { it.species.zone }.toSet() == DepthZone.entries.toSet()) {
            add(Secrets.ZONE_SWEEP, base * (ZONE_SWEEP_MULTIPLIER - 1.0))
        }
        if (r.snags.isNotEmpty() && ascent.size >= GRAND_SLAM_COUNT) add(Secrets.GRAND_SLAM, base * (GRAND_SLAM_MULTIPLIER - 1.0))
        val together = ascent.filter { it.caughtAt.isFinite() }.groupBy { it.caughtAt }.values.filter { it.size >= 2 }.flatten()
        if (together.isNotEmpty()) add(Secrets.DOUBLE_HEADER, together.sumOf { it.points * (DOUBLE_HEADER_MULTIPLIER - 1.0) })
        if (r.fullLineTouches >= CROWD_SURFER_COUNT) add(Secrets.CROWD_SURFER, base * (CROWD_SURFER_MULTIPLIER - 1.0))

        // Tier 5
        if (lastBumpFish != null && lastBumpFish.species.id == GOLIATH_SPECIES_ID && lastBumpFish.species.weightKg > r.lineStrengthKg) {
            add(Secrets.DAVID_AND_GOLIATH, lastBumpFish.points * (DAVID_AND_GOLIATH_MULTIPLIER - 1.0))
        }
        if (isRockBottom(r) && isPerfectLoad(r) && ascent.any { it.sparkling }) {
            add(Secrets.PERFECT_CAST, base * (PERFECT_CAST_MULTIPLIER - 1.0))
        }
        return out
    }
}

/** The one-time reward for discovering a secret. */
data class SecretReward(val points: Int, val lanterns: Int, val cosmetics: List<Cosmetic> = emptyList())

object SecretRewards {
    private fun set(id: String) = Cosmetic.entries.filter { it.unlockedBy == id }

    private val rewards: Map<String, SecretReward> = mapOf(
        // Tiers 3 to 5 pay more: they're usually found later, when casts earn far more.
        Secrets.LAST_BUMP to SecretReward(0, 0),
        Secrets.FREE_PASS to SecretReward(250, 0),
        Secrets.SCHOOLING to SecretReward(300, 0),
        Secrets.SURFACE_SKIM to SecretReward(300, 0),
        Secrets.ZEN_CAST to SecretReward(400, 0),
        Secrets.PINBALL to SecretReward(750, 1),
        Secrets.SECOND_ENCOUNTER to SecretReward(750, 1),
        Secrets.CLEAN_DROP to SecretReward(1_000, 1),
        Secrets.ROCK_BOTTOM to SecretReward(1_000, 1),
        Secrets.BUZZER_BEATER to SecretReward(800, 1),
        Secrets.CORNER_POCKET to SecretReward(4_000, 2),
        Secrets.GRAND_TOUR to SecretReward(5_000, 2),
        Secrets.SPARKLE_SNAG to SecretReward(5_000, 2),
        Secrets.LADDER to SecretReward(4_000, 2),
        Secrets.PERFECT_LOAD to SecretReward(5_000, 2),
        Secrets.MOONLIT_ANGLER to SecretReward(6_000, 3),
        Secrets.SHUTOUT to SecretReward(6_000, 3),
        Secrets.WHILE_IT_FLEES to SecretReward(18_000, 4),
        Secrets.BLIND_DROP to SecretReward(22_500, 4),
        Secrets.ZONE_SWEEP to SecretReward(24_000, 5),
        Secrets.GRAND_SLAM to SecretReward(18_000, 4),
        Secrets.DOUBLE_HEADER to SecretReward(21_000, 4),
        Secrets.CROWD_SURFER to SecretReward(22_500, 5),
        Secrets.DAVID_AND_GOLIATH to SecretReward(45_000, 6, set(Secrets.DAVID_AND_GOLIATH)),
        Secrets.PERFECT_CAST to SecretReward(60_000, 8, set(Secrets.PERFECT_CAST)),
        Secrets.MASTER_ANGLER to SecretReward(75_000, 10, set(Secrets.MASTER_ANGLER)),
    )

    fun of(id: String): SecretReward = rewards[id] ?: SecretReward(0, 0)
}

/** Where a cosmetic goes. One cosmetic per slot is worn at a time. */
enum class CosmeticSlot(val displayName: String) {
    HOOK("Hook"),
    LINE("Line"),
    BOAT("Boat"),
    TRAIL("Bubble trail"),
    /** Shown under your name on the home screen and in battles (online opponents see it too). */
    TITLE("Title"),
}

/**
 * Rewards for legendary secrets, meant to be shown off. They are purely visual and never change
 * gameplay: same hook size and hitbox, nothing that lights up dark water or reveals fish. The app
 * picks how to draw them.
 */
enum class Cosmetic(
    val displayName: String,
    val description: String,
    val slot: CosmeticSlot,
    val unlockedBy: String,
    /** The set this belongs to, e.g. "Giant-slayer". */
    val setName: String,
) {
    // David and Goliath: Giant-slayer
    LEVIATHAN_BONE_HOOK("Leviathan bone hook", "Ivory carved from a giant, with runes that pulse cyan.", CosmeticSlot.HOOK, Secrets.DAVID_AND_GOLIATH, "Giant-slayer"),
    GIANTS_BRAID_LINE("Giant's braid", "A thick pale braid with a shimmer running down it.", CosmeticSlot.LINE, Secrets.DAVID_AND_GOLIATH, "Giant-slayer"),
    GIANT_SLAYER_PENNANT("Giant-slayer pennant", "A tall mast flying a leviathan banner, and a cyan-trimmed hull.", CosmeticSlot.BOAT, Secrets.DAVID_AND_GOLIATH, "Giant-slayer"),
    TIDE_TRAIL("Tide trail", "Cyan bubbles and ripples behind your hook.", CosmeticSlot.TRAIL, Secrets.DAVID_AND_GOLIATH, "Giant-slayer"),
    GIANT_SLAYER_TITLE("Giant-slayer", "Title: you landed a giant on a thin line.", CosmeticSlot.TITLE, Secrets.DAVID_AND_GOLIATH, "Giant-slayer"),

    // Perfect cast: Prismatic
    PRISM_HOOK("Prism hook", "A crystal hook that cycles through every colour.", CosmeticSlot.HOOK, Secrets.PERFECT_CAST, "Prismatic"),
    RAINBOW_LINE("Rainbow line", "Colours flowing down the whole line.", CosmeticSlot.LINE, Secrets.PERFECT_CAST, "Prismatic"),
    PRISM_SAIL("Prism sail", "A rainbow sail on your boat.", CosmeticSlot.BOAT, Secrets.PERFECT_CAST, "Prismatic"),
    PRISM_TRAIL("Prism trail", "Rainbow bubbles and sparkles behind your hook.", CosmeticSlot.TRAIL, Secrets.PERFECT_CAST, "Prismatic"),
    PERFECTIONIST_TITLE("Perfectionist", "Title: the perfect cast.", CosmeticSlot.TITLE, Secrets.PERFECT_CAST, "Prismatic"),

    // Master angler: Sovereign
    CROWN_HOOK("Crown hook", "Solid gold, with a crown at the eye.", CosmeticSlot.HOOK, Secrets.MASTER_ANGLER, "Sovereign"),
    GOLDEN_LINE("Golden line", "A gold line with glints running down it.", CosmeticSlot.LINE, Secrets.MASTER_ANGLER, "Sovereign"),
    ROYAL_BOAT("Royal boat", "A gold-trimmed hull and a purple crowned sail.", CosmeticSlot.BOAT, Secrets.MASTER_ANGLER, "Sovereign"),
    GOLDEN_TRAIL("Golden trail", "Golden sparkles behind your hook.", CosmeticSlot.TRAIL, Secrets.MASTER_ANGLER, "Sovereign"),
    MASTER_ANGLER_TITLE("Master angler", "Title: every fish in the log.", CosmeticSlot.TITLE, Secrets.MASTER_ANGLER, "Sovereign"),
}
