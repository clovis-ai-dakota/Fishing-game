package com.deepline.fishing.billing

import android.app.Activity
import com.deepline.fishing.core.ItemKind
import com.deepline.fishing.core.ShopItem

/**
 * Paying with money. Release builds use Google Play ([PlayStore]); debug builds and tests use the
 * in-app [TestStore]. A store's product ids are the shop's item ids ([ShopItem.id]).
 *
 * The flow is the same for every store: [buy] starts a payment, the store answers through its
 * [StoreListener], the game grants the item, then calls [finish] so the store can consume or
 * acknowledge it. A purchase the game hasn't finished (the app closed, a payment that was pending)
 * is delivered again by [restore], so it's never lost; the game ignores tokens it already granted.
 */
interface Store {
    /** Shown in the shop ("Google Play", "Test store"). */
    val name: String

    /** The store's own price for [item] (localised by Play), or null to use the shop's euro label. */
    fun priceOf(item: ShopItem): String? = null

    /** Starts buying [item]. Play needs the [activity] to show its purchase sheet. */
    fun buy(activity: Activity?, item: ShopItem)

    /** Marks a granted purchase as done: consumables are consumed, the rest acknowledged. */
    fun finish(purchase: StorePurchase)

    /** Delivers purchases the store holds that the game may not have granted yet. */
    fun restore()

    fun close() = Unit
}

/** A payment the store says went through. */
data class StorePurchase(val productId: String, val token: String, val kind: ItemKind)

/** How a purchase the player started ended, when it didn't go through. */
enum class StoreProblem { CANCELLED, PENDING, FAILED, UNAVAILABLE }

interface StoreListener {
    /** A payment went through (now, or delivered later by [Store.restore]): grant it, then finish it. */
    fun onPurchased(purchase: StorePurchase)

    /** The Club subscription is active in the store (it renews by itself). */
    fun onSubscriptionActive(productId: String) = Unit

    /** A purchase didn't go through, or is waiting for the payment to clear. */
    fun onProblem(productId: String?, problem: StoreProblem, message: String? = null)
}

/** What each kind of item is on Google Play: consumed (bought again and again) or kept. */
val ItemKind.isConsumedByStore: Boolean
    get() = this == ItemKind.CONSUMABLE || this == ItemKind.SEASONAL
