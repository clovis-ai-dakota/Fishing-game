package com.deepline.fishing.server

import com.deepline.fishing.core.BattleCast
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.online.CastDto
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.social.AttemptStart
import com.deepline.fishing.core.social.BoardEntry
import com.deepline.fishing.core.social.BoardKind
import com.deepline.fishing.core.social.BoardResponse
import com.deepline.fishing.core.social.BoardView
import com.deepline.fishing.core.social.ChallengeDto
import com.deepline.fishing.core.social.ClubClaim
import com.deepline.fishing.core.social.ClubDto
import com.deepline.fishing.core.social.ClubMember
import com.deepline.fishing.core.social.ClubRole
import com.deepline.fishing.core.social.ClubSummary
import com.deepline.fishing.core.social.Clubs
import com.deepline.fishing.core.social.FriendDto
import com.deepline.fishing.core.social.FriendsResponse
import com.deepline.fishing.core.social.Leaderboards
import com.deepline.fishing.core.social.MyClub
import com.deepline.fishing.core.social.PlayerCard
import com.deepline.fishing.core.social.Profile
import com.deepline.fishing.core.social.SocialApi
import com.deepline.fishing.core.social.SocialProblem
import com.deepline.fishing.core.social.SubmitResponse
import com.deepline.fishing.core.social.TopClubs
import com.deepline.fishing.core.social.TournamentInfo
import com.deepline.fishing.core.social.TournamentResult
import com.deepline.fishing.core.social.Tournaments
import com.deepline.fishing.core.social.WaterCheck
import com.deepline.fishing.core.social.Weeks
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** A social call that can't be done, and why (sent back as a [SocialProblem]). */
class SocialFailure(val problem: SocialProblem) : Exception(problem.name)

private fun fail(problem: SocialProblem): Nothing = throw SocialFailure(problem)

/**
 * One kind of entity kept as one JSON file per id in [dir] (only in memory when [dir] is null, for
 * tests). Files are written to a temporary file and then moved over the old one, so a crash never
 * leaves half a file.
 */
internal class JsonFiles<T>(private val dir: File?, private val serializer: KSerializer<T>, private val json: Json) {
    private val log = LoggerFactory.getLogger(JsonFiles::class.java)

    init {
        dir?.mkdirs()
    }

    private fun file(id: String) = File(dir, "$id.json")

    fun read(id: String): T? {
        val f = dir?.let { file(id) }?.takeIf { it.exists() } ?: return null
        return try {
            json.decodeFromString(serializer, f.readText())
        } catch (e: Exception) {
            log.error("Unreadable file {}", f, e)
            null
        }
    }

    fun write(id: String, value: T) {
        val d = dir ?: return
        val tmp = File(d, "$id.json.tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        try {
            Files.move(tmp.toPath(), file(id).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file(id).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun delete(id: String) {
        dir?.let { file(id).delete() }
    }

    fun ids(): List<String> = dir?.listFiles { f -> f.name.endsWith(".json") }?.map { it.name.removeSuffix(".json") }.orEmpty()
}

/**
 * Friends, weekly leaderboards, the weekly tournament and clubs (see [SocialApi]). Players are
 * authenticated by [accounts] (public id + secret, the same as gifts). Data is kept as one JSON
 * file per player (`players/`), per week (`weeks/`: leaderboards and the tournament, which reset
 * on Monday 00:00 UTC simply because a new week starts a new file) and per club (`clubs/`) under
 * [dir]. Every method holds one lock, so concurrent requests can't interleave; old weeks are
 * deleted once their rewards can't be claimed any more.
 *
 * Scores are server-authoritative: they only come from casts the server validated itself
 * (tournament attempts, checked against the week's water, and online battle casts).
 */
class SocialStore(
    private val accounts: AccountStore,
    dir: File? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Keeps each week's tournament water secret until the week starts (`DEEPLINE_TOURNAMENT_SALT`). */
    private val tournamentSalt: Long = 0L,
    /** Clubs ship in a later app update; `DEEPLINE_CLUBS=off` answers every club call with 404. */
    val clubsEnabled: Boolean = true,
    private val random: Random = SecureRandom().asKotlinRandom(),
) {
    @Serializable
    data class FriendRequest(val from: String, val atMs: Long)

    @Serializable
    data class Player(
        val id: String,
        val profile: Profile = Profile(),
        val lastSeenMs: Long = 0L,
        val friends: List<String> = emptyList(),
        val incoming: List<FriendRequest> = emptyList(),
        val outgoing: List<String> = emptyList(),
        val challenges: List<ChallengeDto> = emptyList(),
        val club: String? = null,
        /** Clubs the player was removed from, and when they may rejoin (epoch ms). */
        val rejoinAfter: Map<String, Long> = emptyMap(),
    )

    @Serializable
    data class Score(val value: Long, val speciesId: String? = null, val atMs: Long = 0L)

    @Serializable
    data class Attempt(val number: Int, val startedAtMs: Long, val points: Int? = null)

    @Serializable
    data class WeekEntry(
        val id: String,
        /** The player's league: players are grouped by the order they first scored in the week. */
        val league: Int,
        val boards: Map<BoardKind, Score> = emptyMap(),
        val attempts: List<Attempt> = emptyList(),
        val tournamentBest: Int? = null,
        val tournamentBestAtMs: Long = 0L,
        /** The app has given this week's tournament reward. */
        val rewardClaimed: Boolean = false,
    )

    @Serializable
    data class WeekRecord(val week: Long, val entries: Map<String, WeekEntry> = emptyMap())

    @Serializable
    data class Membership(val id: String, val role: ClubRole, val joinedAtMs: Long)

    @Serializable
    data class DayCount(val day: Long, val fish: Int)

    @Serializable
    data class ClubWeek(
        /** Fish each member added this week (kept when they leave, as in most games' clan goals). */
        val fish: Map<String, Int> = emptyMap(),
        val today: Map<String, DayCount> = emptyMap(),
        /** Goal tiers each member's app has given them. */
        val claimed: Map<String, List<Int>> = emptyMap(),
    )

    @Serializable
    data class Club(
        val id: String,
        val code: String,
        val adjective: Int,
        val noun: Int,
        val createdAtMs: Long,
        val members: List<Membership>,
        val weeks: Map<Long, ClubWeek> = emptyMap(),
    ) {
        val owner: String? get() = members.firstOrNull { it.role == ClubRole.OWNER }?.id
    }

    private val log = LoggerFactory.getLogger(SocialStore::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val limiter = RateLimiter(clock)
    private val playerFiles = JsonFiles(dir?.let { File(it, "players") }, Player.serializer(), json)
    private val weekFiles = JsonFiles(dir?.let { File(it, "weeks") }, WeekRecord.serializer(), json)
    private val clubFiles = JsonFiles(dir?.let { File(it, "clubs") }, Club.serializer(), json)
    private val players = HashMap<String, Player>()
    private val weeks = HashMap<Long, WeekRecord>()
    private val clubs = HashMap<String, Club>()
    private val clubCodes = HashMap<String, String>()

    init {
        // Clubs are few and small: all of them stay in memory (for codes and the top clubs).
        for (id in clubFiles.ids()) clubFiles.read(id)?.let { c ->
            clubs[c.id] = c
            clubCodes[c.code] = c.id
        }
        pruneWeeks()
    }

    // ---- Plumbing ------------------------------------------------------------------------------------

    private fun now() = clock()

    private fun player(id: String): Player = players[id] ?: playerFiles.read(id)?.also { players[id] = it } ?: Player(id)

    private fun save(p: Player) {
        players[p.id] = p
        playerFiles.write(p.id, p)
    }

    private fun weekRecord(w: Long): WeekRecord {
        val current = Weeks.indexAt(now())
        if (current != prunedAt) pruneWeeks()
        return weeks[w] ?: (weekFiles.read(w.toString()) ?: WeekRecord(w)).also { weeks[w] = it }
    }

    private fun save(r: WeekRecord) {
        weeks[r.week] = r
        weekFiles.write(r.week.toString(), r)
    }

    private fun save(c: Club) {
        val current = Weeks.indexAt(now())
        val kept = c.copy(weeks = c.weeks.filterKeys { it >= current - 1 })
        clubs[c.id] = kept
        clubCodes[c.code] = c.id
        clubFiles.write(c.id, kept)
    }

    /** Forgets weeks whose rewards can't be claimed any more (memory and files). */
    private var prunedAt = Long.MIN_VALUE

    private fun pruneWeeks() {
        prunedAt = Weeks.indexAt(now())
        val oldest = prunedAt - GameConfig.TOURNAMENT_CLAIM_WEEKS - 1
        weeks.keys.filter { it < oldest }.forEach { weeks.remove(it) }
        for (id in weekFiles.ids()) if ((id.toLongOrNull() ?: continue) < oldest) weekFiles.delete(id)
    }

    /** Checks the id and secret (registering the id on first use); [write] calls are rate-limited. */
    private fun auth(id: String, secret: String, write: Boolean = false) {
        try {
            accounts.authenticate(id, secret)
        } catch (e: AccountStore.Failure) {
            fail(if (e.problem == AccountStore.Problem.FORBIDDEN) SocialProblem.WRONG_SECRET else SocialProblem.BAD_REQUEST)
        }
        if (write && !limiter.allow("write:$id", WRITES_PER_MINUTE, MINUTE_MS)) fail(SocialProblem.TOO_MANY_REQUESTS)
    }

    /** Updates what others see of the player and when they were last seen. */
    private fun touch(id: String, profile: Profile?) {
        val p = player(id)
        val clean = profile?.clean() ?: p.profile
        val now = now()
        if (clean != p.profile || now - p.lastSeenMs > SEEN_RESOLUTION_MS) save(p.copy(profile = clean, lastSeenMs = now))
    }

    private fun online(p: Player, now: Long) = now - p.lastSeenMs < GameConfig.ONLINE_WINDOW_MINUTES * MINUTE_MS

    private fun card(id: String): PlayerCard = player(id).let { PlayerCard(id, it.profile.name, it.profile.title, it.profile.titleFrame) }

    private fun target(id: String?, me: String): String {
        if (id == null || !PlayerId.isValid(id) || id == me) fail(SocialProblem.BAD_REQUEST)
        return id
    }

    /** The player was seen on the battle server (an authenticated connection). */
    fun seen(id: String) = synchronized(lock) {
        if (PlayerId.isValid(id)) touch(id, null)
    }

    // ---- Friends -------------------------------------------------------------------------------------

    fun friends(id: String, secret: String, profile: Profile?): FriendsResponse = synchronized(lock) {
        auth(id, secret)
        touch(id, profile)
        val me = player(id)
        val now = now()
        val rec = weekRecord(Weeks.indexAt(now))
        FriendsResponse(
            friends = me.friends.map { fid ->
                val f = player(fid)
                val e = rec.entries[fid]
                FriendDto(
                    fid, f.profile.name, f.profile.title, f.profile.titleFrame, online(f, now),
                    weekBestCast = e?.boards?.get(BoardKind.BEST_CAST)?.value?.toInt(),
                    weekDeepestM = e?.boards?.get(BoardKind.DEEPEST)?.value?.let(Leaderboards::depthOf),
                    weekTournament = e?.tournamentBest,
                )
            }.sortedWith(compareByDescending<FriendDto> { it.online }.thenBy { it.name.lowercase() }),
            incoming = me.incoming.map { card(it.from) },
            // Asking someone doesn't reveal the name they chose: only accepting does.
            outgoing = me.outgoing.map { PlayerCard(it, Leaderboards.anglerName(it)) },
            challenges = me.challenges.filter { it.expiresAtMs > now && it.from in me.friends },
        )
    }

    fun requestFriend(id: String, secret: String, to: String?) = synchronized(lock) {
        auth(id, secret, write = true)
        val target = target(to, id)
        if (!accounts.isRegistered(target)) fail(SocialProblem.UNKNOWN_PLAYER)
        val me = player(id)
        if (target in me.friends || target in me.outgoing) return@synchronized
        // They asked first: asking back makes you friends.
        if (me.incoming.any { it.from == target }) return@synchronized makeFriends(id, target)
        if (!limiter.allow("friend:$id", GameConfig.FRIEND_REQUESTS_PER_DAY, DAY_MS)) fail(SocialProblem.TOO_MANY_REQUESTS)
        if (me.friends.size >= GameConfig.MAX_FRIENDS) fail(SocialProblem.FRIENDS_FULL)
        val them = player(target)
        if (them.friends.size >= GameConfig.MAX_FRIENDS || them.incoming.size >= GameConfig.MAX_FRIEND_REQUESTS) fail(SocialProblem.THEIR_FRIENDS_FULL)
        save(them.copy(incoming = them.incoming + FriendRequest(id, now())))
        save(me.copy(outgoing = me.outgoing + target))
    }

    private fun makeFriends(a: String, b: String) {
        val pa = player(a)
        val pb = player(b)
        if (pa.friends.size >= GameConfig.MAX_FRIENDS) fail(SocialProblem.FRIENDS_FULL)
        if (pb.friends.size >= GameConfig.MAX_FRIENDS) fail(SocialProblem.THEIR_FRIENDS_FULL)
        save(pa.copy(friends = (pa.friends + b).distinct(), incoming = pa.incoming.filterNot { it.from == b }, outgoing = pa.outgoing - b))
        save(pb.copy(friends = (pb.friends + a).distinct(), incoming = pb.incoming.filterNot { it.from == a }, outgoing = pb.outgoing - a))
    }

    fun acceptFriend(id: String, secret: String, from: String?) = synchronized(lock) {
        auth(id, secret, write = true)
        val target = target(from, id)
        if (player(id).incoming.none { it.from == target }) fail(SocialProblem.UNKNOWN_PLAYER)
        makeFriends(id, target)
    }

    /** Declines [other]'s request, or takes back the player's own request to them. Silent either way. */
    fun declineFriend(id: String, secret: String, other: String?) = synchronized(lock) {
        auth(id, secret, write = true)
        dropRequests(id, target(other, id))
    }

    private fun dropRequests(a: String, b: String) {
        val pa = player(a)
        val pb = player(b)
        if (pa.incoming.any { it.from == b } || b in pa.outgoing) {
            save(pa.copy(incoming = pa.incoming.filterNot { it.from == b }, outgoing = pa.outgoing - b))
        }
        if (pb.incoming.any { it.from == a } || a in pb.outgoing) {
            save(pb.copy(incoming = pb.incoming.filterNot { it.from == a }, outgoing = pb.outgoing - a))
        }
    }

    /** Ends a friendship on both sides (the other player isn't told). */
    fun removeFriend(id: String, secret: String, other: String?) = synchronized(lock) {
        auth(id, secret, write = true)
        val target = target(other, id)
        unfriend(id, target)
        dropRequests(id, target)
    }

    private fun unfriend(a: String, b: String) {
        val pa = player(a)
        val pb = player(b)
        if (b in pa.friends || pa.challenges.any { it.from == b }) save(pa.copy(friends = pa.friends - b, challenges = pa.challenges.filterNot { it.from == b }))
        if (a in pb.friends || pb.challenges.any { it.from == a }) save(pb.copy(friends = pb.friends - a, challenges = pb.challenges.filterNot { it.from == a }))
    }

    /** Invites a friend into the battle room [code] the player just created. */
    fun challenge(id: String, secret: String, to: String, code: String, mode: BattleMode) = synchronized(lock) {
        auth(id, secret, write = true)
        val target = target(to, id)
        val room = OnlineProtocol.normalizeRoomCode(code)
        if (room.length != OnlineProtocol.ROOM_CODE_LENGTH || room.any { it !in OnlineProtocol.ROOM_CODE_ALPHABET }) fail(SocialProblem.BAD_REQUEST)
        val me = player(id)
        if (target !in me.friends) fail(SocialProblem.NOT_FRIENDS)
        if (!limiter.allow("challenge:$id", CHALLENGES_PER_HOUR, HOUR_MS)) fail(SocialProblem.TOO_MANY_REQUESTS)
        val now = now()
        val them = player(target)
        val waiting = them.challenges.filter { it.expiresAtMs > now && it.from != id }.takeLast(MAX_CHALLENGES - 1)
        val challenge = ChallengeDto(id, me.profile.name, room, mode, now + GameConfig.CHALLENGE_MINUTES * MINUTE_MS)
        save(them.copy(challenges = waiting + challenge))
    }

    // ---- Leaderboards --------------------------------------------------------------------------------

    private fun ranking(rec: WeekRecord, board: BoardKind): List<Pair<String, Score>> =
        rec.entries.values.mapNotNull { e -> e.boards[board]?.let { e.id to it } }
            .sortedWith(compareByDescending<Pair<String, Score>> { it.second.value }.thenBy { it.second.atMs }.thenBy { it.first })

    /** A row as [viewer] sees it: their friends and themselves by name, strangers by angler name. */
    private fun row(rank: Int, pid: String, value: Long, speciesId: String?, viewer: Player): BoardEntry {
        val p = player(pid)
        val you = pid == viewer.id
        val friend = pid in viewer.friends
        val known = you || friend
        return BoardEntry(
            rank = rank,
            name = if (known) p.profile.name else Leaderboards.anglerName(pid),
            value = value,
            speciesId = speciesId,
            id = pid.takeIf { known },
            title = p.profile.title,
            titleFrame = p.profile.titleFrame,
            you = you,
            friend = friend,
        )
    }

    fun leaderboard(id: String, secret: String, board: BoardKind, view: BoardView): BoardResponse = synchronized(lock) {
        auth(id, secret)
        touch(id, null)
        val w = Weeks.indexAt(now())
        val rec = weekRecord(w)
        val me = player(id)
        val mine = rec.entries[id]
        val ranked = ranking(rec, board)
        val pool = when (view) {
            BoardView.GLOBAL -> ranked
            BoardView.LEAGUE -> if (mine == null) emptyList() else ranked.filter { rec.entries[it.first]?.league == mine.league }
            BoardView.FRIENDS -> ranked.filter { it.first == id || it.first in me.friends }
        }
        val shown = if (view == BoardView.GLOBAL) pool.take(GameConfig.LEADERBOARD_TOP) else pool
        val myIndex = pool.indexOfFirst { it.first == id }
        BoardResponse(
            board, view, w, Weeks.endOf(w),
            entries = shown.mapIndexed { i, (pid, s) -> row(i + 1, pid, s.value, s.speciesId, me) },
            me = if (myIndex >= 0) pool[myIndex].let { (pid, s) -> row(myIndex + 1, pid, s.value, s.speciesId, me) } else null,
            players = pool.size,
        )
    }

    /** Puts a validated cast's values on the week's boards (only improvements count). */
    private fun recordScores(rec: WeekRecord, id: String, result: CastResult, now: Long): WeekRecord {
        val values = Leaderboards.valuesOf(result).filterValues { it.value > 0 }
        val old = rec.entries[id]
        if (values.isEmpty() && old == null) return rec
        var e = old ?: newEntry(rec, id)
        for ((kind, v) in values) {
            val best = e.boards[kind]
            if (best == null || v.value > best.value) e = e.copy(boards = e.boards + (kind to Score(v.value, v.speciesId, now)))
        }
        return if (e == old) rec else rec.copy(entries = rec.entries + (id to e))
    }

    private fun newEntry(rec: WeekRecord, id: String) = WeekEntry(id, league = rec.entries.size / GameConfig.LEAGUE_SIZE)

    /**
     * An online battle cast, already validated (and its points recomputed) by the match: it counts
     * on the boards when every catch is really in that cast's water.
     */
    fun recordBattleCast(id: String, cast: BattleCast, dto: CastDto, seed: Long) = synchronized(lock) {
        if (!PlayerId.isValid(id)) return@synchronized
        if (!WaterCheck.matches(dto, seed, OnlineProtocol.tournamentSpecs)) {
            log.info("Battle cast of {} doesn't match its water: not ranked", id)
            return@synchronized
        }
        val now = now()
        val rec = weekRecord(Weeks.indexAt(now))
        val next = recordScores(rec, id, cast.result, now)
        if (next !== rec) save(next)
    }

    // ---- Tournament ----------------------------------------------------------------------------------

    private fun tournamentRanking(rec: WeekRecord): List<WeekEntry> =
        rec.entries.values.filter { it.tournamentBest != null }
            .sortedWith(compareByDescending<WeekEntry> { it.tournamentBest }.thenBy { it.tournamentBestAtMs }.thenBy { it.id })

    fun tournament(id: String, secret: String): TournamentInfo = synchronized(lock) {
        auth(id, secret)
        touch(id, null)
        val now = now()
        val w = Weeks.indexAt(now)
        val rec = weekRecord(w)
        val me = player(id)
        val mine = rec.entries[id]
        val ranked = tournamentRanking(rec)
        val myIndex = ranked.indexOfFirst { it.id == id }
        TournamentInfo(
            week = w,
            startsAtMs = Weeks.startOf(w),
            endsAtMs = Weeks.endOf(w),
            attemptsMax = GameConfig.TOURNAMENT_ATTEMPTS,
            attemptsUsed = mine?.attempts?.size ?: 0,
            best = mine?.tournamentBest,
            rank = (myIndex + 1).takeIf { myIndex >= 0 },
            participants = ranked.size,
            top = ranked.take(TOURNAMENT_TOP).mapIndexed { i, e -> row(i + 1, e.id, e.tournamentBest!!.toLong(), null, me) },
            rewards = pendingRewards(id, w, now),
        )
    }

    private fun pendingRewards(id: String, current: Long, now: Long): List<TournamentResult> =
        ((current - GameConfig.TOURNAMENT_CLAIM_WEEKS) until current).mapNotNull { w ->
            if (!Tournaments.isClaimable(w, now)) return@mapNotNull null
            val rec = weekRecord(w)
            val e = rec.entries[id] ?: return@mapNotNull null
            val best = e.tournamentBest ?: return@mapNotNull null
            if (e.rewardClaimed) return@mapNotNull null
            val ranked = tournamentRanking(rec)
            val rank = ranked.indexOfFirst { it.id == id } + 1
            val tier = Tournaments.tierFor(rank, ranked.size) ?: return@mapNotNull null
            TournamentResult(w, rank, ranked.size, best, tier)
        }

    /** Uses one of the week's attempts and reveals the week's water. Quitting it scores 0. */
    fun startAttempt(id: String, secret: String): AttemptStart = synchronized(lock) {
        auth(id, secret, write = true)
        val now = now()
        val w = Weeks.indexAt(now)
        val rec = weekRecord(w)
        val e = rec.entries[id] ?: newEntry(rec, id)
        if (e.attempts.size >= GameConfig.TOURNAMENT_ATTEMPTS) fail(SocialProblem.NO_ATTEMPTS_LEFT)
        val number = e.attempts.size + 1
        // Starting counts as taking part (with 0 until a score comes in).
        val next = e.copy(
            attempts = e.attempts + Attempt(number, now),
            tournamentBest = e.tournamentBest ?: 0,
            tournamentBestAtMs = if (e.tournamentBest == null) now else e.tournamentBestAtMs,
        )
        save(rec.copy(entries = rec.entries + (id to next)))
        AttemptStart(w, number, Tournaments.seedOf(w, tournamentSalt), now + GameConfig.TOURNAMENT_ATTEMPT_MINUTES * MINUTE_MS)
    }

    /**
     * Scores an attempt: validated like a battle cast and against the week's water, points
     * recomputed. A rejected or late attempt stays at 0 (it can't be tried again).
     */
    fun submitAttempt(id: String, secret: String, week: Long, attempt: Int, dto: CastDto): SubmitResponse = synchronized(lock) {
        auth(id, secret, write = true)
        val now = now()
        // An attempt lasts minutes: only this week's, or one started just before Monday, can still
        // be scored. Other weeks (they come from the app) are refused before being looked up.
        val current = Weeks.indexAt(now)
        if (week > current) fail(SocialProblem.BAD_REQUEST)
        if (week < current - 1) fail(SocialProblem.ATTEMPT_EXPIRED)
        val rec = weekRecord(week)
        val e = rec.entries[id] ?: fail(SocialProblem.BAD_REQUEST)
        val a = e.attempts.firstOrNull { it.number == attempt } ?: fail(SocialProblem.BAD_REQUEST)
        if (a.points != null || now > a.startedAtMs + GameConfig.TOURNAMENT_ATTEMPT_MINUTES * MINUTE_MS) fail(SocialProblem.ATTEMPT_EXPIRED)
        val cast = Tournaments.validate(dto, attempt, Tournaments.seedOf(week, tournamentSalt))
        // A cast can't have lasted longer than the time since its attempt started.
        if (cast == null || !dto.durationSec.isFinite() || dto.durationSec * 1000f > (now - a.startedAtMs) + CLOCK_SLACK_MS) {
            val zero = e.copy(attempts = e.attempts.map { if (it.number == attempt) it.copy(points = 0) else it })
            save(rec.copy(entries = rec.entries + (id to zero)))
            fail(SocialProblem.CAST_REJECTED)
        }
        val points = cast.points
        var next = e.copy(attempts = e.attempts.map { if (it.number == attempt) it.copy(points = points) else it })
        if (points > (next.tournamentBest ?: -1)) next = next.copy(tournamentBest = points, tournamentBestAtMs = now)
        val updated = recordScores(rec.copy(entries = rec.entries + (id to next)), id, cast.result, now)
        save(updated)
        val ranked = tournamentRanking(updated)
        SubmitResponse(week, attempt, points, next.tournamentBest ?: points, ranked.indexOfFirst { it.id == id } + 1, ranked.size)
    }

    /** The app has given the reward of [week]: it's no longer offered. */
    fun claimTournament(id: String, secret: String, week: Long?) = synchronized(lock) {
        auth(id, secret, write = true)
        if (week == null || !Tournaments.isClaimable(week, now())) fail(SocialProblem.BAD_REQUEST)
        val rec = weekRecord(week)
        val e = rec.entries[id] ?: fail(SocialProblem.BAD_REQUEST)
        if (!e.rewardClaimed) save(rec.copy(entries = rec.entries + (id to e.copy(rewardClaimed = true))))
    }

    // ---- Clubs ---------------------------------------------------------------------------------------

    private fun requireClubs() {
        if (!clubsEnabled) fail(SocialProblem.CLUBS_OFF)
    }

    private fun clubOf(p: Player): Club? = p.club?.let { clubs[it] }?.takeIf { c -> c.members.any { it.id == p.id } }

    private fun clubDto(c: Club, viewer: String): ClubDto {
        val now = now()
        val w = Weeks.indexAt(now)
        val wk = c.weeks[w] ?: ClubWeek()
        val members = c.members.map { m ->
            val p = player(m.id)
            ClubMember(m.id, p.profile.name, p.profile.title, p.profile.titleFrame, m.role, wk.fish[m.id] ?: 0, online(p, now))
        }.sortedWith(compareByDescending<ClubMember> { it.weekFish }.thenBy { it.role != ClubRole.OWNER }.thenBy { it.name.lowercase() })
        val total = wk.fish.values.sum()
        return ClubDto(c.id, Clubs.name(c.adjective, c.noun) ?: "Club", c.code, w, Weeks.endOf(w), members, total, Clubs.tiersReached(total), claimable(c, viewer, w))
    }

    /** Goal tiers reached this week or last week that [id] helped with and hasn't been given yet. */
    private fun claimable(c: Club, id: String, current: Long): List<ClubClaim> = listOf(current - 1, current).flatMap { w ->
        val cw = c.weeks[w] ?: return@flatMap emptyList()
        if ((cw.fish[id] ?: 0) <= 0) return@flatMap emptyList()
        val done = cw.claimed[id].orEmpty()
        (1..Clubs.tiersReached(cw.fish.values.sum())).filter { it !in done }.map { ClubClaim(w, it) }
    }

    fun myClub(id: String, secret: String, profile: Profile?): MyClub = synchronized(lock) {
        requireClubs()
        auth(id, secret)
        touch(id, profile)
        val me = player(id)
        val club = clubOf(me)
        if (club == null && me.club != null) save(me.copy(club = null))
        MyClub(club?.let { clubDto(it, id) })
    }

    fun createClub(id: String, secret: String, adjective: Int, noun: Int, profile: Profile?): MyClub = synchronized(lock) {
        requireClubs()
        auth(id, secret, write = true)
        touch(id, profile)
        if (Clubs.name(adjective, noun) == null) fail(SocialProblem.BAD_REQUEST)
        val me = player(id)
        if (clubOf(me) != null) fail(SocialProblem.ALREADY_IN_CLUB)
        if (!limiter.allow("club:$id", GameConfig.CLUB_JOINS_PER_DAY, DAY_MS)) fail(SocialProblem.TOO_MANY_REQUESTS)
        var code: String
        do code = Clubs.randomCode(random) while (code in clubCodes)
        var clubId: String
        do clubId = "C" + Clubs.randomCode(random) + Clubs.randomCode(random) while (clubId in clubs)
        val club = Club(clubId, code, adjective, noun, now(), listOf(Membership(id, ClubRole.OWNER, now())))
        save(club)
        save(me.copy(club = clubId))
        log.info("Club {} created", clubId)
        MyClub(clubDto(club, id))
    }

    fun joinClub(id: String, secret: String, code: String, profile: Profile?): MyClub = synchronized(lock) {
        requireClubs()
        auth(id, secret, write = true)
        touch(id, profile)
        val clean = Clubs.normalizeCode(code)
        if (!Clubs.isValidCode(clean)) fail(SocialProblem.BAD_REQUEST)
        val club = clubCodes[clean]?.let { clubs[it] } ?: fail(SocialProblem.UNKNOWN_CLUB)
        val me = player(id)
        val current = clubOf(me)
        if (current?.id == club.id) return@synchronized MyClub(clubDto(club, id))
        if (current != null) fail(SocialProblem.ALREADY_IN_CLUB)
        if ((me.rejoinAfter[club.id] ?: 0L) > now()) fail(SocialProblem.REJOIN_LATER)
        if (club.members.size >= GameConfig.CLUB_MAX_MEMBERS) fail(SocialProblem.CLUB_FULL)
        if (!limiter.allow("club:$id", GameConfig.CLUB_JOINS_PER_DAY, DAY_MS)) fail(SocialProblem.TOO_MANY_REQUESTS)
        val joined = club.copy(members = club.members + Membership(id, ClubRole.MEMBER, now()))
        save(joined)
        save(me.copy(club = club.id))
        MyClub(clubDto(joined, id))
    }

    fun leaveClub(id: String, secret: String) = synchronized(lock) {
        requireClubs()
        auth(id, secret, write = true)
        val club = clubOf(player(id)) ?: fail(SocialProblem.NOT_IN_CLUB)
        removeMember(club, id)
    }

    /** Takes [member] out of [club]: an owner leaving hands the club to the longest-standing member. */
    private fun removeMember(club: Club, member: String, rejoinAfter: Long? = null) {
        val rest = club.members.filterNot { it.id == member }
        if (rest.isEmpty()) {
            clubs.remove(club.id)
            clubCodes.remove(club.code)
            clubFiles.delete(club.id)
            log.info("Club {} closed", club.id)
        } else {
            val hasOwner = rest.any { it.role == ClubRole.OWNER }
            val next = if (hasOwner) rest else {
                val heir = rest.minWith(compareBy<Membership> { it.joinedAtMs }.thenBy { it.id })
                rest.map { if (it.id == heir.id) it.copy(role = ClubRole.OWNER) else it }
            }
            save(club.copy(members = next))
        }
        val p = player(member)
        save(p.copy(club = null, rejoinAfter = if (rejoinAfter != null) p.rejoinAfter.filterValues { it > now() } + (club.id to rejoinAfter) else p.rejoinAfter))
    }

    private fun ownedClub(id: String): Club {
        val club = clubOf(player(id)) ?: fail(SocialProblem.NOT_IN_CLUB)
        if (club.owner != id) fail(SocialProblem.NOT_OWNER)
        return club
    }

    /** The owner removes a member (who can't rejoin for [GameConfig.CLUB_REJOIN_HOURS]). */
    fun kick(id: String, secret: String, member: String?) = synchronized(lock) {
        requireClubs()
        auth(id, secret, write = true)
        val target = target(member, id)
        val club = ownedClub(id)
        if (club.members.none { it.id == target }) fail(SocialProblem.UNKNOWN_PLAYER)
        removeMember(club, target, rejoinAfter = now() + GameConfig.CLUB_REJOIN_HOURS * HOUR_MS)
    }

    /** The owner hands the club to another member and becomes a member. */
    fun transfer(id: String, secret: String, member: String?) = synchronized(lock) {
        requireClubs()
        auth(id, secret, write = true)
        val target = target(member, id)
        val club = ownedClub(id)
        if (club.members.none { it.id == target }) fail(SocialProblem.UNKNOWN_PLAYER)
        save(club.copy(members = club.members.map {
            when (it.id) {
                target -> it.copy(role = ClubRole.OWNER)
                id -> it.copy(role = ClubRole.MEMBER)
                else -> it
            }
        }))
    }

    fun renameClub(id: String, secret: String, adjective: Int, noun: Int) = synchronized(lock) {
        requireClubs()
        auth(id, secret, write = true)
        if (Clubs.name(adjective, noun) == null) fail(SocialProblem.BAD_REQUEST)
        val club = ownedClub(id)
        save(club.copy(adjective = adjective, noun = noun))
    }

    /**
     * Fish the member caught in solo casts since their app last reported, for the weekly goal. The
     * app reports them, so the server caps them per member per day.
     */
    fun contribute(id: String, secret: String, fish: Int) = synchronized(lock) {
        requireClubs()
        auth(id, secret, write = true)
        if (fish < 0 || fish > GameConfig.CLUB_FISH_PER_MEMBER_PER_DAY) fail(SocialProblem.BAD_REQUEST)
        val club = clubOf(player(id)) ?: fail(SocialProblem.NOT_IN_CLUB)
        if (fish == 0) return@synchronized
        val now = now()
        val day = Math.floorDiv(now, DAY_MS)
        val w = Weeks.indexAt(now)
        val cw = club.weeks[w] ?: ClubWeek()
        val today = cw.today[id]?.takeIf { it.day == day }?.fish ?: 0
        val added = minOf(fish, GameConfig.CLUB_FISH_PER_MEMBER_PER_DAY - today)
        if (added <= 0) return@synchronized
        val next = cw.copy(fish = cw.fish + (id to (cw.fish[id] ?: 0) + added), today = cw.today + (id to DayCount(day, today + added)))
        save(club.copy(weeks = club.weeks + (w to next)))
    }

    /** The app has given these goal rewards ("week:tier"). */
    fun claimClub(id: String, secret: String, keys: List<String>) = synchronized(lock) {
        requireClubs()
        auth(id, secret, write = true)
        if (keys.size > 2 * GameConfig.CLUB_GOAL_TIERS.size) fail(SocialProblem.BAD_REQUEST)
        val club = clubOf(player(id)) ?: fail(SocialProblem.NOT_IN_CLUB)
        val open = claimable(club, id, Weeks.indexAt(now())).associateBy { it.key }
        var weeksNow = club.weeks
        for (key in keys) {
            val claim = open[key] ?: continue
            val cw = weeksNow[claim.week] ?: continue
            weeksNow = weeksNow + (claim.week to cw.copy(claimed = cw.claimed + (id to (cw.claimed[id].orEmpty() + claim.tier).distinct())))
        }
        if (weeksNow != club.weeks) save(club.copy(weeks = weeksNow))
    }

    /** This week's most active clubs (fish caught together). */
    fun topClubs(id: String, secret: String): TopClubs = synchronized(lock) {
        requireClubs()
        auth(id, secret)
        val w = Weeks.indexAt(now())
        val mine = player(id).club
        val sorted = clubs.values.map { it to (it.weeks[w]?.fish?.values?.sum() ?: 0) }
            .sortedWith(compareByDescending<Pair<Club, Int>> { it.second }.thenBy { it.first.createdAtMs })
        TopClubs(w, sorted.take(SocialApi.TOP_CLUBS).mapIndexed { i, (c, fish) ->
            ClubSummary(i + 1, Clubs.name(c.adjective, c.noun) ?: "Club", c.members.size, fish, c.id == mine)
        })
    }

    // ---- Account deletion ----------------------------------------------------------------------------

    /** The player asks to delete their social data ([SocialApi.FORGET_PATH]): see [forgetPlayer]. */
    fun forget(id: String, secret: String) = synchronized(lock) {
        auth(id, secret, write = true)
        forgetPlayer(id)
    }

    /**
     * Erases everything this store keeps about the player [id], for account deletion (the server
     * registers it as an `AccountStore` deletion listener) and for [forget]. It needs no secret:
     * the caller has already checked who is asking. Every file is swept, not just the ones the
     * player's own file points to, so nothing is left behind even if records disagree:
     * - friendships, friend requests (sent and received) and challenges, on everyone's side;
     * - club membership (an owner's club passes to its longest-standing member, an empty club is
     *   closed) and the player's contributions and claims in every club;
     * - their entries on every kept week's leaderboards and tournament (others move up a rank);
     * - their own file.
     * An id that isn't a valid player id is ignored (ids are file names).
     */
    fun forgetPlayer(id: String) = synchronized(lock) {
        if (!PlayerId.isValid(id)) return@synchronized
        // Everyone else's lists: read without caching, saved only when something changes.
        for (other in (players.keys + playerFiles.ids()).toSet() - id) {
            val o = players[other] ?: playerFiles.read(other) ?: continue
            val cleaned = o.copy(
                friends = o.friends - id,
                incoming = o.incoming.filterNot { it.from == id },
                outgoing = o.outgoing - id,
                challenges = o.challenges.filterNot { it.from == id },
            )
            if (cleaned != o) save(cleaned)
        }
        // Clubs: membership first (ownership passes on), then contributions and claims.
        for (clubId in clubs.values.filter { c -> c.members.any { it.id == id } }.map { it.id }) {
            clubs[clubId]?.let { removeMember(it, id) }
        }
        for (c in clubs.values.toList()) {
            if (c.weeks.values.any { id in it.fish || id in it.today || id in it.claimed }) {
                save(c.copy(weeks = c.weeks.mapValues { (_, cw) -> cw.copy(fish = cw.fish - id, today = cw.today - id, claimed = cw.claimed - id) }))
            }
        }
        // Leaderboards and tournaments of every week still kept.
        pruneWeeks()
        for (w in (weeks.keys + weekFiles.ids().mapNotNull { it.toLongOrNull() }).toSet()) {
            val rec = weekRecord(w)
            if (id in rec.entries) save(rec.copy(entries = rec.entries - id))
        }
        players.remove(id)
        playerFiles.delete(id)
        log.info("Social data of a player deleted")
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
        /** Social writes per player per minute. */
        const val WRITES_PER_MINUTE = 60
        const val CHALLENGES_PER_HOUR = 30
        const val MAX_CHALLENGES = 20
        const val TOURNAMENT_TOP = 10
        /** Last-seen times are only saved this often (avoids a write on every read). */
        const val SEEN_RESOLUTION_MS = 60_000L
        /** Tolerance on a cast's reported duration (it's measured by the phone). */
        const val CLOCK_SLACK_MS = 5_000L
    }
}
