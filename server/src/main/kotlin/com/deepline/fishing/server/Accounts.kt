package com.deepline.fishing.server

import com.deepline.fishing.core.PlayerId
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.online.AccountApi
import com.deepline.fishing.core.online.Gift
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Player accounts for gifts and cloud saves: one JSON file per player in [dir] (in memory when
 * [dir] is null, for tests). Only a hash of each player's secret is kept.
 */
class AccountStore(private val dir: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    @Serializable
    data class Account(
        val id: String,
        /** Null until the player first calls the API (a gift can arrive before that). */
        val secretHash: String? = null,
        val save: String? = null,
        val savedAtMs: Long = 0L,
        val gifts: List<Gift> = emptyList(),
    )

    enum class Problem { BAD_REQUEST, FORBIDDEN, TOO_BIG, NOT_FOUND }

    class Failure(val problem: Problem) : Exception(problem.name)

    private val json = Json { ignoreUnknownKeys = true }
    private val memory = HashMap<String, Account>()
    private val lock = Any()

    init {
        dir?.mkdirs()
    }

    private fun file(id: String) = File(dir, "$id.json")

    private fun read(id: String): Account? = synchronized(lock) {
        memory[id] ?: dir?.let { d -> file(id).takeIf { it.exists() }?.let { json.decodeFromString(Account.serializer(), it.readText()) } }
            ?.also { memory[id] = it }
    }

    private fun write(a: Account) = synchronized(lock) {
        memory[a.id] = a
        dir?.let {
            // Write then rename, so a crash never leaves half a file.
            val tmp = File(it, "${a.id}.json.tmp")
            tmp.writeText(json.encodeToString(Account.serializer(), a))
            tmp.renameTo(file(a.id))
        }
    }

    /** The player's account, registering it on first use; fails if the secret doesn't match. */
    fun authenticate(id: String, secret: String): Account = synchronized(lock) {
        if (!PlayerId.isValid(id) || !PlayerId.isValidSecret(secret)) throw Failure(Problem.BAD_REQUEST)
        val hash = PlayerId.hash(secret)
        val existing = read(id)
        when {
            existing == null -> Account(id, hash).also(::write)
            existing.secretHash == null -> existing.copy(secretHash = hash).also(::write)
            existing.secretHash == hash -> existing
            else -> throw Failure(Problem.FORBIDDEN)
        }
    }

    fun save(id: String, secret: String, data: String) = synchronized(lock) {
        if (data.toByteArray().size > AccountApi.MAX_SAVE_BYTES) throw Failure(Problem.TOO_BIG)
        write(authenticate(id, secret).copy(save = data, savedAtMs = clock()))
    }

    fun load(id: String, secret: String): Account = synchronized(lock) {
        authenticate(id, secret).takeIf { it.save != null } ?: throw Failure(Problem.NOT_FOUND)
    }

    /** Leaves a gift for [to]. The sender pays in the app; the server checks the item can be given. */
    fun sendGift(from: String, secret: String, fromName: String, to: String, itemId: String) = synchronized(lock) {
        authenticate(from, secret)
        val item = ShopCatalog.byId(itemId)
        if (item == null || !AccountApi.isGiftable(item) || !PlayerId.isValid(to) || to == from) throw Failure(Problem.BAD_REQUEST)
        val recipient = read(to) ?: Account(to)
        if (recipient.gifts.size >= AccountApi.MAX_INBOX) throw Failure(Problem.TOO_BIG)
        val name = com.deepline.fishing.core.online.OnlineProtocol.sanitizeName(fromName)
        write(recipient.copy(gifts = recipient.gifts + Gift(UUID.randomUUID().toString(), name, itemId, clock())))
    }

    /** The player's waiting gifts (kept until [ackGifts]). */
    fun inbox(id: String, secret: String): List<Gift> = synchronized(lock) { authenticate(id, secret).gifts }

    /** The app has given these gifts to the player: forget them. */
    fun ackGifts(id: String, secret: String, giftIds: Collection<String>) = synchronized(lock) {
        val a = authenticate(id, secret)
        if (a.gifts.any { it.id in giftIds }) write(a.copy(gifts = a.gifts.filterNot { it.id in giftIds }))
    }

    private val deletionListeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    /**
     * Anything else the server keeps about players registers here, so deleting an account erases
     * it too (Google Play and the GDPR need every piece of a player's data gone).
     */
    fun onDelete(listener: (playerId: String) -> Unit) {
        deletionListeners += listener
    }

    /**
     * Erases everything kept for the player: their file (the secret's hash, the cloud save, gifts
     * waiting) and what [onDelete] listeners keep. Needs the player's secret, like every other
     * call; an id that was never claimed (only gifts sent to it) is the first caller's, as in
     * [authenticate]. True if there was something to delete; deleting twice is harmless.
     */
    fun delete(id: String, secret: String): Boolean = synchronized(lock) {
        if (!PlayerId.isValid(id) || !PlayerId.isValidSecret(secret)) throw Failure(Problem.BAD_REQUEST)
        val existing = read(id)
        if (existing?.secretHash != null && existing.secretHash != PlayerId.hash(secret)) throw Failure(Problem.FORBIDDEN)
        memory.remove(id)
        dir?.let {
            File(it, "$id.json.tmp").delete()
            File(it, "$id.json").delete()
        }
        deletionListeners.forEach { it(id) }
        existing != null
    }
}
