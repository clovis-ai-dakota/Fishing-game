package com.deepline.fishing.billing

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepline.fishing.core.ShopItem
import java.util.UUID

/**
 * A pretend store for debug builds and tests: no money changes hands. Buying shows the Test store
 * dialog (see `TestStoreDialog`), where the tester picks how the payment goes: approved, cancelled,
 * failed, or pending. Pending payments clear on the next [restore] (the shop's Restore purchases
 * button, or the next app start), like a slow card payment on Google Play.
 */
class TestStore(private val listener: StoreListener) : Store {
    override val name = "Test store"

    /** The purchase waiting for the tester's answer. */
    var request by mutableStateOf<ShopItem?>(null)
        private set

    private val pending = ArrayList<ShopItem>()

    override fun buy(activity: Activity?, item: ShopItem) {
        request = item
    }

    /** The tester's answer for [request]. */
    fun answer(outcome: TestOutcome) {
        val item = request ?: return
        request = null
        when (outcome) {
            TestOutcome.APPROVE -> listener.onPurchased(purchaseOf(item))
            TestOutcome.CANCEL -> listener.onProblem(item.id, StoreProblem.CANCELLED)
            TestOutcome.FAIL -> listener.onProblem(item.id, StoreProblem.FAILED, "The test store declined the card.")
            TestOutcome.PENDING -> {
                pending += item
                listener.onProblem(item.id, StoreProblem.PENDING)
            }
        }
    }

    override fun finish(purchase: StorePurchase) = Unit

    override fun restore() {
        val cleared = pending.toList()
        pending.clear()
        for (item in cleared) listener.onPurchased(purchaseOf(item))
    }

    // Tokens are unique across app runs: the game ignores a token it has already granted.
    private fun purchaseOf(item: ShopItem) = StorePurchase(item.id, "test-${UUID.randomUUID()}", item.kind)

    enum class TestOutcome(val label: String) {
        APPROVE("Approve"),
        CANCEL("Cancel"),
        FAIL("Fail"),
        PENDING("Pending"),
    }
}
