package com.deepline.fishing

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepline.fishing.core.Atmosphere
import com.deepline.fishing.core.Battle
import com.deepline.fishing.core.BattleCast
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BattlePlayer
import com.deepline.fishing.core.BattleTurn
import com.deepline.fishing.core.BoatDesign
import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.CastEvent
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.Cosmetic
import com.deepline.fishing.core.CosmeticSlot
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.Milestone
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Progression
import com.deepline.fishing.core.ProgressCodec
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.ReverseReason
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.SecretRules
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.ItemKind
import com.deepline.fishing.core.PremiumSkins
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.core.ShopSection
import com.deepline.fishing.core.Tackle
import com.deepline.fishing.analytics.GameAnalytics
import com.deepline.fishing.billing.PlayStore
import com.deepline.fishing.billing.Store
import com.deepline.fishing.billing.StoreListener
import com.deepline.fishing.billing.StoreProblem
import com.deepline.fishing.billing.StorePurchase
import com.deepline.fishing.billing.TestStore
import com.deepline.fishing.core.online.ClientMessage
import com.deepline.fishing.core.online.LiveStatus
import com.deepline.fishing.core.online.OnlineMatch
import com.deepline.fishing.core.online.OnlineProtocol
import com.deepline.fishing.core.online.ServerMessage
import com.deepline.fishing.core.online.toDto
import com.deepline.fishing.ui.Skins
import com.deepline.fishing.ui.i18n.AppLanguages
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.i18n.labelText
import com.deepline.fishing.ui.i18n.nameText
import com.deepline.fishing.ui.shop.ShopText
import com.deepline.fishing.ui.tour.Tour
import com.deepline.fishing.ui.tour.TourSession
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Where the player is. [HOME] (the Fish tab), [DEX], [SHOP] and [BATTLE_SETUP] are the four
 * bottom-bar tabs; the others are full screens pushed on top of them (no tab bar).
 */
enum class Screen {
    HOME, DEX, SHOP, BATTLE_SETUP, CAST, FISH_DETAIL, MILESTONES, BATTLE, ONLINE_LOBBY, BOAT_EDITOR, AQUARIUM, THEMES,
    /** Settings (the gear on the Fish tab): sound, notifications, privacy, language, the tour. */
    SETTINGS,
    // -- Daily loop screens below.
    /** The daily reward calendar, today's challenges and migrations (from the Fish tab's daily card). */
    DAILY,

    // -- Social screens below.
    /** Friends and rankings (from the Battle tab), and its pages. CLUBS only while FeatureFlags.clubs is on. */
    SOCIAL, FRIENDS, LEADERBOARDS, TOURNAMENT, CLUBS,
}

/** The four top-level destinations of the bottom navigation bar, in order. [labelRes]: strings_shell.xml. */
enum class MainTab(@param:androidx.annotation.StringRes val labelRes: Int, val screen: Screen) {
    FISH(R.string.shell_tab_fish, Screen.HOME),
    DEX(R.string.shell_tab_dex, Screen.DEX),
    SHOP(R.string.shell_tab_shop, Screen.SHOP),
    BATTLE(R.string.shell_tab_battle, Screen.BATTLE_SETUP);

    companion object {
        fun of(screen: Screen): MainTab? = entries.firstOrNull { it.screen == screen }
    }
}

/**
 * The pages of the Shop tab: rope upgrades (points only), then the shop's sections, where every
 * item has a euro price and a points price. A section only shows once it has items.
 */
enum class ShopPage(@param:androidx.annotation.StringRes val labelRes: Int, val section: ShopSection?) {
    ROPE(R.string.shell_shop_page_rope, null),
    TACKLE(R.string.shell_shop_page_tackle, ShopSection.TACKLE),
    STYLE(R.string.shell_shop_page_style, ShopSection.STYLE),
    PASSES(R.string.shell_shop_page_passes, ShopSection.PASSES),
    AQUARIUM(R.string.shell_shop_page_aquarium, ShopSection.AQUARIUM),
}

/** The sections of the Dex tab. Secrets only appears once one is found; Skins is always there. */
enum class DexSection(@param:androidx.annotation.StringRes val labelRes: Int) {
    FISH(R.string.shell_dex_section_species),
    SECRETS(R.string.shell_dex_section_secrets),
    SKINS(R.string.shell_dex_section_skins),
}

/** A short message flashed over the game screen. */
data class Notice(val text: UiText, val shownAtSeconds: Float)

/** Who the player is battling: the computer at some difficulty, or a friend on the same device. */
sealed interface Opponent {
    data class Computer(val difficulty: BotDifficulty) : Opponent
    data object Friend : Opponent
    data object Online : Opponent
}

enum class OnlineStatus { DISCONNECTED, CONNECTING, CONNECTED, WAITING_ROOM, WAITING_QUICK, IN_MATCH }

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SharedPrefsStore(app)
    private val repository = ProgressRepository(store)

    /** Sound, music, haptics and the casts' screen effects (see feel/GameFeel.kt). */
    val feel = com.deepline.fishing.feel.GameFeel.create(app, store)

    var progress by mutableStateOf(repository.load())
        private set
    var screen by mutableStateOf(Screen.HOME)
        private set
    var dexSection by mutableStateOf(DexSection.FISH)
        private set
    /** The species shown by the Dex detail page (an index in `FishCatalog.all`). */
    var dexFishIndex by mutableIntStateOf(0)
        private set
    var useLantern by mutableStateOf(false)
        private set
    var session by mutableStateOf<CastSession?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var pendingResult by mutableStateOf<CastResult?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set

    // --- Battles -------------------------------------------------------------------------

    var battle by mutableStateOf<Battle?>(null)
        private set
    /** The human turn currently being fished, if the current cast belongs to a battle. */
    var battleTurn by mutableStateOf<BattleTurn?>(null)
        private set
    /** Bumped whenever the (non-observable) battle changes, so battle screens recompose. */
    var battleVersion by mutableIntStateOf(0)
        private set
    private var battleOpponent: Opponent? = null
    private var battleRecorded = false

    // --- Online --------------------------------------------------------------------------

    var onlineStatus by mutableStateOf(OnlineStatus.DISCONNECTED)
        private set
    var onlineError by mutableStateOf<UiText?>(null)
        private set
    var roomCode by mutableStateOf<String?>(null)
        private set
    var onlineMode by mutableStateOf(BattleMode.BEST_OF_FIVE)
        private set
    var onlineMatch by mutableStateOf<OnlineMatch?>(null)
        private set
    var playerName by mutableStateOf(store.getString(KEY_NAME) ?: "Angler") // i18n-ignore: the default name, sent online as typed
        private set
    var serverUrl by mutableStateOf(store.getString(KEY_SERVER) ?: BuildConfig.DEFAULT_SERVER_URL)
        private set
    private var connection: OnlineConnection? = null
    private var lastLiveSentAt = -1f

    val isOnlineBattle: Boolean get() = onlineMatch != null && battle === onlineMatch?.battle

    // --- Privacy: consent to analytics and crash reports, account deletion (privacy/) ------

    val privacy = com.deepline.fishing.privacy.PrivacyController(store, viewModelScope)

    /** Settings > Privacy: erases the online account, then plays on with a new random identity. */
    fun deleteOnlineAccount() = privacy.deleteAccount(accounts, playerIdentity) { fresh ->
        saveIdentity(fresh)
        playerIdentity = fresh
        lastBackupMs = 0L
        store.putString(KEY_LAST_BACKUP, "0")
    }

    // --- Tour ----------------------------------------------------------------------------

    /** The guided tour, while it is showing. */
    var tour by mutableStateOf<TourSession?>(null)
        private set
    /** True right after the tour closes: ask whether to show it again next launch. */
    var askTourAgain by mutableStateOf(false)
        private set

    /** The daily loop: daily reward, challenges, reminders (see daily/DailyController.kt). */
    val daily = com.deepline.fishing.daily.DailyController(app, store, { progress }, ::update)

    init {
        // Each app start is a session (the starter pack is offered from a later one).
        update(ShopCatalog.onSessionStart(progress, ShopClock.now()))
        // A first launch (no answer stored yet) shows the full tour; afterwards it only opens
        // again if the player asked for it, or to show steps added since the version they saw.
        val showAgain = store.getString(KEY_TOUR_AGAIN)?.toBooleanStrictOrNull() ?: true
        val seen = store.getString(KEY_TOUR_SEEN)?.toIntOrNull() ?: 0
        // The consent sheet comes first (nothing is collected before it's answered), then the tour.
        Tour.planAtLaunch(showAgain, seen)?.let { privacy.afterConsent { beginTour(TourSession(it.steps, whatsNew = it.whatsNew)) } }
        daily.onAppOpened()
    }

    fun openDaily() {
        screen = Screen.DAILY
    }

    /** The app came back to the foreground: a new day may have started. */
    fun onAppResumed() = daily.onAppOpened()

    /** A tapped reminder opens the screen it talks about. */
    fun openFromNotification(key: String?) {
        val target = com.deepline.fishing.daily.ReminderTarget.of(key) ?: return
        daily.onNotificationOpened(target)
        if (session != null || tour != null) return
        when (target) {
            com.deepline.fishing.daily.ReminderTarget.DAILY -> openDaily()
            com.deepline.fishing.daily.ReminderTarget.MIGRATION -> screen = Screen.HOME
            com.deepline.fishing.daily.ReminderTarget.EVENT -> openShop(ShopPage.PASSES)
        }
    }

    /** Starts the full tour from the menu. */
    fun startTour() = beginTour(TourSession(Tour.steps))

    private fun beginTour(session: TourSession) {
        if (this.session != null) return
        askTourAgain = false
        tour = session
        GameAnalytics.tourStarted(session)
        showTourStep(session)
    }

    fun tourNext() {
        val t = tour ?: return
        if (t.isLast) return endTour(completed = true)
        val next = t.copy(index = t.index + 1)
        tour = next
        showTourStep(next)
    }

    fun tourBack() {
        val t = tour ?: return
        if (t.isFirst) return
        val previous = t.copy(index = t.index - 1)
        tour = previous
        showTourStep(previous)
    }

    private fun showTourStep(t: TourSession) {
        screen = t.current.screen
        if (screen == Screen.DEX) dexSection = t.current.dexSection ?: DexSection.FISH
        if (screen == Screen.SHOP) shopPage = t.current.shopPage ?: ShopPage.ROPE
    }

    /** Skipping and finishing both close the tour and ask about next time. */
    fun skipTour() = endTour(completed = false)

    private fun endTour(completed: Boolean) {
        tour?.let { GameAnalytics.tourEnded(it, completed) }
        tour = null
        screen = Screen.HOME
        store.putString(KEY_TOUR_SEEN, Tour.VERSION.toString())
        askTourAgain = true
    }

    fun answerTourAgain(showNextTime: Boolean) {
        store.putString(KEY_TOUR_AGAIN, showNextTime.toString())
        askTourAgain = false
    }

    private fun update(p: PlayerProgress) {
        progress = p
        repository.save(p)
        GameAnalytics.progressChanged(p)
    }

    fun navigate(target: Screen) {
        if (target == Screen.CAST) return startCast()
        screen = target
    }

    /** A tap on the bottom navigation bar. */
    fun selectTab(tab: MainTab) {
        if (screen != tab.screen) feel.cue(com.deepline.fishing.feel.Cue.UI_TAP)
        screen = tab.screen
        shopMessage = null
        if (tab == MainTab.SHOP) checkGifts()
    }

    /** Opens the Dex on [section] (falls back to Species if that section isn't unlocked yet). */
    fun openDex(section: DexSection = dexSection) {
        dexSection = if (section in availableDexSections) section else DexSection.FISH
        screen = Screen.DEX
    }

    /** Dex sections the player can open: Secrets once one is found; Skins (boat customising) always. */
    val availableDexSections: List<DexSection>
        get() = DexSection.entries.filter {
            when (it) {
                DexSection.FISH -> true
                DexSection.SECRETS -> progress.discoveredSecrets.isNotEmpty()
                DexSection.SKINS -> true
            }
        }

    // --- Skins: boat customisation, shop, atmosphere ------------------------------------------

    /** Everything the player wears, as the renderers need it. */
    val skins: Skins
        get() = Skins.of(
            progress.equippedSkins,
            customBoat = progress.boatDesign.takeIf { progress.customBoatEquipped },
            atmosphere = progress.atmosphere,
        )

    fun openBoatEditor() {
        screen = Screen.BOAT_EDITOR
    }

    // --- Shop ----------------------------------------------------------------------------

    var shopPage by mutableStateOf(ShopPage.ROPE)
        private set

    /** Opens the Shop tab on [page]. */
    fun openShop(page: ShopPage = shopPage) {
        shopPage = page
        screen = Screen.SHOP
        checkGifts()
    }

    fun selectShopPage(page: ShopPage) {
        shopPage = page
    }

    /** Pages with something to show (the Aquarium once it has items). */
    val shopPages: List<ShopPage>
        get() = ShopPage.entries.filter { it.section == null || ShopCatalog.inSection(it.section).isNotEmpty() }

    /** What just happened in the shop (a purchase, a pending payment...), until dismissed. */
    var shopMessage by mutableStateOf<UiText?>(null)
        private set

    fun dismissShopMessage() {
        shopMessage = null
    }

    private val storeListener = object : StoreListener {
        override fun onPurchased(purchase: StorePurchase) = grantStorePurchase(purchase)

        override fun onSubscriptionActive(productId: String) {
            update(ShopCatalog.onClubSubscriptionActive(progress, ShopClock.now()))
        }

        override fun onProblem(productId: String?, problem: StoreProblem, message: String?) {
            GameAnalytics.storeProblem(productId, problem.name.lowercase(), message)
            val name = productId?.let { ShopCatalog.byId(it)?.nameText() }
            // The store's own explanation (from Google Play) stays as it comes.
            val detail = message?.takeIf { it.isNotBlank() }
            shopMessage = when (problem) {
                StoreProblem.CANCELLED -> UiText.res(R.string.shop_message_cancelled)
                StoreProblem.PENDING -> name?.let { UiText.res(R.string.shop_message_pending, it) } ?: UiText.res(R.string.shop_message_pending_unknown)
                StoreProblem.FAILED -> detail?.let { UiText.res(R.string.shop_message_failed_because, it) } ?: UiText.res(R.string.shop_message_failed)
                StoreProblem.UNAVAILABLE -> detail?.let(UiText::raw) ?: UiText.res(R.string.shop_message_store_unavailable, billing.name)
            }
        }
    }

    /** Pays with money: Google Play in release builds, the test store in debug builds and tests. */
    val billing: Store = if (BuildConfig.USE_PLAY_BILLING) {
        PlayStore(app, storeListener) { com.deepline.fishing.core.online.AccountApi.storeProducts }
    } else {
        TestStore(storeListener)
    }

    /** The test store, when that's what [billing] is (its dialog is part of the app). */
    val testStore: TestStore? get() = billing as? TestStore

    /** Purchase tokens already granted, so a purchase delivered twice is only granted once. */
    private val grantedTokens = LinkedHashSet(store.getString(KEY_GRANTED)?.split(',')?.filter { it.isNotBlank() }.orEmpty())

    private fun grantStorePurchase(purchase: StorePurchase) {
        if (purchase.token !in grantedTokens) GameAnalytics.storePurchase(purchase.productId)
        com.deepline.fishing.core.online.AccountApi.itemOfGiftProduct(purchase.productId)?.let { item ->
            if (purchase.token !in grantedTokens) {
                rememberToken(purchase.token)
                val to = pendingGifts.remove(purchase.productId)
                savePendingGifts()
                if (to != null) deliverGift(item, to.first, to.second, refundPoints = 0L) else receiveGift(item, UiText.res(R.string.shop_gift_from_you))
            }
            billing.finish(purchase)
            return
        }
        val item = ShopCatalog.byId(purchase.productId) ?: return
        if (purchase.token !in grantedTokens) {
            val owned = item.kind == ItemKind.PERMANENT && ShopCatalog.owns(progress, item.id)
            if (!owned) {
                val contents = ShopText.contents(item, progress)
                update(ShopCatalog.grant(progress, item, ShopClock.now()))
                shopMessage = UiText.res(R.string.shop_message_thanks, item.nameText(), contents)
                feel.cue(com.deepline.fishing.feel.Cue.PURCHASE)
            }
            rememberToken(purchase.token)
        }
        billing.finish(purchase)
    }

    private fun rememberToken(token: String) {
        grantedTokens += token
        while (grantedTokens.size > MAX_GRANTED_TOKENS) grantedTokens.remove(grantedTokens.first())
        store.putString(KEY_GRANTED, grantedTokens.joinToString(","))
    }

    // --- Account: gifts and cloud save ---------------------------------------------------------

    /** Who this player is on the battle server (made on first launch, or restored from a code). */
    var playerIdentity by mutableStateOf(loadIdentity())
        private set

    private fun loadIdentity(): com.deepline.fishing.core.PlayerId {
        val id = store.getString(KEY_PLAYER_ID)
        val secret = store.getString(KEY_PLAYER_SECRET)
        if (id != null && secret != null && com.deepline.fishing.core.PlayerId.isValid(id) && com.deepline.fishing.core.PlayerId.isValidSecret(secret)) {
            return com.deepline.fishing.core.PlayerId(id, secret)
        }
        return com.deepline.fishing.core.PlayerId.random().also(::saveIdentity)
    }

    private fun saveIdentity(who: com.deepline.fishing.core.PlayerId) {
        store.putString(KEY_PLAYER_ID, who.id)
        store.putString(KEY_PLAYER_SECRET, who.secret)
    }

    /** Talks to the server's gift and cloud save API (tests swap in a fake). */
    internal var accounts = AccountClient { com.deepline.fishing.core.online.AccountApi.httpBase(serverUrl) }

    var lastBackupMs by mutableStateOf(store.getString(KEY_LAST_BACKUP)?.toLongOrNull() ?: 0L)
        private set

    /** A server request is running (the account dialog shows a spinner). */
    var accountBusy by mutableStateOf(false)
        private set

    /** Cloud save is a Captain's Club perk. */
    val canCloudSave: Boolean get() = isClubMember

    private fun launchAccount(quiet: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!quiet) accountBusy = true
            try {
                block()
            } catch (e: AccountClient.AccountException) {
                GameAnalytics.problem("account", e)
                if (!quiet) shopMessage = e.text
            } finally {
                if (!quiet) accountBusy = false
            }
        }
    }

    fun backupNow(quiet: Boolean = false) {
        if (!canCloudSave) {
            if (!quiet) shopMessage = UiText.res(R.string.shop_cloud_club_only)
            return
        }
        val data = ProgressCodec.encode(progress)
        launchAccount(quiet) {
            accounts.save(playerIdentity, data)
            lastBackupMs = ShopClock.now()
            store.putString(KEY_LAST_BACKUP, lastBackupMs.toString())
            if (!quiet) shopMessage = UiText.res(R.string.shop_cloud_backed_up)
        }
    }

    /** Club members are backed up after their casts, at most every few minutes. */
    private fun autoBackup() {
        if (canCloudSave && ShopClock.now() - lastBackupMs > AUTO_BACKUP_MS) backupNow(quiet = true)
    }

    /** Restores the progress saved for a recovery code (from another phone) onto this one. */
    fun restoreFromCode(code: String) {
        val who = com.deepline.fishing.core.PlayerId.fromRecoveryCode(code)
        if (who == null) {
            shopMessage = UiText.res(R.string.shop_cloud_bad_code)
            return
        }
        launchAccount {
            val save = accounts.load(who)
            saveIdentity(who)
            playerIdentity = who
            update(ProgressCodec.decode(save.data))
            refreshTackleToggles()
            shopMessage = UiText.res(R.string.shop_cloud_restored)
        }
    }

    /** Someone to send a gift to (an online opponent), while the gift picker is open. */
    data class GiftTarget(val playerId: String, val name: String)

    var giftTarget by mutableStateOf<GiftTarget?>(null)
        private set

    fun openGifts(target: GiftTarget) {
        giftTarget = target
    }

    fun closeGifts() {
        giftTarget = null
    }

    /** Gifts paid with euros, waiting for the store: gift product id → (recipient id, name). */
    private val pendingGifts = HashMap<String, Pair<String, String>>().apply {
        store.getString(KEY_PENDING_GIFTS)?.split(';')?.forEach { e ->
            val parts = e.split('=', limit = 2)
            val who = parts.getOrNull(1)?.split(',', limit = 2)
            if (parts.size == 2 && who?.size == 2) put(parts[0], who[0] to who[1])
        }
    }

    private fun savePendingGifts() =
        store.putString(KEY_PENDING_GIFTS, pendingGifts.entries.joinToString(";") { "${it.key}=${it.value.first},${it.value.second}" })

    fun sendGiftWithPoints(item: ShopItem) {
        val to = giftTarget ?: return
        val price = ShopCatalog.pointsPrice(item, progress)
        if (progress.pointsBalance < price || !com.deepline.fishing.core.online.AccountApi.isGiftable(item)) return
        update(progress.copy(pointsBalance = progress.pointsBalance - price))
        GameAnalytics.spend(price, "gift", item.id)
        giftTarget = null
        deliverGift(item, to.playerId, to.name, refundPoints = price)
    }

    fun sendGiftWithMoney(item: ShopItem, activity: Activity?) {
        val to = giftTarget ?: return
        val product = com.deepline.fishing.core.online.AccountApi.giftProduct(item)
        pendingGifts[product.id] = to.playerId to to.name
        savePendingGifts()
        giftTarget = null
        GameAnalytics.storeCheckout(item, gift = true)
        billing.buy(activity, product)
    }

    /** Sends a paid gift; if the server can't take it, the points come back (or the item is kept). */
    private fun deliverGift(item: ShopItem, toId: String, toName: String, refundPoints: Long) {
        val fromName = OnlineProtocol.sanitizeName(playerName)
        viewModelScope.launch {
            try {
                accounts.sendGift(playerIdentity, fromName, toId, item.id)
                shopMessage = UiText.res(R.string.shop_gift_sent, item.nameText(), toName)
            } catch (e: AccountClient.AccountException) {
                GameAnalytics.problem("gift", e)
                if (refundPoints > 0) {
                    update(progress.copy(pointsBalance = progress.pointsBalance + refundPoints))
                    GameAnalytics.earn(refundPoints, "gift_refund")
                    shopMessage = UiText.res(R.string.shop_gift_refunded, e.text)
                } else {
                    receiveGift(item, UiText.res(R.string.shop_gift_from_you))
                    shopMessage = UiText.res(R.string.shop_gift_kept, e.text, item.nameText())
                }
            }
        }
    }

    private val grantedGiftIds = LinkedHashSet(store.getString(KEY_GIFTS_GRANTED)?.split(',')?.filter { it.isNotBlank() }.orEmpty())

    /** Collects gifts waiting on the server (at launch, and when the Shop opens). */
    fun checkGifts() = launchAccount(quiet = true) {
        val gifts = accounts.inbox(playerIdentity)
        if (gifts.isEmpty()) return@launchAccount
        val lines = ArrayList<UiText>()
        for (g in gifts) {
            if (g.id in grantedGiftIds) continue
            val item = ShopCatalog.byId(g.itemId) ?: continue
            lines += receiveGift(item, UiText.raw(g.fromName))
            grantedGiftIds += g.id
        }
        while (grantedGiftIds.size > MAX_GRANTED_TOKENS) grantedGiftIds.remove(grantedGiftIds.first())
        store.putString(KEY_GIFTS_GRANTED, grantedGiftIds.joinToString(","))
        accounts.ack(playerIdentity, gifts.map { it.id })
        if (lines.isNotEmpty()) shopMessage = UiText.lines(lines)
    }

    init {
        // Gifts sent while the app was closed.
        checkGifts()
    }

    /** Gives [item] to this player; something they own already becomes its points price. */
    private fun receiveGift(item: ShopItem, from: UiText): UiText {
        val p = progress
        return if (item.kind == ItemKind.PERMANENT && ShopCatalog.owns(p, item.id)) {
            val points = ShopCatalog.pointsPrice(item, p)
            update(p.copy(pointsBalance = p.pointsBalance + points))
            UiText.res(R.string.shop_gift_received_owned, from, item.nameText(), UiText.number(points))
        } else {
            val contents = ShopText.contents(item, p)
            update(ShopCatalog.grant(p, item, ShopClock.now()))
            UiText.res(R.string.shop_gift_received, from, item.nameText(), contents)
        }
    }

    fun shopPointsPrice(item: ShopItem): Long = ShopCatalog.pointsPrice(item, progress)

    fun canBuy(item: ShopItem): Boolean = ShopCatalog.canBuy(item, progress, ShopClock.now())

    /** Buys [item] with points; false if it can't be bought now or isn't affordable. */
    fun buyWithPoints(item: ShopItem): Boolean {
        val contents = ShopText.contents(item, progress)
        val next = ShopCatalog.buyWithPoints(progress, item, ShopClock.now()) ?: return false
        GameAnalytics.shopPointsPurchase(item, progress, next)
        update(next)
        shopMessage = UiText.res(R.string.shop_message_got, item.nameText(), contents)
        feel.cue(com.deepline.fishing.feel.Cue.PURCHASE)
        return true
    }

    /** Buys [item] with money through [billing] (Play needs the [activity] for its sheet). */
    fun buyWithMoney(item: ShopItem, activity: Activity?) {
        if (!canBuy(item)) return
        shopMessage = null
        GameAnalytics.storeCheckout(item)
        billing.buy(activity, item)
    }

    /** Asks the store for purchases not granted yet (a new phone, a payment that cleared). */
    fun restorePurchases() {
        shopMessage = UiText.res(R.string.shop_message_restoring, billing.name)
        billing.restore()
    }

    val isClubMember: Boolean get() = ShopCatalog.isClubMember(progress, ShopClock.now())

    val clubPouchReady: Boolean get() = ShopCatalog.clubPouchReady(progress, ShopClock.now())

    fun claimClubPouch() {
        val pouch = ShopCatalog.clubPouch
        val contents = ShopText.contents(pouch, progress)
        ShopCatalog.claimClubPouch(progress, ShopClock.now())?.let {
            update(it)
            shopMessage = UiText.res(R.string.shop_message_club_pouch, contents)
            feel.cue(com.deepline.fishing.feel.Cue.CLAIM)
        }
    }

    val starterOfferOpen: Boolean get() = ShopCatalog.starterOfferOpen(progress, ShopClock.now())

    /** Season and weekend event rewards waiting to be claimed (the Shop tab shows a dot). */
    val seasonRewardsReady: Boolean
        get() = com.deepline.fishing.core.Seasons.claimable(progress, ShopClock.now()).isNotEmpty() ||
            com.deepline.fishing.core.Seasons.eventClaimable(progress, ShopClock.now()).isNotEmpty()

    fun claimSeasonRewards() {
        val now = ShopClock.now()
        val open = com.deepline.fishing.core.Seasons.claimable(progress, now)
        if (open.isEmpty()) return
        val what = ShopText.seasonGrants(open.flatMap { it.grants }, progress)
        val next = com.deepline.fishing.core.Seasons.claimAll(progress, now)
        GameAnalytics.seasonClaimed(open.size, open.any { it.premium }, progress, next)
        update(next)
        shopMessage = UiText.res(R.string.shop_message_season_rewards, what)
        feel.cue(com.deepline.fishing.feel.Cue.CLAIM)
    }

    fun claimEventReward() {
        val now = ShopClock.now()
        com.deepline.fishing.core.Seasons.claimEvent(progress, now)?.let {
            GameAnalytics.weekendEventClaimed(progress, it)
            update(it)
            shopMessage = UiText.res(R.string.shop_message_event_claimed)
            feel.cue(com.deepline.fishing.feel.Cue.CLAIM)
        }
    }

    /** Wears a premade boat the player owns. */
    fun wearPreset(presetId: String) {
        ShopCatalog.wearPreset(progress, presetId)?.let(::update)
    }

    /** Puts a paint, emblem or print the player owns on their boat, and wears the boat. */
    fun applyBoatPart(part: BoatPart): Boolean = saveBoatDesign(com.deepline.fishing.core.BoatParts.applyTo(progress.boatDesign, part, progress))

    /** Whether the boat the player wears uses [part]. */
    fun isOnBoat(part: BoatPart): Boolean =
        progress.customBoatEquipped && part in com.deepline.fishing.core.BoatParts.partsOf(progress.boatDesign)

    /** Whether the player is wearing the premade boat [presetId]. */
    fun isWearingPreset(presetId: String): Boolean =
        progress.customBoatEquipped && PremiumSkins.byId(presetId)?.design == progress.boatDesign

    /** Buys a boat option with points; false if it can't be bought or isn't affordable. */
    fun unlockBoatPart(part: BoatPart): Boolean {
        val next = Progression.unlockBoatPart(progress, part) ?: return false
        GameAnalytics.pointsChanged(progress, next, "boat_part", part.key.lowercase())
        update(next)
        feel.cue(com.deepline.fishing.feel.Cue.PURCHASE)
        return true
    }

    /** Saves [design] and wears it; false if it uses an option that's still locked. */
    fun saveBoatDesign(design: BoatDesign): Boolean {
        val next = Progression.setBoatDesign(progress, design) ?: return false
        update(Progression.equipCustomBoat(next, true))
        return true
    }

    fun equipCustomBoat(on: Boolean) {
        update(Progression.equipCustomBoat(progress, on))
    }

    fun setAtmosphere(atmosphere: Atmosphere) {
        Progression.setAtmosphere(progress, atmosphere)?.let(::update)
    }

    /** Opens the Dex page of the species at [index] in `FishCatalog.all`. */
    fun openFishDetail(index: Int) {
        dexFishIndex = index
        screen = Screen.FISH_DETAIL
    }

    /** Remembers which page the detail pager is on, so back returns to the right place. */
    fun showFishDetail(index: Int) {
        dexFishIndex = index
    }

    fun toggleLantern(enabled: Boolean) {
        useLantern = enabled && progress.lanterns > 0
        if (useLantern) feel.cue(com.deepline.fishing.feel.Cue.LANTERN)
    }

    /** The other tackle switched on for the next cast (Lucky charm, Sparkle lure). */
    var useCharm by mutableStateOf(false)
        private set
    var useLure by mutableStateOf(false)
        private set

    fun toggleCharm(enabled: Boolean) {
        useCharm = enabled && progress.luckyCharms > 0
    }

    fun toggleLure(enabled: Boolean) {
        useLure = enabled && progress.sparkleLures > 0
    }

    /** The rare fish detector, switched on for the next cast (in battles too). */
    var useDetector by mutableStateOf(false)
        private set

    fun toggleDetector(enabled: Boolean) {
        useDetector = enabled && progress.detectors > 0
    }

    /**
     * Species the detector marks during the current cast (those not caught yet when it started),
     * or null when it's off. Only the screen uses it: the water is the same.
     */
    var detectorTargets by mutableStateOf<Set<String>?>(null)
        private set

    /** Uses up the sight tackle switched on (Lantern, detector) for one cast; allowed in battles. */
    private fun useSightTackle(): Pair<Boolean, Boolean> {
        var p = progress
        val lantern = useLantern && p.lanterns > 0
        if (lantern) p = Progression.consumeLantern(p) ?: p
        val detector = useDetector && p.detectors > 0
        if (detector) p = Progression.consumeTackle(p, Tackle.DETECTOR) ?: p
        update(p)
        return lantern to detector
    }

    /** Switches tackle off once it has run out. */
    private fun refreshTackleToggles() {
        useLantern = useLantern && progress.lanterns > 0
        useCharm = useCharm && progress.luckyCharms > 0
        useLure = useLure && progress.sparkleLures > 0
        useDetector = useDetector && progress.detectors > 0
    }

    fun buyTackle(item: Tackle) {
        Progression.buyTackle(progress, item)?.let {
            GameAnalytics.tackleBought(item, progress, it)
            update(it)
            feel.cue(com.deepline.fishing.feel.Cue.PURCHASE)
        }
    }

    /** The Sparkling chance of the water about to be generated (raised by a Sparkle lure). */
    private var nextSparklingChance = GameConfig.SPARKLING_CHANCE

    /** The migration's spawn boost for the water about to be generated (solo casts only). */
    private var nextBoost: com.deepline.fishing.core.SpawnBoost? = null

    fun startCast() {
        val (lantern, detector) = useSightTackle()
        var p = progress
        val charm = useCharm && p.luckyCharms > 0
        if (charm) p = Progression.consumeTackle(p, Tackle.LUCKY_CHARM) ?: p
        val lure = useLure && p.sparkleLures > 0
        if (lure) p = Progression.consumeTackle(p, Tackle.SPARKLE_LURE) ?: p
        update(p)
        nextSparklingChance = GameConfig.SPARKLING_CHANCE * if (lure) GameConfig.SPARKLE_LURE_MULTIPLIER else 1f
        // A migration makes two species more common, in solo casts only (battles share seeded water).
        nextBoost = com.deepline.fishing.core.Migrations.boostAt(ShopClock.now())
        val s = castFactory(p.specs, lantern)
        nextSparklingChance = GameConfig.SPARKLING_CHANCE
        nextBoost = null
        if (charm) s.bonusBumps = GameConfig.LUCKY_CHARM_EXTRA_BUMPS
        s.detectorActive = detector
        beginSession(s, turn = null)
    }

    private fun beginSession(s: CastSession, turn: BattleTurn?) {
        detectorTargets = if (s.detectorActive) {
            com.deepline.fishing.core.FishCatalog.all.filterNot(progress::isDiscovered).map { it.id }.toSet()
        } else {
            null
        }
        explainedThisCast.clear()
        secretQueue = emptyList()
        // A solo cast's lure toggle is still on while its lure is in use (see refreshTackleToggles).
        GameAnalytics.castStarted(s, battle?.mode.takeIf { turn != null }, isOnlineBattle, lure = turn == null && useLure)
        session = s
        battleTurn = turn
        pendingResult = null
        notice = null
        paused = false
        screen = Screen.CAST
        feel.onCastStart(s)
    }

    /** Builds the water for a regular cast. Tests swap it to fish hand-placed water. */
    internal var castFactory: (RopeSpecs, Boolean) -> CastSession = { specs, lantern ->
        CastSession.create(specs, lantern, sparklingChance = nextSparklingChance, boost = nextBoost)
    }

    /** Fishing secrets waiting to be explained; the first one is showing (the cast is paused). */
    private var secretQueue by mutableStateOf<List<String>>(emptyList())

    /** The fishing secret being explained right now. */
    val secretPopup: String? get() = secretQueue.firstOrNull()

    /** Secrets already explained during the current cast (so they don't pop up twice). */
    private val explainedThisCast = HashSet<String>()

    fun onFrame(dt: Float, steerTargetX: Float?) {
        val s = session ?: return
        if (paused || pendingResult != null || secretPopup != null) return
        // Slow motion (a rare catch) only ever slows solo casts: battles run in real time.
        s.update(feel.simDt(dt, solo = battleTurn == null), steerTargetX)
        val events = s.drainEvents()
        feel.onCastFrame(s, events)
        // A last-bump hook comes with the line turning back; its notice is the one worth showing.
        val hooked = events.any { it is CastEvent.LastBumpHooked }
        for (event in events) {
            val text = when (event) {
                is CastEvent.Touched ->
                    if (event.touchesUsed < GameConfig.DESCENT_TOUCH_LIMIT) {
                        UiText.plural(R.plurals.cast_notice_bump, GameConfig.DESCENT_TOUCH_LIMIT - event.touchesUsed)
                    } else {
                        null
                    }
                is CastEvent.Reversed -> when (event.reason) {
                    ReverseReason.TOUCH_LIMIT -> if (hooked) null else UiText.res(R.string.cast_notice_pulled_up)
                    ReverseReason.ROPE_END -> UiText.res(R.string.cast_notice_rope_end)
                }
                is CastEvent.Caught -> UiText.res(
                    if (event.record.sparkling) R.string.cast_notice_caught_sparkling else R.string.cast_notice_caught,
                    event.record.species.nameText(),
                    UiText.number(event.record.points),
                )
                is CastEvent.TooHeavy -> UiText.res(R.string.cast_notice_too_heavy, event.species.nameText())
                is CastEvent.LastBumpHooked -> {
                    onSecretTriggered(Secrets.LAST_BUMP)
                    UiText.res(
                        R.string.cast_notice_last_bump,
                        event.fish?.species?.nameText() ?: event.snag!!.kind.labelText(),
                        UiText.number(event.points),
                    )
                }
                CastEvent.Surfaced -> null
                CastEvent.FastReel -> null
            }
            if (text != null) notice = Notice(text, s.elapsedSeconds)
        }
        val limit = battleTurn?.timeLimitSec
        if (limit != null && s.phase != CastPhase.SURFACED && s.elapsedSeconds >= limit) {
            s.endNow()
            notice = Notice(UiText.res(R.string.cast_notice_times_up), s.elapsedSeconds)
        }
        if (s.phase == CastPhase.SURFACED) {
            val result = s.result
            pendingResult = result
            result?.let(feel::onCastResult)
            // Secrets pulled off in this cast are revealed with the results.
            result?.let { r -> Secrets.revealedBy(r).forEach(::onSecretTriggered) }
        }
        sendLiveStatus(s)
    }

    private fun sendLiveStatus(s: CastSession) {
        val turn = battleTurn ?: return
        if (!isOnlineBattle) return
        if (lastLiveSentAt >= 0f && s.elapsedSeconds - lastLiveSentAt < LIVE_INTERVAL_SEC) return
        lastLiveSentAt = s.elapsedSeconds
        connection?.send(
            ClientMessage.Live(
                LiveStatus(
                    castNumber = turn.castNumber,
                    depth = s.hookDepth,
                    points = s.pointsSoFar,
                    fish = s.catches.size,
                    ascending = s.phase == CastPhase.ASCENDING,
                ),
            ),
        )
    }

    /** Seconds left on the battle clock during a timed battle cast, otherwise null. */
    fun battleTimeLeft(): Float? {
        val limit = battleTurn?.timeLimitSec ?: return null
        val elapsed = session?.elapsedSeconds ?: 0f
        return (limit - elapsed).coerceAtLeast(0f)
    }

    /** A hidden mechanic just happened: remember it, and explain it unless the player opted out. */
    private fun onSecretTriggered(id: String) {
        if (!explainedThisCast.add(id)) return
        val explain = Progression.shouldExplainSecret(progress, id)
        // One sound for every secret (it never tells which).
        if (explain || id !in progress.discoveredSecrets) feel.cue(com.deepline.fishing.feel.Cue.SECRET)
        // The first time also grants the secret's one-time reward (points, Lanterns, skins).
        val next = Progression.discoverSecret(progress, id)
        if (id !in progress.discoveredSecrets) GameAnalytics.secretDiscovered(id, battleTurn != null || screen == Screen.BATTLE, progress, next)
        update(next)
        if (explain) secretQueue = secretQueue + id
    }

    /** Closes the secret explanation; [showAgain] is the answer to "show this again next time?". */
    fun answerSecretPopup(showAgain: Boolean) {
        val id = secretPopup ?: return
        update(Progression.setSecretPopupMuted(progress, id, muted = !showAgain))
        secretQueue = secretQueue.drop(1)
    }

    /** Hauls a full line straight up (see [CastSession.startFastReel]). */
    fun fastReel() {
        val s = session ?: return
        if (paused || pendingResult != null) return
        s.startFastReel()
    }

    fun pause() {
        if (session != null && pendingResult == null) paused = true
    }

    fun resume() {
        paused = false
    }

    fun onAppPaused() {
        if (screen == Screen.CAST) pause()
    }

    /** Leaves the cast without scoring anything. In a battle, this forfeits the match. */
    fun abandonCast() {
        val turn = battleTurn
        session?.let { GameAnalytics.castAbandoned(it, battle?.mode.takeIf { turn != null }, isOnlineBattle) }
        session = null
        paused = false
        pendingResult = null
        battleTurn = null
        // -- Social: quitting a tournament attempt scores 0 (the attempt is used).
        if (tournamentAttempt != null) {
            tournamentAttempt = null
            return openSocial(Screen.TOURNAMENT)
        }
        if (turn != null) {
            onlineMatch?.let { if (isOnlineBattle) it.markSent(turn) }
            forfeitBattle(turn.playerIndex)
        } else {
            screen = Screen.HOME
        }
    }

    fun collectResult() {
        val result = pendingResult
        val turn = battleTurn
        val s = session
        if (result != null && s != null) GameAnalytics.castFinished(result, s, turn, battle?.mode.takeIf { turn != null }, isOnlineBattle, progress)
        pendingResult = null
        session = null
        battleTurn = null
        // -- Social: a tournament attempt goes to the server; it never counts for points or the log.
        tournamentAttempt?.let { attempt ->
            tournamentAttempt = null
            return finishTournamentAttempt(attempt, result, s)
        }
        if (turn != null && result != null && s != null) {
            val duration = turn.timeLimitSec?.let { minOf(it, s.elapsedSeconds) } ?: s.elapsedSeconds
            val cast = BattleCast(result, duration)
            val online = onlineMatch
            if (isOnlineBattle && online != null) {
                // Online casts are only recorded once the server confirms them.
                online.markSent(turn)
                connection?.send(ClientMessage.CastFinished(turn, cast.toDto()))
            } else {
                battle?.record(turn, cast)
            }
            refreshTackleToggles()
            onBattleChanged()
            screen = Screen.BATTLE
            return
        }
        // Battle casts never reach here: only regular casts count for points and the fish log.
        result?.let {
            val before = progress
            update(Progression.applyCast(progress, it))
            daily.afterCast(before, progress)
        }
        autoBackup()
        // Master angler: the fish log has just been completed (it can only happen once).
        (Secrets.revealedByProgress(progress) - progress.discoveredSecrets).forEach(::onSecretTriggered)
        refreshTackleToggles()
        screen = Screen.HOME
    }

    fun equipSkin(slot: CosmeticSlot, cosmetic: Cosmetic?) {
        update(Progression.equipSkin(progress, slot, cosmetic))
    }

    /** Steps a rope stat down one level and refunds what it cost. */
    fun downgrade(stat: RopeStat) {
        Progression.downgrade(progress, stat)?.let {
            GameAnalytics.ropeChanged(stat, progress, it)
            update(it)
        }
    }

    /** The title the player wears (shown at home and in battles), or null. */
    val title: String? get() = progress.equippedSkins[CosmeticSlot.TITLE]?.displayName

    /** The frame of the player's title badge (gold unless another is chosen and available). */
    val titleFrame: com.deepline.fishing.core.TitleFrame
        get() = com.deepline.fishing.core.Showcase.titleFrame(progress, ShopClock.now())

    fun setTitleFrame(frame: com.deepline.fishing.core.TitleFrame) {
        com.deepline.fishing.core.Showcase.setTitleFrame(progress, frame, ShopClock.now())?.let(::update)
    }

    val cardStyle: com.deepline.fishing.core.CardStyle get() = com.deepline.fishing.core.Showcase.cardStyle(progress)

    fun setCardStyle(style: com.deepline.fishing.core.CardStyle) {
        com.deepline.fishing.core.Showcase.setCardStyle(progress, style)?.let(::update)
    }

    fun toggleTankFish(speciesId: String) {
        com.deepline.fishing.core.Showcase.toggleTankFish(progress, speciesId)?.let(::update)
    }

    fun toggleDecor(decor: com.deepline.fishing.core.Decor) {
        com.deepline.fishing.core.Showcase.toggleDecor(progress, decor)?.let(::update)
    }

    fun openAquarium() {
        screen = Screen.AQUARIUM
    }

    // --- Menu themes ------------------------------------------------------------------------

    /** The theme of the menus (Lagoon unless another is chosen and available). */
    val menuTheme: com.deepline.fishing.core.MenuTheme
        get() = com.deepline.fishing.core.Themes.theme(progress, ShopClock.now())

    val nightMode: com.deepline.fishing.core.NightMode get() = com.deepline.fishing.core.Themes.nightMode(progress)

    fun setMenuTheme(theme: com.deepline.fishing.core.MenuTheme) {
        com.deepline.fishing.core.Themes.setTheme(progress, theme, ShopClock.now())?.let(::update)
    }

    fun setNightMode(mode: com.deepline.fishing.core.NightMode) {
        update(com.deepline.fishing.core.Themes.setNightMode(progress, mode))
    }

    fun openThemes() {
        screen = Screen.THEMES
    }

    fun openSettings() {
        screen = Screen.SETTINGS
    }

    fun buyUpgrade(stat: RopeStat) {
        Progression.upgrade(progress, stat)?.let {
            GameAnalytics.ropeChanged(stat, progress, it)
            update(it)
            feel.cue(com.deepline.fishing.feel.Cue.PURCHASE)
        }
    }

    fun buyLantern() {
        Progression.buyLantern(progress)?.let {
            GameAnalytics.tackleBought(Tackle.LANTERN, progress, it)
            update(it)
            feel.cue(com.deepline.fishing.feel.Cue.PURCHASE)
        }
    }

    fun claimMilestone(milestone: Milestone) {
        Progression.claimMilestone(progress, milestone)?.let {
            GameAnalytics.milestoneClaimed(milestone, progress, it)
            update(it)
            feel.cue(com.deepline.fishing.feel.Cue.CLAIM)
        }
    }

    // --- Battle flow ---------------------------------------------------------------------

    /** How the last wager went, for the battle screen. */
    data class WagerResult(val stake: Long, val won: Boolean?, val rival: BotDifficulty) {
        val payout: Long get() = when (won) {
            true -> (stake * GameConfig.wagerPayout(rival)).toLong()
            null -> stake
            false -> 0L
        }
    }

    var lastWagerResult by mutableStateOf<WagerResult?>(null)
        private set

    /** Points staked on the current battle against the computer (0: no wager). */
    var battleStake by mutableStateOf(0L)
        private set

    fun startBattle(mode: BattleMode, opponent: Opponent, stake: Long = 0L) {
        // Wagers only against the computer, and only what the player can afford.
        val wager = if (opponent is Opponent.Computer && stake > 0) Progression.placeWager(progress, stake) else null
        if (wager != null) update(wager)
        battleStake = if (wager != null) stake else 0L
        lastWagerResult = null
        // Names are part of the battle (core): written in the language shown when it starts.
        val texts = AppLanguages.localized(getApplication())
        val players = when (opponent) {
            is Opponent.Computer -> listOf(
                BattlePlayer(texts.getString(R.string.battle_player_you)),
                BattlePlayer(UiText.res(R.string.battle_player_rival, opponent.difficulty.labelText()).resolve(texts), opponent.difficulty),
            )
            Opponent.Friend -> listOf(
                BattlePlayer(texts.getString(R.string.battle_player_one)),
                BattlePlayer(texts.getString(R.string.battle_player_two)),
            )
            Opponent.Online -> return openOnlineLobby(mode)
        }
        battle = Battle(mode, players, Random.nextLong())
        battleOpponent = opponent
        battleRecorded = false
        battleTurn = null
        GameAnalytics.battleStarted(mode, opponent, battleStake)
        onBattleChanged()
        screen = Screen.BATTLE
    }

    fun rematch() {
        val b = battle ?: return
        val opponent = battleOpponent ?: return
        if (opponent == Opponent.Online) {
            clearBattle()
            openOnlineLobby(b.mode)
        } else {
            startBattle(b.mode, opponent)
        }
    }

    /** The next cast to play from this device, or null when waiting or finished. */
    fun battleNextTurn(): BattleTurn? {
        val online = onlineMatch
        return if (isOnlineBattle && online != null) online.myNextTurn() else battle?.nextTurn()
    }

    /**
     * Plays the next turn. A human turn starts a cast; computer turns are simulated instantly
     * (all of the computer's consecutive casts at once).
     */
    fun playNextTurn() {
        val online = onlineMatch
        if (isOnlineBattle && online != null) {
            val turn = online.myNextTurn() ?: return
            lastLiveSentAt = -1f
            beginSession(battleCast(OnlineProtocol.tournamentSpecs, turn), turn)
            return
        }
        val b = battle ?: return
        var turn = b.nextTurn() ?: return
        val firstBot = b.players[turn.playerIndex].bot
        if (firstBot == null) {
            // Both players use the same rope and the same water. Lanterns and detectors are
            // allowed (anyone can get them); the charm and the lure are solo only.
            beginSession(battleCast(progress.specs, turn), turn)
            return
        }
        while (true) {
            val bot = b.players[turn.playerIndex].bot ?: break
            b.record(turn, BotPlayer.playCast(progress.specs, turn.seed, bot, turn.timeLimitSec))
            turn = b.nextTurn() ?: break
        }
        onBattleChanged()
    }

    /** A battle cast on the turn's shared water, with the sight tackle switched on. */
    private fun battleCast(specs: RopeSpecs, turn: BattleTurn): CastSession {
        val (lantern, detector) = useSightTackle()
        return CastSession.seeded(specs, turn.seed, lanternActive = lantern, timeLeftAtStart = turn.timeLimitSec)
            .also { it.detectorActive = detector }
    }

    fun forfeitBattle(playerIndex: Int = 0) {
        if (isOnlineBattle) {
            // The server answers with MatchOver.
            connection?.send(ClientMessage.Forfeit)
        } else {
            battle?.forfeit(playerIndex)
            onBattleChanged()
        }
        screen = Screen.BATTLE
    }

    fun leaveBattle() {
        val wasOnline = isOnlineBattle
        val b = battle
        if (wasOnline) {
            if (onlineMatch?.outcome == null) connection?.send(ClientMessage.Forfeit)
        } else if (b != null && !b.isOver) {
            b.forfeit(0)
            onBattleChanged()
        }
        clearBattle()
        screen = if (wasOnline) Screen.ONLINE_LOBBY else Screen.BATTLE_SETUP
    }

    private fun clearBattle() {
        battle = null
        battleOpponent = null
        onlineMatch = null
        if (onlineStatus == OnlineStatus.IN_MATCH) onlineStatus = OnlineStatus.CONNECTED
    }

    private fun onBattleChanged() {
        battleVersion++
        val b = battle ?: return
        val outcome = (if (isOnlineBattle) onlineMatch?.outcome else b.outcome()) ?: return
        val me = if (isOnlineBattle) onlineMatch?.myIndex else if (battleOpponent is Opponent.Computer) 0 else null
        feel.onBattleOutcome(b, outcome.winner, me)
        if (!battleRecorded && battleOpponent is Opponent.Computer) {
            battleRecorded = true
            val won = outcome.winner?.let { it == 0 }
            update(Progression.recordBattle(progress, won))
            if (battleStake > 0) {
                val rival = (battleOpponent as Opponent.Computer).difficulty
                update(Progression.settleWager(progress, battleStake, rival, if (outcome.byForfeit && won != true) false else won))
                lastWagerResult = WagerResult(battleStake, won, rival)
                battleStake = 0L
            }
            // Shutout: a battle-result secret, paid into the player's points.
            val rival = (battleOpponent as Opponent.Computer).difficulty
            for (id in Secrets.revealedByBattle(b, outcome, playerIndex = 0, rival = rival)) {
                if (id in progress.discoveredSecrets) {
                    update(progress.copy(pointsBalance = progress.pointsBalance + SecretRules.SHUTOUT_BONUS))
                    GameAnalytics.earn(SecretRules.SHUTOUT_BONUS.toLong(), "secret_bonus")
                }
                onSecretTriggered(id)
            }
        }
        val wager = lastWagerResult
        GameAnalytics.battleEnded(b, outcome, battleOpponent, onlineMatch?.myIndex ?: 0, wager?.stake ?: 0L, wager?.payout ?: 0L)
    }

    /**
     * Handles the system back button: a pushed screen goes back to where it came from, any other
     * tab goes back to the Fish tab, and on the Fish tab back leaves the app (not handled here).
     */
    fun onBack() {
        when (screen) {
            Screen.HOME -> Unit
            Screen.CAST -> if (pendingResult != null) collectResult() else if (paused) resume() else pause()
            Screen.BATTLE -> if (battle?.isOver != false) leaveBattle() else Unit
            Screen.ONLINE_LOBBY -> closeOnlineLobby()
            Screen.FISH_DETAIL -> screen = Screen.DEX
            Screen.BOAT_EDITOR -> openDex(DexSection.SKINS)
            Screen.AQUARIUM -> openDex(DexSection.FISH)
            Screen.THEMES -> screen = Screen.HOME
            Screen.SETTINGS -> screen = Screen.HOME
            // -- Daily loop screens' back below.
            Screen.DAILY -> screen = Screen.HOME

            // -- Social screens' back below.
            Screen.SOCIAL -> screen = Screen.BATTLE_SETUP
            Screen.FRIENDS, Screen.LEADERBOARDS, Screen.TOURNAMENT, Screen.CLUBS -> screen = Screen.SOCIAL

            Screen.DEX, Screen.SHOP, Screen.BATTLE_SETUP, Screen.MILESTONES -> screen = Screen.HOME
        }
    }

    // --- Online lobby ---------------------------------------------------------------------

    fun openOnlineLobby(mode: BattleMode) {
        onlineMode = mode
        onlineError = null
        screen = Screen.ONLINE_LOBBY
    }

    fun closeOnlineLobby() {
        disconnect()
        screen = Screen.BATTLE_SETUP
    }

    fun updatePlayerName(name: String) {
        playerName = name.take(OnlineProtocol.MAX_NAME_LENGTH)
        store.putString(KEY_NAME, playerName)
    }

    fun updateServerUrl(url: String) {
        serverUrl = url.trim()
        store.putString(KEY_SERVER, serverUrl)
        // A different server needs a fresh connection.
        if (onlineStatus == OnlineStatus.CONNECTED) disconnect()
    }

    fun createRoom() = sendToServer(ClientMessage.CreateRoom(onlineMode))

    fun joinRoom(code: String) {
        val normalized = OnlineProtocol.normalizeRoomCode(code)
        if (normalized.length != OnlineProtocol.ROOM_CODE_LENGTH) {
            onlineError = UiText.res(R.string.online_error_code_length, OnlineProtocol.ROOM_CODE_LENGTH)
            return
        }
        sendToServer(ClientMessage.JoinRoom(normalized))
    }

    fun quickMatch() = sendToServer(ClientMessage.QuickMatch(onlineMode))

    fun cancelWaiting() {
        connection?.send(ClientMessage.CancelWaiting)
    }

    private fun sendToServer(message: ClientMessage) {
        onlineError = null
        if (connection == null) {
            onlineStatus = OnlineStatus.CONNECTING
            connection = OnlineConnection(viewModelScope, ::onServerMessage, ::onConnectionClosed).also {
                val frame = com.deepline.fishing.core.Showcase.titleFrame(progress, ShopClock.now())
                it.connect(serverUrl, OnlineProtocol.sanitizeName(playerName), title, frame.name, playerIdentity.id, playerIdentity.secret)
            }
        }
        connection?.send(message)
    }

    private fun onServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Welcome -> if (onlineStatus == OnlineStatus.CONNECTING) onlineStatus = OnlineStatus.CONNECTED
            is ServerMessage.RoomCreated -> {
                roomCode = message.code
                onlineStatus = OnlineStatus.WAITING_ROOM
                social.onRoomCreated(message.code, message.mode)
            }
            is ServerMessage.Waiting -> onlineStatus = OnlineStatus.WAITING_QUICK
            ServerMessage.WaitingCancelled -> {
                roomCode = null
                onlineStatus = OnlineStatus.CONNECTED
            }
            is ServerMessage.MatchStarted -> {
                val match = OnlineMatch(message)
                roomCode = null
                onlineMatch = match
                battle = match.battle
                battleOpponent = Opponent.Online
                battleRecorded = false
                battleTurn = null
                onlineStatus = OnlineStatus.IN_MATCH
                GameAnalytics.battleStarted(match.mode, Opponent.Online, stake = 0L)
                onBattleChanged()
                screen = Screen.BATTLE
            }
            is ServerMessage.Error -> {
                // The server's own words (English): see docs/localization.md for mapping known ones.
                onlineError = UiText.raw(message.message)
                if (onlineStatus == OnlineStatus.CONNECTING) onlineStatus = OnlineStatus.CONNECTED
            }
            is ServerMessage.CastRecorded, is ServerMessage.OpponentLive, is ServerMessage.MatchOver -> {
                onlineMatch?.apply(message)
                onBattleChanged()
            }
        }
    }

    private fun onConnectionClosed(reason: UiText) {
        connection = null
        roomCode = null
        onlineStatus = OnlineStatus.DISCONNECTED
        onlineError = reason
        val match = onlineMatch
        // Only the kind of failure: the reason can name the server's address.
        GameAnalytics.problem("online", if (match != null && match.outcome == null) "connection lost in a match" else "connection closed")
        if (match != null && match.outcome == null) {
            match.connectionLost(reason.resolve(AppLanguages.localized(getApplication())))
            if (screen == Screen.CAST && battleTurn != null) {
                session = null
                paused = false
                pendingResult = null
                battleTurn = null
                screen = Screen.BATTLE
            }
            onBattleChanged()
        }
    }

    private fun disconnect() {
        connection?.close()
        connection = null
        roomCode = null
        if (onlineMatch == null) onlineStatus = OnlineStatus.DISCONNECTED
    }

    // --- Social: friends, weekly leaderboards, the weekly tournament, clubs ---------------------------

    /** What the social screens show, and their server calls (tests swap `social.client` for a fake). */
    val social = com.deepline.fishing.social.SocialController(
        viewModelScope,
        store,
        identity = { playerIdentity },
        profile = { com.deepline.fishing.core.social.Profile(OnlineProtocol.sanitizeName(playerName), title, titleFrame.name) },
        progress = { progress },
        update = ::update,
        baseUrl = { com.deepline.fishing.core.online.AccountApi.httpBase(serverUrl) },
    )

    init {
        social.onLaunch()
    }

    /** Opens a social screen (Clubs only while the feature is on) and refreshes what it shows. */
    fun openSocial(target: Screen = Screen.SOCIAL) {
        if (target == Screen.CLUBS && !FeatureFlags.clubs) return
        screen = target
        social.onOpen(target)
    }

    /** The weekly tournament attempt being fished, when the current cast is one. */
    var tournamentAttempt by mutableStateOf<com.deepline.fishing.core.social.AttemptStart?>(null)
        private set

    /**
     * Uses one of the week's attempts and casts on the tournament's water: the standard rope and
     * the online rules; Lanterns and detectors are allowed, as in battles.
     */
    fun startTournamentAttempt() = social.startAttempt { start ->
        val (lantern, detector) = useSightTackle()
        val s = CastSession.seeded(com.deepline.fishing.core.social.Tournaments.specs, start.seed, lanternActive = lantern)
        s.detectorActive = detector
        beginSession(s, turn = null)
        tournamentAttempt = start
    }

    private fun finishTournamentAttempt(attempt: com.deepline.fishing.core.social.AttemptStart, result: CastResult?, s: CastSession?) {
        if (result != null && s != null) social.submitAttempt(attempt, BattleCast(result, s.elapsedSeconds).toDto())
        refreshTackleToggles()
        screen = Screen.TOURNAMENT
    }

    /** Challenges a friend: creates a battle room for [mode] and sends them its code. */
    fun challengeFriend(friend: com.deepline.fishing.core.social.FriendDto, mode: BattleMode) {
        social.prepareChallenge(friend)
        openOnlineLobby(mode)
        createRoom()
    }

    /** Joins the battle room a friend challenged the player to. */
    fun acceptChallenge(challenge: com.deepline.fishing.core.social.ChallengeDto) {
        social.onChallengeAccepted(challenge)
        openOnlineLobby(challenge.mode)
        joinRoom(challenge.code)
    }

    override fun onCleared() {
        connection?.close()
        accounts.close()
        social.close()
        billing.close()
        feel.release()
        super.onCleared()
    }

    private companion object {
        const val KEY_NAME = "online.name"
        const val KEY_SERVER = "online.server"
        const val KEY_TOUR_AGAIN = "tour.showAgain"
        const val KEY_TOUR_SEEN = "tour.seenVersion"
        const val KEY_GRANTED = "store.granted"
        const val KEY_PLAYER_ID = "account.id"
        const val KEY_PLAYER_SECRET = "account.secret"
        const val KEY_LAST_BACKUP = "account.lastBackup"
        const val KEY_PENDING_GIFTS = "gifts.pending"
        const val KEY_GIFTS_GRANTED = "gifts.granted"
        const val AUTO_BACKUP_MS = 10 * 60_000L
        const val MAX_GRANTED_TOKENS = 200
        const val LIVE_INTERVAL_SEC = 0.5f
    }
}
