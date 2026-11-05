package com.deepline.fishing.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.deepline.fishing.core.ItemKind
import com.deepline.fishing.core.ShopItem

/**
 * Google Play Billing. Every [ShopItem] is a Play product with the same id: tackle packs and
 * seasonal items are consumable in-app products (consumed once granted, so they can be bought
 * again), other items are kept in-app products (acknowledged, restored on a new install), and
 * the Captain's Club is a monthly subscription. Create them in the Play Console with these ids.
 *
 * Play calls back on its own threads; everything is passed to the [listener] on the main thread.
 */
class PlayStore(
    context: Context,
    private val listener: StoreListener,
    private val catalog: () -> List<ShopItem>,
) : Store, PurchasesUpdatedListener {
    override val name = "Google Play"

    private val main = Handler(Looper.getMainLooper())
    private val details = HashMap<String, ProductDetails>()
    private val purchases = HashMap<String, Purchase>()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        connect { restore() }
    }

    private fun onMain(block: () -> Unit) = main.post(block)

    private fun connect(then: () -> Unit) {
        if (client.isReady) return then()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    loadDetails()
                    then()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun loadDetails() {
        val items = catalog()
        for ((type, list) in items.groupBy { if (it.kind == ItemKind.SUBSCRIPTION) ProductType.SUBS else ProductType.INAPP }) {
            val products = list.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(type).build() }
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, found ->
                if (result.responseCode == BillingResponseCode.OK) onMain { found.forEach { details[it.productId] = it } }
            }
        }
    }

    override fun priceOf(item: ShopItem): String? {
        val d = details[item.id] ?: return null
        return d.oneTimePurchaseOfferDetails?.formattedPrice
            ?: d.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
    }

    override fun buy(activity: Activity?, item: ShopItem) {
        if (activity == null) return listener.onProblem(item.id, StoreProblem.UNAVAILABLE, "Google Play needs the game on screen.")
        connect {
            onMain {
                val d = details[item.id]
                    ?: return@onMain listener.onProblem(item.id, StoreProblem.UNAVAILABLE, "This item isn't on sale in Google Play yet.")
                val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
                d.subscriptionOfferDetails?.firstOrNull()?.let { params.setOfferToken(it.offerToken) }
                val result = client.launchBillingFlow(
                    activity,
                    BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build(),
                )
                if (result.responseCode != BillingResponseCode.OK) {
                    listener.onProblem(item.id, StoreProblem.FAILED, result.debugMessage)
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, list: MutableList<Purchase>?) {
        onMain {
            when (result.responseCode) {
                BillingResponseCode.OK -> list.orEmpty().forEach(::handle)
                BillingResponseCode.USER_CANCELED -> listener.onProblem(null, StoreProblem.CANCELLED)
                // Bought before but never granted here (another device, a crash): restore it.
                BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
                else -> listener.onProblem(null, StoreProblem.FAILED, result.debugMessage)
            }
        }
    }

    private fun handle(p: Purchase) {
        for (id in p.products) {
            val item = catalog().firstOrNull { it.id == id } ?: continue
            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    purchases[p.purchaseToken] = p
                    if (item.kind == ItemKind.SUBSCRIPTION) listener.onSubscriptionActive(id)
                    // An acknowledged subscription was granted already; renewals only keep it active.
                    if (item.kind != ItemKind.SUBSCRIPTION || !p.isAcknowledged) {
                        listener.onPurchased(StorePurchase(id, p.purchaseToken, item.kind))
                    }
                }
                Purchase.PurchaseState.PENDING -> listener.onProblem(id, StoreProblem.PENDING)
                else -> Unit
            }
        }
    }

    override fun finish(purchase: StorePurchase) {
        val p = purchases[purchase.token]
        if (purchase.kind.isConsumedByStore) {
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.token).build()) { _, _ -> }
        } else if (p == null || !p.isAcknowledged) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build()) { }
        }
    }

    override fun restore() {
        connect {
            for (type in listOf(ProductType.INAPP, ProductType.SUBS)) {
                client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, list ->
                    if (result.responseCode == BillingResponseCode.OK) onMain { list.forEach(::handle) }
                }
            }
        }
    }

    override fun close() = client.endConnection()
}
