package com.deepline.fishing.core.online

import com.deepline.fishing.core.BattleCast
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BattleTurn
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CatchRecord
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.Scoring
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
    const val VERSION = 1

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
    data class Hello(val name: String, val protocolVersion: Int = OnlineProtocol.VERSION) : ClientMessage

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
data class CatchDto(val speciesId: String, val sparkling: Boolean, val depth: Float, val points: Int = 0)

@Serializable
data class CastDto(
    val catches: List<CatchDto>,
    val maxDepth: Float,
    val touchesUsed: Int,
    val reverseReason: ReverseReason? = null,
    val durationSec: Float,
)

fun BattleCast.toDto(): CastDto = CastDto(
    catches = result.catches.map { CatchDto(it.species.id, it.sparkling, it.depth, it.points) },
    maxDepth = result.maxDepth,
    touchesUsed = result.touchesUsed,
    reverseReason = result.reverseReason,
    durationSec = durationSec,
)

object CastValidation {
    private const val DEPTH_TOLERANCE = 1f
    private const val MIN_DURATION = 1f
    private const val MAX_DURATION = 600f

    /**
     * Rebuilds a cast from what a client reported, or returns null if it is impossible with
     * [specs] (unknown species, fish outside its habitat or beyond the rope, too much weight).
     * Points are always recomputed from species, depth and Sparkling, never taken from the client.
     */
    fun toBattleCast(dto: CastDto, turn: BattleTurn, specs: RopeSpecs): BattleCast? {
        val catches = ArrayList<CatchRecord>()
        for (c in dto.catches) {
            val species = FishCatalog.byId(c.speciesId) ?: return null
            if (!c.depth.isFinite()) return null
            if (c.depth < species.minDepth - DEPTH_TOLERANCE || c.depth > species.maxDepth + DEPTH_TOLERANCE) return null
            if (c.depth > specs.lengthM + DEPTH_TOLERANCE) return null
            catches += CatchRecord(species, c.sparkling, c.depth, Scoring.points(species, c.depth, c.sparkling))
        }
        if (catches.sumOf { it.species.weightKg.toDouble() } > specs.maxWeightKg + 1e-3) return null
        val maxDuration = turn.timeLimitSec ?: MAX_DURATION
        val duration = if (dto.durationSec.isFinite()) dto.durationSec else maxDuration
        val result = CastResult(
            catches = catches,
            maxDepth = if (dto.maxDepth.isFinite()) dto.maxDepth.coerceIn(0f, specs.lengthM) else 0f,
            touchesUsed = dto.touchesUsed.coerceIn(0, GameConfig.DESCENT_TOUCH_LIMIT),
            reverseReason = dto.reverseReason,
            lanternUsed = false,
        )
        return BattleCast(result, duration.coerceIn(minOf(MIN_DURATION, maxDuration), maxDuration))
    }
}
