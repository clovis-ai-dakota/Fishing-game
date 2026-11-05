package com.deepline.fishing.core.online

import com.deepline.fishing.core.ItemKind
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.core.ShopSection
import kotlinx.serialization.Serializable

/**
 * The battle server's small HTTP API for gifts and cloud saves (POST, JSON bodies). A player is
 * known by a public id and a secret (see `PlayerId`): the first request with an id registers it;
 * later ones must bring the same secret. The server keeps only a hash of the secret.
 *
 * - `/api/save`: [SaveRequest] → 204 (Captain's Club members back up their progress).
 * - `/api/load`: [AuthRequest] → [SaveResponse], or 404 when nothing is saved.
 * - `/api/gifts/send`: [GiftRequest] → 204, 400 for an item that can't be given.
 * - `/api/gifts/inbox`: [AuthRequest] → [Inbox], the gifts waiting.
 * - `/api/gifts/ack`: [AckRequest] → 204, once the app has given them to the player (so a gift
 *   is never lost if the app closes in between; the app also remembers ids it has granted).
 *
 * Errors: 400 malformed, 403 wrong secret, 413 too big.
 */
object AccountApi {
    const val SAVE_PATH = "/api/save"
    const val LOAD_PATH = "/api/load"
    const val SEND_GIFT_PATH = "/api/gifts/send"
    const val INBOX_PATH = "/api/gifts/inbox"
    const val ACK_PATH = "/api/gifts/ack"

    /** A cloud save can't be bigger than this (a save is a few kilobytes). */
    const val MAX_SAVE_BYTES = 256 * 1024

    /** Gifts waiting for one player, at most. */
    const val MAX_INBOX = 50

    /** Gifts paid with euros are their own (consumable) store products: "gift_" + the item's id. */
    const val GIFT_PREFIX = "gift_"

    /** Things that can be given: looks (bought once) and tackle packs. Not memberships or passes. */
    fun isGiftable(item: ShopItem): Boolean = when (item.section) {
        ShopSection.TACKLE -> item.kind == ItemKind.CONSUMABLE
        ShopSection.STYLE, ShopSection.AQUARIUM -> item.kind == ItemKind.PERMANENT
        ShopSection.PASSES -> false
    }

    val giftable: List<ShopItem> get() = ShopCatalog.all.filter(::isGiftable)

    /** The store product for buying [item] as a gift. */
    fun giftProduct(item: ShopItem): ShopItem = item.copy(id = GIFT_PREFIX + item.id, kind = ItemKind.CONSUMABLE)

    /** The item a gift product stands for. */
    fun itemOfGiftProduct(productId: String): ShopItem? =
        productId.takeIf { it.startsWith(GIFT_PREFIX) }?.let { ShopCatalog.byId(it.removePrefix(GIFT_PREFIX)) }?.takeIf(::isGiftable)

    /** Every product a store sells: the items, and each giftable item as a gift. */
    val storeProducts: List<ShopItem> get() = ShopCatalog.all + giftable.map(::giftProduct)

    /** "ws://host:8080/battle" → "http://host:8080" (and wss → https). */
    fun httpBase(battleUrl: String): String {
        val u = battleUrl.trim()
        val scheme = when {
            u.startsWith("wss://") -> "https://"
            u.startsWith("ws://") -> "http://"
            u.startsWith("https://") -> "https://"
            else -> "http://"
        }
        val rest = u.substringAfter("://").substringBefore('/')
        return scheme + rest
    }
}

@Serializable
data class AuthRequest(val id: String, val secret: String)

@Serializable
data class SaveRequest(val id: String, val secret: String, val data: String)

@Serializable
data class SaveResponse(val data: String, val savedAtMs: Long)

@Serializable
data class GiftRequest(val from: String, val secret: String, val fromName: String, val to: String, val itemId: String)

@Serializable
data class Gift(val id: String, val fromName: String, val itemId: String, val sentAtMs: Long)

@Serializable
data class Inbox(val gifts: List<Gift>)

@Serializable
data class AckRequest(val id: String, val secret: String, val giftIds: List<String>)
