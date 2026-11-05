package com.deepline.fishing.core.online

import com.deepline.fishing.core.BattleCast
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BattleTurn
import com.deepline.fishing.core.BumpKind
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CatchRecord
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.Scoring
import com.deepline.fishing.core.SnagRecord
import com.deepline.fishing.core.snagPoints
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Messages exchanged over the `/battle` WebSocket, encoded as JSON with a `type` field.
 *
 * Flow: the client says [ClientMessage.Hello], then either creates a room, joins one with a
 * code, or asks for a quick match. Once two players are paired the server sends
 * [ServerMessage.MatchStarted] with the shared seed. Both players then fish at the same time;
 * each finished cast is sent with [ClientMessage.CastFinished], validated by the server and
 * broadcast to both as [ServerMessage.CastRecorded]. The server ends the match with
 * [ServerMessage.MatchOver].
 */
object OnlineProtocol {
    /**
     * 2: bumps only on fish heads, and the last bump hooks what it hit (snags).
     * 3: fishing-secret bonuses (casts report what each bump hit and which fish were re-caught).
     * 4: all 24 secrets (more cast facts), the 200th fish, and player titles.
     * 5: full-line touches (the Crowd surfer secret).
     * 6: Lanterns and rare fish detectors in battles (casts say whether they were used), title
     *    badge frames and public player ids (for gifts).
     */
    const val VERSION = 6

    /** Online battles use the same standard rope for both players. */
    const val TOURNAMENT_ROPE_LEVEL = 5
    val tournamentSpecs: RopeSpecs = RopeSpecs.fromLevels(RopeStat.entries.associateWith { TOURNAMENT_ROPE_LEVEL })

    const val MAX_NAME_LENGTH = 16
    const val ROOM_CODE_LENGTH = 5
    const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(message: ClientMessage): String = json.encodeToString(ClientMessage.serializer(), message)
    fun encode(message: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), message)

    fun decodeClient(text: String): ClientMessage? =
        runCatching { json.decodeFromString(ClientMessage.serializer(), text) }.getOrNull()

    fun decodeServer(text: String): ServerMessage? =
        runCatching { json.decodeFromString(ServerMessage.serializer(), text) }.getOrNull()

    fun sanitizeName(name: String): String =
        name.trim().filter { !it.isISOControl() }.take(MAX_NAME_LENGTH).ifBlank { "Angler" }

    fun normalizeRoomCode(code: String): String = code.trim().uppercase()
}

@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("hello")
    data class Hello(
        val name: String,
        val protocolVersion: Int = OnlineProtocol.VERSION,
        /** The title the player wears (a cosmetic), shown to the opponent. */
        val title: String? = null,
        /** The title badge's frame (a `TitleFrame` name). */
        val titleFrame: String? = null,
        /** The player's public id (see `PlayerId`), so the opponent can send them a gift. */
        val playerId: String? = null,
    ) : ClientMessage

    @Serializable @SerialName("create_room")
    data class CreateRoom(val mode: BattleMode) : ClientMessage

    @Serializable @SerialName("join_room")
    data class JoinRoom(val code: String) : ClientMessage

    @Serializable @SerialName("quick_match")
    data class QuickMatch(val mode: BattleMode) : ClientMessage

    @Serializable @SerialName("cancel_waiting")
    data object CancelWaiting : ClientMessage

    /** Live progress of the current cast, relayed to the opponent. */
    @Serializable @SerialName("live")
    data class Live(val status: LiveStatus) : ClientMessage

    @Serializable @SerialName("cast_finished")
    data class CastFinished(val turn: BattleTurn, val cast: CastDto) : ClientMessage

    @Serializable @SerialName("forfeit")
    data object Forfeit : ClientMessage
}

@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("welcome")
    data class Welcome(val protocolVersion: Int = OnlineProtocol.VERSION) : ServerMessage

    @Serializable @SerialName("room_created")
    data class RoomCreated(val code: String, val mode: BattleMode) : ServerMessage

    @Serializable @SerialName("waiting")
    data class Waiting(val mode: BattleMode) : ServerMessage

    @Serializable @SerialName("waiting_cancelled")
    data object WaitingCancelled : ServerMessage

    @Serializable @SerialName("match_started")
    data class MatchStarted(
        val mode: BattleMode,
        val seed: Long,
        val yourIndex: Int,
        val playerNames: List<String>,
        val playerTitles: List<String?> = emptyList(),
        val playerFrames: List<String?> = emptyList(),
        val playerIds: List<String?> = emptyList(),
    ) : ServerMessage

    @Serializable @SerialName("opponent_live")
    data class OpponentLive(val status: LiveStatus) : ServerMessage

    /** A validated cast. Sent to both players, including the one who fished it. */
    @Serializable @SerialName("cast_recorded")
    data class CastRecorded(val turn: BattleTurn, val cast: CastDto) : ServerMessage

    /** [winner] is a player index, or null for a draw. */
    @Serializable @SerialName("match_over")
    data class MatchOver(val winner: Int?, val byForfeit: Boolean, val reason: String? = null) : ServerMessage

    @Serializable @SerialName("error")
    data class Error(val message: String) : ServerMessage
}

@Serializable
data class LiveStatus(
    val castNumber: Int,
    val depth: Float,
    val points: Int,
    val fish: Int,
    val ascending: Boolean,
)

@Serializable
data class CatchDto(
    val speciesId: String,
    val sparkling: Boolean,
    val depth: Float,
    val points: Int = 0,
    val byLastBump: Boolean = false,
    val bumpedOnDescent: Boolean = false,
    val caughtAt: Float? = null,
    val whileFleeing: Boolean = false,
    val afterWallTurn: Boolean = false,
)

@Serializable
data class SnagDto(val kind: ObstacleKind, val depth: Float, val points: Int = 0)

@Serializable
data class CastDto(
    val catches: List<CatchDto>,
    val maxDepth: Float,
    val touchesUsed: Int,
    val reverseReason: ReverseReason? = null,
    val durationSec: Float,
    val snags: List<SnagDto> = emptyList(),
    val bumps: List<BumpKind> = emptyList(),
    val bumpTimes: List<Float> = emptyList(),
    val freePasses: Int = 0,
    val steered: Boolean = true,
    /** Different fish brushed on the way up with a full line (the Crowd surfer secret). */
    val fullLineTouches: Int = 0,
    /** Tackle the player used (both change only what they see, so both are allowed). */
    val lanternUsed: Boolean = false,
    val detectorUsed: Boolean = false,
)

fun BattleCast.toDto(): CastDto = CastDto(
    catches = result.catches.map {
        CatchDto(
            it.species.id, it.sparkling, it.depth, it.points, it.byLastBump, it.bumpedOnDescent,
            caughtAt = it.caughtAt.takeIf { t -> t.isFinite() }, whileFleeing = it.whileFleeing, afterWallTurn = it.afterWallTurn,
        )
    },
    maxDepth = result.maxDepth,
    touchesUsed = result.touchesUsed,
    reverseReason = result.reverseReason,
    durationSec = durationSec,
    snags = result.snags.map { SnagDto(it.kind, it.depth, it.points) },
    bumps = result.bumps,
    bumpTimes = result.bumpTimes,
    freePasses = result.freePasses,
    steered = result.steered,
    fullLineTouches = result.fullLineTouches,
    lanternUsed = result.lanternUsed,
    detectorUsed = result.detectorUsed,
)

object CastValidation {
    private const val DEPTH_TOLERANCE = 1f
    /** Fish come up deepest first; a big fish's head can reach the hook a few meters early. */
    private const val ASCENT_ORDER_TOLERANCE = 6f
    /** Fish heads passed in the bump-free zone can't plausibly exceed this. */
    private const val MAX_FREE_PASSES = 30
    private const val MIN_DURATION = 1f
    private const val MAX_DURATION = 600f

    /**
     * Rebuilds a cast from what a client reported, or returns null if it is impossible with
     * [specs]: unknown species, fish outside its habitat or beyond the rope, too much weight, or a
     * last-bump hook that couldn't have happened (more than one, without using every bump, or in
     * the bump-free zone), bump facts that don't add up, or catches reordered to fake a Ladder.
     * Points and fishing-secret bonuses are always recomputed, never taken from the client.
     */
    fun toBattleCast(dto: CastDto, turn: BattleTurn, specs: RopeSpecs): BattleCast? {
        val catches = ArrayList<CatchRecord>()
        for (c in dto.catches) {
            val species = FishCatalog.byId(c.speciesId) ?: return null
            if (!c.depth.isFinite()) return null
            if (c.depth < species.minDepth - DEPTH_TOLERANCE || c.depth > species.maxDepth + DEPTH_TOLERANCE) return null
            if (c.depth > specs.lengthM + DEPTH_TOLERANCE) return null
            if (species.lastBumpOnly && !c.byLastBump) return null
            if (c.whileFleeing && !c.bumpedOnDescent) return null
            val at = c.caughtAt
            if (at != null && (!at.isFinite() || at < 0f || at > dto.durationSec + 1f)) return null
            catches += CatchRecord(
                species, c.sparkling, c.depth, Scoring.points(species, c.depth, c.sparkling), c.byLastBump, c.bumpedOnDescent,
                caughtAt = at ?: Float.NaN, whileFleeing = c.whileFleeing, afterWallTurn = c.afterWallTurn,
            )
        }
        val snags = ArrayList<SnagRecord>()
        for (sn in dto.snags) {
            if (!sn.depth.isFinite()) return null
            if (sn.depth < sn.kind.minDepth - DEPTH_TOLERANCE || sn.depth > sn.kind.maxDepth + DEPTH_TOLERANCE) return null
            if (sn.depth > specs.lengthM + DEPTH_TOLERANCE) return null
            snags += SnagRecord(sn.kind, sn.depth, Scoring.snagPoints(sn.kind, sn.depth))
        }
        val lastBumpHooks = catches.filter { it.byLastBump }
        if (lastBumpHooks.size + snags.size > 1) return null
        if (lastBumpHooks.size + snags.size == 1) {
            if (dto.touchesUsed != GameConfig.DESCENT_TOUCH_LIMIT) return null
            val depth = lastBumpHooks.firstOrNull()?.depth ?: snags.first().depth
            if (depth < GameConfig.BUMP_FREE_DEPTH - DEPTH_TOLERANCE) return null
        }
        // What each bump hit must add up with the rest of the cast.
        if (dto.bumps.size != dto.touchesUsed) return null
        if (lastBumpHooks.isNotEmpty() && dto.bumps.lastOrNull() != BumpKind.FISH_HEAD) return null
        if (snags.isNotEmpty() && dto.bumps.lastOrNull() != BumpKind.of(snags.first().kind)) return null
        val reencountered = catches.count { it.bumpedOnDescent && !it.byLastBump }
        if (reencountered + lastBumpHooks.size > dto.bumps.count { it == BumpKind.FISH_HEAD }) return null
        if (lastBumpHooks.any { it.bumpedOnDescent }) return null
        val ascent = catches.filter { !it.byLastBump }
        if (ascent.zipWithNext().any { (a, b) -> b.depth > a.depth + ASCENT_ORDER_TOLERANCE }) return null
        if (ascent.zipWithNext().any { (a, b) -> a.caughtAt.isFinite() && b.caughtAt.isFinite() && b.caughtAt < a.caughtAt }) return null
        if (dto.bumpTimes.isNotEmpty()) {
            if (dto.bumpTimes.size != dto.bumps.size) return null
            if (dto.bumpTimes.any { !it.isFinite() || it < 0f || it > dto.durationSec + 1f }) return null
            if (dto.bumpTimes.zipWithNext().any { (a, b) -> b < a }) return null
        }
        if (dto.freePasses !in 0..MAX_FREE_PASSES) return null
        // Full-line touches: at most one per fish row the hook rose through, and only with a
        // line that really was full near the surface (nothing living up there could still fit).
        if (dto.fullLineTouches < 0) return null
        if (dto.fullLineTouches > 0) {
            val rows = (dto.maxDepth.coerceAtLeast(0f) / (GameConfig.FISH_SPACING * 0.5f)).toInt() + 2
            if (dto.fullLineTouches > rows) return null
            val carried = catches.sumOf { it.species.weightKg.toDouble() } + snags.sumOf { it.kind.weightKg.toDouble() }
            val room = specs.maxWeightKg - carried + 1e-3
            if (FishCatalog.lightestAbove(GameConfig.FIRST_FISH_DEPTH + 1f) <= room) return null
        }

        // The last-bump hook always holds; fish caught on the way up must fit alongside it.
        val hookedWeight = lastBumpHooks.sumOf { it.species.weightKg.toDouble() } + snags.sumOf { it.kind.weightKg.toDouble() }
        val ascentWeight = catches.filter { !it.byLastBump }.sumOf { it.species.weightKg.toDouble() }
        if (ascentWeight > 0.0 && hookedWeight + ascentWeight > specs.maxWeightKg + 1e-3) return null
        val maxDuration = turn.timeLimitSec ?: MAX_DURATION
        val duration = if (dto.durationSec.isFinite()) dto.durationSec else maxDuration
        val result = CastResult(
            catches = catches,
            maxDepth = if (dto.maxDepth.isFinite()) dto.maxDepth.coerceIn(0f, specs.lengthM) else 0f,
            touchesUsed = dto.touchesUsed.coerceIn(0, GameConfig.DESCENT_TOUCH_LIMIT),
            reverseReason = dto.reverseReason,
            // Lanterns and detectors are allowed in battles; secrets that need the dark check them.
            lanternUsed = dto.lanternUsed,
            detectorUsed = dto.detectorUsed,
            snags = snags,
            bumps = dto.bumps,
            lineStrengthKg = specs.maxWeightKg,
            ropeLengthM = specs.lengthM,
            bumpTimes = dto.bumpTimes,
            freePasses = dto.freePasses,
            steered = dto.steered,
            fullLineTouches = dto.fullLineTouches,
            // The battle clock comes from the server's own turn, never from the client.
            timeLeftAtStart = turn.timeLimitSec,
        )
        return BattleCast(result, duration.coerceIn(minOf(MIN_DURATION, maxDuration), maxDuration))
    }
}
