package com.deepline.fishing.analytics

import com.deepline.fishing.Opponent
import com.deepline.fishing.core.Battle
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BattleOutcome
import com.deepline.fishing.core.BattleTurn
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.Milestone
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.ui.tour.TourSession

/**
 * The game's analytics events, named in one place and documented in `docs/analytics-events.md`
 * (keep both in step: `AnalyticsTaxonomyTest` checks every name is there and within Firebase's
 * limits). The view model calls these at the moments that matter; they never see personal data
 * (no names, codes or ids of other players), only game facts.
 */
object GameAnalytics {
    // --- Names ----------------------------------------------------------------------------

    const val TUTORIAL_BEGIN = "tutorial_begin"
    const val TUTORIAL_COMPLETE = "tutorial_complete"
    const val TUTORIAL_SKIP = "tutorial_skip"
    const val LEVEL_START = "level_start"
    const val LEVEL_END = "level_end"
    const val FIRST_CAST = "first_cast"
    const val FIRST_CATCH = "first_catch"
    const val EARN = "earn_virtual_currency"
    const val SPEND = "spend_virtual_currency"
    const val ROPE_UPGRADE = "rope_upgrade"
    const val ROPE_DOWNGRADE = "rope_downgrade"
    const val STORE_CHECKOUT = "store_checkout"
    const val STORE_PURCHASE = "store_purchase"
    const val STORE_PROBLEM = "store_problem"
    const val BATTLE_START = "battle_start"
    const val BATTLE_END = "battle_end"
    const val MILESTONE = "unlock_achievement"
    const val SEASON_CLAIM = "season_claim"
    const val WEEKEND_EVENT_CLAIM = "weekend_event_claim"
    const val SECRET_DISCOVER = "secret_discover"

    /** Every event this object logs (the taxonomy test checks the doc lists them). */
    val EVENTS = listOf(
        TUTORIAL_BEGIN, TUTORIAL_COMPLETE, TUTORIAL_SKIP, LEVEL_START, LEVEL_END, FIRST_CAST, FIRST_CATCH,
        EARN, SPEND, ROPE_UPGRADE, ROPE_DOWNGRADE, STORE_CHECKOUT, STORE_PURCHASE, STORE_PROBLEM,
        BATTLE_START, BATTLE_END, MILESTONE, SEASON_CLAIM, WEEKEND_EVENT_CLAIM, SECRET_DISCOVER,
    )

    const val PROP_ROPE_BAND = "rope_band"
    const val PROP_REACH_ZONE = "reach_zone"
    const val PROP_DEX_BAND = "dex_band"
    const val PROP_THEME = "menu_theme"
    const val PROP_NIGHT_MODE = "night_mode"
    const val PROP_CLUB = "club_member"

    val USER_PROPERTIES = listOf(PROP_ROPE_BAND, PROP_REACH_ZONE, PROP_DEX_BAND, PROP_THEME, PROP_NIGHT_MODE, PROP_CLUB)

    /** The game's only virtual currency. */
    const val POINTS = "points"

    // --- The tour ---------------------------------------------------------------------------

    fun tourStarted(tour: TourSession) {
        Analytics.event(TUTORIAL_BEGIN, mapOf("tour" to tourKind(tour), "steps" to tour.steps.size))
    }

    fun tourEnded(tour: TourSession, completed: Boolean) {
        if (completed) {
            Analytics.event(TUTORIAL_COMPLETE, mapOf("tour" to tourKind(tour), "steps" to tour.steps.size))
        } else {
            Analytics.event(
                TUTORIAL_SKIP,
                mapOf("tour" to tourKind(tour), "step" to tour.index + 1, "step_id" to tour.current.id, "steps" to tour.steps.size),
            )
        }
    }

    private fun tourKind(tour: TourSession) = if (tour.whatsNew) "whats_new" else "full"

    // --- Casts ------------------------------------------------------------------------------

    /** A cast begins: solo, or a turn of a battle against the computer, a friend or online. */
    fun castStarted(s: CastSession, battleMode: BattleMode?, online: Boolean, lure: Boolean) {
        Analytics.event(
            LEVEL_START,
            mapOf(
                "level_name" to levelName(battleMode, online),
                "rope_length_m" to s.specs.lengthM.toInt(),
                "lantern" to s.lanternActive,
                "detector" to s.detectorActive,
                "lucky_charm" to (s.bonusBumps > 0),
                "sparkle_lure" to lure,
            ),
        )
    }

    /**
     * A cast's results were collected. [before] is the progress before they're added: it tells
     * a player's very first cast and first catch (solo casts only: battle catches don't count).
     */
    fun castFinished(result: CastResult, s: CastSession, turn: BattleTurn?, battleMode: BattleMode?, online: Boolean, before: PlayerProgress) {
        val timeUp = turn?.timeLimitSec?.let { s.elapsedSeconds >= it } == true
        val landed = result.catches.size + result.snags.size
        Analytics.event(
            LEVEL_END,
            mapOf(
                "level_name" to levelName(battleMode, online),
                "success" to (landed > 0),
                "end" to if (timeUp) "time_up" else "surfaced",
                "depth_m" to result.maxDepth.toInt(),
                "zone" to zoneName(result.maxDepth),
                "catches" to result.catches.size,
                "sparkling" to result.catches.count { it.sparkling },
                "snags" to result.snags.size,
                "points" to result.totalPoints,
                "secrets" to result.secretBonuses.size,
                "bumps" to result.touchesUsed,
                "turned_by" to turnedBy(result.reverseReason),
                "fast_reel" to (result.fastReelAt != null),
                "lantern" to result.lanternUsed,
                "detector" to result.detectorUsed,
                "duration_s" to s.elapsedSeconds.toInt(),
            ),
        )
        if (turn != null) return
        if (result.totalPoints > 0) earn(result.totalPoints.toLong(), "cast")
        if (before.castsMade == 0) {
            Analytics.event(FIRST_CAST, mapOf("depth_m" to result.maxDepth.toInt(), "catches" to result.catches.size))
        }
        if (before.discoveredCount == 0 && result.catches.isNotEmpty()) {
            val first = result.catches.first()
            Analytics.event(
                FIRST_CATCH,
                mapOf("species_id" to first.species.id, "depth_m" to first.depth.toInt(), "casts_before" to before.castsMade),
            )
        }
    }

    /** The player quit a cast from the pause menu (a battle cast forfeits the battle). */
    fun castAbandoned(s: CastSession, battleMode: BattleMode?, online: Boolean) {
        Analytics.event(
            LEVEL_END,
            mapOf(
                "level_name" to levelName(battleMode, online),
                "success" to false,
                "end" to "quit",
                "phase" to if (s.phase == CastPhase.ASCENDING) "ascending" else "descending",
                "depth_m" to s.maxDepthReached.toInt(),
                "zone" to zoneName(s.maxDepthReached),
                "catches" to s.catches.size,
                "duration_s" to s.elapsedSeconds.toInt(),
            ),
        )
    }

    private fun levelName(battleMode: BattleMode?, online: Boolean): String = when {
        battleMode == null -> "solo"
        online -> "online_" + battleMode.name.lowercase()
        else -> "battle_" + battleMode.name.lowercase()
    }

    private fun zoneName(depth: Float) = DepthZone.of(depth).name.lowercase()

    private fun turnedBy(reason: ReverseReason?) = when (reason) {
        ReverseReason.TOUCH_LIMIT -> "bumps"
        ReverseReason.ROPE_END -> "rope_end"
        null -> "none"
    }

    // --- Points: earning and spending ------------------------------------------------------

    fun earn(points: Long, source: String) {
        if (points <= 0) return
        Analytics.event(EARN, mapOf("virtual_currency_name" to POINTS, "value" to points, "source" to source))
    }

    fun spend(points: Long, source: String, item: String) {
        if (points <= 0) return
        Analytics.event(SPEND, mapOf("virtual_currency_name" to POINTS, "value" to points, "item_name" to item, "source" to source))
    }

    /** Logs the points gained or spent between [before] and [after] (one action's effect). */
    fun pointsChanged(before: PlayerProgress, after: PlayerProgress, source: String, item: String) {
        val delta = after.pointsBalance - before.pointsBalance
        if (delta > 0) earn(delta, source) else spend(-delta, source, item)
    }

    fun ropeChanged(stat: RopeStat, before: PlayerProgress, after: PlayerProgress) {
        val from = before.level(stat)
        val to = after.level(stat)
        val delta = after.pointsBalance - before.pointsBalance
        val name = stat.name.lowercase()
        if (to > from) {
            Analytics.event(ROPE_UPGRADE, mapOf("stat" to name, "level" to to, "cost" to -delta))
            spend(-delta, "rope", "rope_${name}_$to")
        } else if (to < from) {
            Analytics.event(ROPE_DOWNGRADE, mapOf("stat" to name, "level" to to, "refund" to delta))
            earn(delta, "rope_refund")
        }
    }

    fun tackleBought(item: Tackle, before: PlayerProgress, after: PlayerProgress) =
        pointsChanged(before, after, "tackle", item.name.lowercase())

    // --- The shop ---------------------------------------------------------------------------

    /** Bought with points (euro purchases are [storePurchase]). */
    fun shopPointsPurchase(item: ShopItem, before: PlayerProgress, after: PlayerProgress) =
        pointsChanged(before, after, "shop", item.id)

    /** A euro payment was started (Google Play's sheet, or the test store in debug builds). */
    fun storeCheckout(item: ShopItem, gift: Boolean = false) {
        Analytics.event(STORE_CHECKOUT, mapOf("item_id" to item.id, "section" to item.section.name.lowercase(), "gift" to gift))
    }

    /**
     * A euro purchase was granted. No price or currency: Google Play purchases are measured by
     * Firebase itself (`in_app_purchase`, once Play is linked), and logging revenue here too
     * would count it twice.
     */
    fun storePurchase(productId: String) {
        val item = ShopCatalog.byId(productId.removePrefix(GIFT_PREFIX)) ?: return
        Analytics.event(
            STORE_PURCHASE,
            mapOf("item_id" to item.id, "section" to item.section.name.lowercase(), "gift" to productId.startsWith(GIFT_PREFIX)),
        )
    }

    fun storeProblem(productId: String?, problem: String) {
        Analytics.event(STORE_PROBLEM, mapOf("item_id" to (productId ?: "unknown"), "problem" to problem))
    }

    private const val GIFT_PREFIX = "gift_"

    // --- Rewards ------------------------------------------------------------------------------

    fun milestoneClaimed(m: Milestone, before: PlayerProgress, after: PlayerProgress) {
        Analytics.event(MILESTONE, mapOf("achievement_id" to "milestone_${m.index + 1}"))
        pointsChanged(before, after, "milestone", "milestone_${m.index + 1}")
    }

    fun seasonClaimed(rewards: Int, premium: Boolean, before: PlayerProgress, after: PlayerProgress) {
        Analytics.event(SEASON_CLAIM, mapOf("season" to after.seasonNumber, "rewards" to rewards, "premium" to premium))
        pointsChanged(before, after, "season", "season_rewards")
    }

    fun weekendEventClaimed(before: PlayerProgress, after: PlayerProgress) {
        Analytics.event(WEEKEND_EVENT_CLAIM, emptyMap())
        pointsChanged(before, after, "weekend_event", "weekend_event")
    }

    /** A fishing secret's first discovery (internal analytics: it tells which secrets players find). */
    fun secretDiscovered(id: String, inBattle: Boolean, before: PlayerProgress, after: PlayerProgress) {
        Analytics.event(SECRET_DISCOVER, mapOf("secret_id" to id, "tier" to Secrets.tierOf(id), "in_battle" to inBattle))
        pointsChanged(before, after, "secret", id)
    }

    // --- Battles ----------------------------------------------------------------------------

    fun battleStarted(mode: BattleMode, opponent: Opponent, stake: Long) {
        Analytics.event(BATTLE_START, mapOf("battle_mode" to mode.name.lowercase(), "opponent" to opponentName(opponent), "stake" to stake))
        if (stake > 0) spend(stake, "wager", "wager_" + opponentName(opponent))
    }

    private var lastEndedBattle: Battle? = null

    /**
     * A battle has an outcome (reported once per battle). [me] is the device player's index (0
     * offline, the server's index online); pass and play reports which player won instead.
     */
    fun battleEnded(battle: Battle, outcome: BattleOutcome, opponent: Opponent?, me: Int, stake: Long, payout: Long) {
        if (lastEndedBattle === battle) return
        lastEndedBattle = battle
        val result = when {
            outcome.winner == null -> "draw"
            opponent == Opponent.Friend -> "player_${outcome.winner + 1}"
            outcome.winner == me -> "win"
            else -> "loss"
        }
        val who = opponent?.let(::opponentName) ?: "unknown"
        Analytics.event(
            BATTLE_END,
            mapOf(
                "battle_mode" to battle.mode.name.lowercase(),
                "opponent" to who,
                "result" to result,
                "by_forfeit" to outcome.byForfeit,
                "stake" to stake,
                "payout" to payout,
            ),
        )
        if (payout > 0) earn(payout, "wager")
    }

    private fun opponentName(o: Opponent) = when (o) {
        is Opponent.Computer -> o.difficulty.name.lowercase()
        Opponent.Friend -> "friend"
        Opponent.Online -> "online"
    }

    // --- Who the player is (user properties) ----------------------------------------------------

    private val lastProperties = HashMap<String, String>()

    /** Keeps the user properties in step with the progress (only changes are sent). */
    fun progressChanged(p: PlayerProgress) {
        val levels = RopeStat.entries.sumOf { p.level(it) }
        val maxLevels = RopeStat.entries.sumOf { it.maxLevel }
        val caught = p.discoveredCount
        val values = mapOf(
            PROP_ROPE_BAND to band(levels * 100 / maxLevels.coerceAtLeast(1)),
            PROP_REACH_ZONE to DepthZone.of((p.specs.lengthM - 1f).coerceAtLeast(0f)).name.lowercase(),
            PROP_DEX_BAND to band(caught * 100 / FishCatalog.all.size.coerceAtLeast(1)),
            PROP_THEME to p.menuTheme.lowercase(),
            PROP_NIGHT_MODE to p.nightMode.lowercase(),
            PROP_CLUB to if (ShopCatalog.isClubMember(p, ShopClock.now())) "yes" else "no",
        )
        for ((name, value) in values) {
            if (lastProperties[name] != value) {
                lastProperties[name] = value
                Analytics.setUserProperty(name, value)
            }
        }
    }

    /** A percentage as a coarse band: "0", "1-24", "25-49", "50-74", "75-99" or "100". */
    private fun band(percent: Int): String = when {
        percent <= 0 -> "0"
        percent < 25 -> "1-24"
        percent < 50 -> "25-49"
        percent < 75 -> "50-74"
        percent < 100 -> "75-99"
        else -> "100"
    }

    // --- Errors -----------------------------------------------------------------------------

    private val reported = HashSet<String>()

    /**
     * A caught problem worth a non-fatal crash report: a 3D renderer failure, a payment the
     * store refused, the server unreachable... Each kind is reported once per app session, and
     * [area] says where it happened. Messages must not hold personal data.
     */
    fun problem(area: String, error: Throwable) {
        val key = "$area:${error::class.java.name}:${error.message}"
        synchronized(reported) {
            if (reported.size >= MAX_PROBLEMS_PER_SESSION || !reported.add(key)) return
        }
        Analytics.recordError(error, area)
    }

    /** The same, for a problem known by its description only. */
    fun problem(area: String, description: String) = problem(area, ReportedProblem(area, description))

    private const val MAX_PROBLEMS_PER_SESSION = 20
}

/** A problem that was handled (no crash), reported to Crashlytics as a non-fatal. */
class ReportedProblem(area: String, description: String) : Exception("$area: $description")
