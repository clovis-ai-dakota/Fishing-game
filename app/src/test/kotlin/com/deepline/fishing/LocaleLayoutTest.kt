package com.deepline.fishing

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.analytics.RecordingTelemetry
import com.deepline.fishing.analytics.Telemetry
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CollectionEntry
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.ObstacleKind
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.core.ShopClock
import com.deepline.fishing.core.social.ChallengeDto
import com.deepline.fishing.core.social.FriendDto
import com.deepline.fishing.core.social.Leaderboards
import com.deepline.fishing.core.social.PlayerCard
import com.deepline.fishing.feel.FeelSettings
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Translated layouts: renders the game's main screens in one language (the subclasses: German
 * and French, the longest, and Turkish) into `app/build/screenshots/locales/<lang>/`, and checks
 * every text on them with [TextAudit] (cut off, truncated, broken words, a number torn from its
 * unit, overlapping texts). Problems are listed in `report.txt` there and fail the test.
 *
 * Covers the consent sheet and every tour card, then a mid-game player's Fish tab, daily screen,
 * milestones, Settings, every Shop page, themes, aquarium, the Dex (species, secrets, skins) and a
 * species page, the boat editor, the Battle tab, the online lobby, a battle from start to end,
 * casts (HUD, notices, the fast reel, pause, results) and the social screens.
 *
 * One class per language: each gets a fresh JVM (see `forkEvery`), so nothing leaks between them.
 * Run one: `./gradlew :app:testDebugUnitTest --tests '*LocaleLayoutDeTest'`.
 */
abstract class LocaleLayoutTest(private val language: String) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var vm: GameViewModel
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store by lazy { SharedPrefsStore(context) }
    private val dir by lazy { File("build/screenshots/locales/$language").apply { deleteRecursively(); mkdirs() } }
    private val problems = ArrayList<TextProblem>()
    private var count = 0

    /**
     * Truncations that are fine where they are (a name in a narrow card); each with its reason.
     * Matched on the problem's kind and text.
     */
    protected open val accepted: List<Pair<String, Regex>> = emptyList()

    @After
    fun tearDown() {
        scenario?.close()
        Telemetry.uninstall()
    }

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
    }

    private fun shot(name: String, overlaps: Boolean = true) {
        if (compose.mainClock.autoAdvance) {
            compose.waitForIdle()
        } else {
            // State changed from the test (a paused clock): let the recomposer see it, then draw.
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
        }
        val file = "%03d_%s".format(++count, name)
        scenario!!.onActivity { captureWindows(it).savePng(File(dir, "$file.png")) }
        problems += TextAudit.check(compose, file, overlaps)
    }

    /** The biggest list that scrolls down on the screen now, if any. */
    private fun mainScroller(): SemanticsNode? =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .filter { SemanticsActions.ScrollBy in it.config }
            .maxByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }

    /** Shots of the whole screen, scrolling down a page at a time (at most [max] pages). */
    private fun pages(name: String, max: Int = 4, overlaps: Boolean = true) {
        shot(name, overlaps)
        for (page in 2..max) {
            val node = mainScroller() ?: return
            val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
            val before = range.value()
            if (before >= range.maxValue() - 1f) return
            compose.onNode(SemanticsMatcher("node ${node.id}") { it.id == node.id }).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, node.boundsInRoot.height * 0.8f)
            }
            compose.waitForIdle()
            if (range.value() <= before + 1f) return
            shot("${name}_$page", overlaps)
        }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun tap(tag: String) {
        val node = compose.onNodeWithTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun waitFor(tag: String) = runCatching { compose.waitUntil(5_000) { exists(tag) } }

    @Test
    fun everyScreenFits() {
        firstLaunch()
        midGame()
        casts()
        battle()
        social()
        val report = File(dir, "report.txt")
        val left = problems.filterNot { p -> accepted.any { (kind, text) -> p.kind == kind && text.containsMatchIn(p.text) } }
        report.writeText(
            if (problems.isEmpty()) "No text problems in $count screens.\n"
            else problems.joinToString("\n", "Text problems in $count screens (${left.size} not accepted):\n", "\n"),
        )
        assertTrue("Text problems in $language (see ${report.path}):\n" + left.joinToString("\n"), left.isEmpty())
    }

    /** The consent sheet (as in a build with Firebase), then every card of the tour. */
    private fun firstLaunch() {
        context.getSharedPreferences("deepline", Context.MODE_PRIVATE).edit().clear().commit()
        Telemetry.install(RecordingTelemetry())
        launch()
        shot("consent")
        tap("consent_choose")
        shot("consent_choose")
        tap("consent_refuse")
        compose.waitForIdle()
        var step = 0
        while (vm.tour != null && step < 80) {
            val id = vm.tour!!.current.id
            // The tour card is drawn over the screen it points at: their texts may overlap.
            shot("tour_${id}", overlaps = false)
            vm.tourNext()
            compose.waitForIdle()
            step++
        }
        if (vm.askTourAgain) {
            shot("tour_again")
            vm.answerTourAgain(false)
        }
        Telemetry.uninstall()
    }

    private fun midGame() {
        scenario?.close()
        scenario = null
        context.getSharedPreferences("deepline", Context.MODE_PRIVATE).edit().clear().commit()
        ProgressRepository(store).save(LayoutPlayers.midGame())
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        // Still pictures: no shake or flash between frames.
        store.putString(FeelSettings.KEY_REDUCE_MOTION, "true")
        launch()
        vm.social.client = LayoutPlayers.socialClient()
        vm.updateServerUrl("ws://127.0.0.1:1/battle")

        pages("home")
        vm.navigate(Screen.MILESTONES)
        pages("milestones", max = 3)
        vm.openDaily()
        pages("daily")
        vm.openSettings()
        pages("settings", max = 8)
        for (page in vm.shopPages) {
            vm.openShop(page)
            pages("shop_${page.name.lowercase()}", max = 6)
        }
        // A points purchase asks first.
        vm.openShop(ShopPage.TACKLE)
        compose.waitForIdle()
        if (exists("buy_pts_lantern_crate")) {
            tap("buy_pts_lantern_crate")
            assertTrue("the points confirmation opens", exists("confirm_points"))
            shot("shop_confirm")
            tap("confirm_points")
            shot("shop_bought")
            vm.dismissShopMessage()
        }
        vm.openThemes()
        pages("themes", max = 3)
        vm.openAquarium()
        pages("aquarium", max = 2)
        for (section in vm.availableDexSections) {
            vm.openDex(section)
            pages("dex_${section.name.lowercase()}", max = 5)
        }
        val caught = FishCatalog.all.indexOfFirst { it.id in vm.progress.collection }
        val seen = FishCatalog.all.indexOfFirst { it.id in vm.progress.seenSpecies && it.id !in vm.progress.collection }
        val unknown = FishCatalog.all.indexOfFirst { com.deepline.fishing.core.Progression.dexState(vm.progress, it.id) == com.deepline.fishing.core.DexState.UNKNOWN }
        for ((name, index) in listOf("caught" to caught, "seen" to seen, "unknown" to unknown)) {
            if (index < 0) continue
            vm.openDex(com.deepline.fishing.DexSection.FISH)
            compose.waitForIdle()
            vm.openFishDetail(index)
            pages("species_$name", max = 2)
        }
        vm.openBoatEditor()
        pages("boat_editor", max = 5)
        vm.selectTab(MainTab.BATTLE)
        pages("battle_setup", max = 4)
        vm.openOnlineLobby(BattleMode.BEST_OF_FIVE)
        pages("online_lobby", max = 2)
        vm.closeOnlineLobby()
    }

    /** Plays [scene] frame by frame (the clock paused), as the cast screen would. */
    private fun play(scene: StoreScene) {
        vm.castFactory = { _, _ -> scene.newSession() }
        compose.mainClock.autoAdvance = false
        vm.startCast()
        val s = vm.session!!
        var frames = 0
        while (!scene.done(s) && s.phase != CastPhase.SURFACED && frames++ < 60 * 120) {
            vm.onFrame(StoreScene.FRAME, scene.steer(s))
            if (vm.secretPopup != null) return
        }
    }

    private fun leaveCast() {
        if (vm.pendingResult != null) vm.collectResult() else if (vm.session != null) vm.abandonCast()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun casts() {
        for (scene in StoreScenes.all) {
            play(scene)
            shot("cast_${scene.name}")
            if (scene === StoreScenes.descent) {
                vm.pause()
                shot("cast_paused")
                vm.resume()
            }
            leaveCast()
        }
        // All four bumps: the last one hooks a rock (a secret, explained once), then the fast reel.
        play(LayoutPlayers.fourBumps)
        shot("cast_secret_found")
        while (vm.secretPopup != null) vm.answerSecretPopup(showAgain = false)
        val s = vm.session!!
        repeat(30) { vm.onFrame(StoreScene.FRAME, null) }
        shot("cast_fast_reel")
        vm.fastReel()
        while (s.phase != CastPhase.SURFACED) vm.onFrame(StoreScene.FRAME, null)
        shot("cast_results_snag")
        while (vm.secretPopup != null) vm.answerSecretPopup(showAgain = false)
        leaveCast()
    }

    private fun battle() {
        vm.battleSeeds = { 42L }
        vm.startBattle(BattleMode.BEST_OF_FIVE, Opponent.Computer(BotDifficulty.NORMAL))
        shot("battle_start")
        fun playOneRound() {
            val b = vm.battle ?: return
            val turn = vm.battleNextTurn() ?: return
            b.record(turn, BotPlayer.playCast(vm.progress.specs, turn.seed, BotDifficulty.HARD, turn.timeLimitSec))
            vm.playNextTurn()
            compose.waitForIdle()
        }
        playOneRound()
        playOneRound()
        pages("battle_round3", max = 3)
        // The player's own cast in a battle: the HUD names the round.
        compose.mainClock.autoAdvance = false
        vm.playNextTurn()
        repeat(90) { vm.onFrame(StoreScene.FRAME, GameConfig.WORLD_WIDTH / 2f) }
        shot("battle_cast")
        vm.abandonCast()
        compose.mainClock.autoAdvance = true
        shot("battle_over")
        vm.leaveBattle()
        // A timed battle's cast shows its clock.
        vm.startBattle(BattleMode.TIME_ATTACK, Opponent.Computer(BotDifficulty.EASY))
        compose.mainClock.autoAdvance = false
        vm.playNextTurn()
        if (vm.session != null) {
            repeat(60) { vm.onFrame(StoreScene.FRAME, null) }
            shot("battle_cast_timed")
            vm.abandonCast()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        vm.leaveBattle()
    }

    private fun social() {
        vm.selectTab(MainTab.BATTLE)
        vm.openSocial(Screen.SOCIAL)
        waitFor("my_player_id")
        pages("social_hub", max = 3)
        vm.openSocial(Screen.FRIENDS)
        waitFor("friend_id")
        pages("friends", max = 3)
        vm.openSocial(Screen.LEADERBOARDS)
        waitFor("board_me")
        pages("leaderboards", max = 2)
        vm.openSocial(Screen.TOURNAMENT)
        waitFor("tournament_attempts")
        pages("tournament", max = 3)
        if (FeatureFlags.clubs) {
            vm.openSocial(Screen.CLUBS)
            compose.waitForIdle()
            pages("clubs", max = 3)
        }
        // Social calls that fail show the offline card.
        (vm.social.client as FakeSocialClient).reachable = false
        vm.openSocial(Screen.SOCIAL)
        waitFor("social_offline")
        shot("social_offline")
    }
}

/** The players and data the locale layout test shows. Names are generated, never typed. */
object LayoutPlayers {
    /** A player 70-odd casts in: 50 species caught across three zones, a few seen, two secrets found. */
    fun midGame(): PlayerProgress {
        val zones = mapOf(DepthZone.SHALLOWS to 22, DepthZone.TWILIGHT to 18, DepthZone.MIDNIGHT to 10)
        val caught = zones.flatMap { (zone, n) -> FishCatalog.all.filter { it.zone == zone && !it.lastBumpOnly }.take(n) }
        val seen = zones.keys.flatMap { zone -> FishCatalog.all.filter { it.zone == zone && it !in caught }.take(4) }
        return PlayerProgress(
            // A saver: enough for a Lantern crate (the points confirmation), and a long number.
            pointsBalance = 1_248_210,
            pointsEarned = 182_400,
            ropeLevels = mapOf(RopeStat.LENGTH to 6, RopeStat.STRENGTH to 5, RopeStat.AGILITY to 4),
            lanterns = 4,
            luckyCharms = 1,
            sparkleLures = 1,
            detectors = 2,
            collection = caught.mapIndexed { i, sp ->
                sp.id to CollectionEntry(caught = 1 + i % 4, sparklingCaught = if (i % 9 == 0) 1 else 0, deepestM = sp.minDepth + 12f, bestPoints = sp.basePoints * 3)
            }.toMap(),
            seenSpecies = (caught + seen).map { it.id }.toSet(),
            castsMade = 74,
            battleWins = 6,
            battleLosses = 4,
            discoveredSecrets = setOf(Secrets.FREE_PASS, Secrets.SURFACE_SKIM),
            claimedMilestones = setOf(0, 1, 2),
        )
    }

    /** A social server with a friend, a request and a challenge, all under generated names. */
    fun socialClient(): FakeSocialClient = FakeSocialClient().apply {
        val friend = "FRIEND42"
        friendsList += FriendDto(friend, Leaderboards.anglerName(friend), online = true, weekBestCast = 1_840, weekDeepestM = 212f)
        incoming += PlayerCard("ASKER777", Leaderboards.anglerName("ASKER777"))
        challenges += ChallengeDto(friend, Leaderboards.anglerName(friend), "QWERT", BattleMode.DEEP_DIVE, ShopClock.now() + 600_000L)
    }

    /** Four rocks on the hook's path below the bump-free zone: the last bump snags the fourth. */
    val fourBumps = StoreScene(
        name = "four_bumps",
        specs = StoreScenes.specs(length = 2, strength = 3, agility = 2),
        lantern = false,
        world = {
            com.deepline.fishing.core.World(
                fish = emptyList(),
                obstacles = (0 until 4).map { i -> com.deepline.fishing.core.Obstacle(i, ObstacleKind.ROCK, GameConfig.WORLD_WIDTH / 2f, 60f + i * 12f, 1.2f) },
            )
        },
        steer = { null },
        done = { s -> s.phase == CastPhase.ASCENDING && s.elapsedSeconds - s.lastTouchAtSeconds > 0.3f },
    )
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "de-w400dp-h860dp-xxhdpi")
class LocaleLayoutDeTest : LocaleLayoutTest("de")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "fr-w400dp-h860dp-xxhdpi")
class LocaleLayoutFrTest : LocaleLayoutTest("fr")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "tr-w400dp-h860dp-xxhdpi")
class LocaleLayoutTrTest : LocaleLayoutTest("tr")
