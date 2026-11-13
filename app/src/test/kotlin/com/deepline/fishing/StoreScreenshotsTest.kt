package com.deepline.fishing

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.core.BattleMode
import com.deepline.fishing.core.BoatDesign
import com.deepline.fishing.core.BoatPalette
import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BoatParts
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.BotPlayer
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.DepthZone
import com.deepline.fishing.core.Emblem
import com.deepline.fishing.core.FlagStyle
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.MenuTheme
import com.deepline.fishing.core.PartRequirement
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.ProgressRepository
import com.deepline.fishing.core.SailStyle
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.feel.FeelSettings
import com.deepline.fishing.render3d.Render3dExport
import com.deepline.fishing.ui.CastScreen
import com.deepline.fishing.ui.ColorScript
import com.deepline.fishing.ui.DeepLineTheme
import com.deepline.fishing.ui.i18n.AppLanguages
import com.deepline.fishing.ui.render3d.SceneBuilder
import com.deepline.fishing.ui.render3d.gl.Render3d
import com.deepline.fishing.ui.theme.MenuPalettes
import com.deepline.fishing.ui.tour.Tour
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * The Google Play phone screenshots (docs/store/screenshots.md), in one listing language per
 * subclass. Only with `DEEPLINE_STORE_SCREENSHOTS=1`: `tools/store-screenshots/make-store-screenshots.sh`
 * runs it, draws the 3D frames with the WebGL preview and lays the shots out.
 *
 * Writes to `app/build/store-screenshots/<locale>/`:
 * - `screen_1..5.png` with `scene_1..5.json`: the cast screen's own overlay and HUD (2D labels, aim
 *   ring, notices, the results card) on a transparent background, and the very same moment as a 3D
 *   frame, which the WebGL preview draws underneath (as on a phone, where the HUD sits on the GL view);
 * - `screen_6..8.png`: the Dex, a battle and the boat editor, as the app draws them;
 * - `caption_1..8.png`: each caption in the game's own font, bold, sized to fit the band;
 * - `shots.json`: the band's colours for each shot (the menu palette, or the zone's water).
 *
 * The phone is 400 x 800 dp at 3x (1200 x 2400 px). Nothing shows a typed name or a fishing secret.
 */
abstract class StoreScreenshotsTest(private val locale: String) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val out by lazy { File("build/store-screenshots/$locale").apply { deleteRecursively(); mkdirs() } }
    private var main: ActivityScenario<MainActivity>? = null
    private var bare: ActivityScenario<ComponentActivity>? = null

    @After
    fun tearDown() {
        Render3d.overlayOnly = false
        main?.close()
        bare?.close()
    }

    @Test
    fun storeScreenshots() {
        assumeTrue("set DEEPLINE_STORE_SCREENSHOTS=1 to make the store screenshots", System.getenv("DEEPLINE_STORE_SCREENSHOTS") == "1")
        context.getSharedPreferences("deepline", Context.MODE_PRIVATE).edit().clear().commit()
        val store = SharedPrefsStore(context)
        ProgressRepository(store).save(StorePlayers.player())
        store.putString("tour.showAgain", "false")
        store.putString("tour.seenVersion", Tour.VERSION.toString())
        // Still pictures: no shake or flash between the overlay and the 3D frame (the particles stay).
        store.putString(FeelSettings.KEY_REDUCE_MOTION, "true")
        // The one-time "automatic translation" card is not part of the game being shown.
        AppLanguages.dismissMachineNotice(context)

        menuShots()
        castShots()
        captions()
    }

    // --- Shots 6 to 8: the app's own screens --------------------------------------------------

    private fun menuShots() {
        main = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        lateinit var vm: GameViewModel
        main!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }

        vm.openDex(DexSection.FISH)
        snap(6)

        // A Best of 5 against the computer rival, two rounds played, one each: round 3 is next.
        vm.battleSeeds = { StorePlayers.BATTLE_SEED }
        vm.startBattle(BattleMode.BEST_OF_FIVE, Opponent.Computer(BotDifficulty.NORMAL))
        repeat(2) {
            val turn = vm.battleNextTurn()!!
            vm.battle!!.record(turn, BotPlayer.playCast(vm.progress.specs, turn.seed, BotDifficulty.HARD, turn.timeLimitSec))
            vm.playNextTurn()
            compose.waitForIdle()
        }
        val b = vm.battle!!
        assertEquals("one round each", 1, b.roundWins(0))
        assertEquals("one round each", 1, b.roundWins(1))
        snap(7)
        vm.leaveBattle()

        vm.openBoatEditor()
        snap(8)
        main!!.close()
        main = null
    }

    private fun snap(n: Int) {
        compose.waitForIdle()
        assertNull("no dialog in a store shot", org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing })
        main!!.onActivity { captureWindows(it).also { b -> checkSize(b.width, b.height) }.savePng(File(out, "screen_$n.png")) }
    }

    private fun checkSize(w: Int, h: Int) {
        assertEquals("the phone's width in px", WIDTH, w)
        assertEquals("the phone's height in px", HEIGHT, h)
    }

    // --- Shots 1 to 5: casts, the HUD over the 3D frame ---------------------------------------

    private fun castShots() {
        Render3d.overlayOnly = true
        bare = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var vm: GameViewModel
        bare!!.onActivity { a ->
            vm = ViewModelProvider(a)[GameViewModel::class.java]
            a.setContent { DeepLineTheme(MenuTheme.LAGOON, night = false) { CastScreen(vm) } }
        }
        compose.waitForIdle()
        val shots = listOf(1 to StoreScenes.descent, 2 to StoreScenes.bump, 3 to StoreScenes.ascent, 4 to StoreScenes.dark, 5 to StoreScenes.results)
        for ((n, scene) in shots) {
            vm.castFactory = { _, _ -> scene.newSession() }
            compose.mainClock.autoAdvance = false
            vm.startCast()
            val s = vm.session!!
            var frames = 0
            while (!scene.done(s) && s.phase != CastPhase.SURFACED && frames++ < 60 * 120) vm.onFrame(StoreScene.FRAME, scene.steer(s))
            assertNull("no fishing secret is explained in a store shot", vm.secretPopup)
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            // A finger on the screen: the aim ring the hook glides to.
            scene.fingerX?.let { x -> compose.onRoot().performTouchInput { down(Offset(x / GameConfig.WORLD_WIDTH * width, height * 0.74f)) } }
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            if (n == 5) {
                val result = vm.pendingResult!!
                assertTrue("a Sparkling catch", result.catches.any { it.sparkling })
                assertTrue("no secret on the results", result.secretBonuses.isEmpty() && Secrets.revealedBy(result).isEmpty())
            }
            bare!!.onActivity { a -> captureWindows(a, transparent = true).also { b -> checkSize(b.width, b.height) }.savePng(File(out, "screen_$n.png")) }
            // The same moment in 3D, at the phone's full resolution.
            val frame = SceneBuilder().build(s, s.elapsedSeconds, WIDTH, HEIGHT, vm.skins, fx = vm.feel.fx(s))
            Render3dExport.write(frame, File(out, "scene_$n.json"))
            if (scene.fingerX != null) compose.onRoot().performTouchInput { up() }
            if (vm.pendingResult != null) vm.collectResult() else vm.abandonCast()
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
        Render3d.overlayOnly = false
    }

    // --- Captions -------------------------------------------------------------------------------

    /** Each caption as the game writes it (its font, bold), on a transparent band, and the band's colours. */
    private fun captions() {
        val captions = File("../docs/store/listings/$locale/screenshot_captions.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals("eight captions for $locale", 8, captions.size)
        val lagoon = MenuPalettes.of(MenuTheme.LAGOON, night = false)
        // The Lagoon's day sky behind the caption, its sea further down; the deep shot sits on its zone's water.
        val (deepTop, deepBottom) = ColorScript.zoneGradient(DepthZone.MIDNIGHT)
        val bands = (1..8).map { n ->
            if (n == 4) {
                Band(Color(deepTop), Color(deepTop), Color(deepBottom))
            } else {
                Band(lagoon.sky.first(), lagoon.sky.last(), lagoon.sea.last())
            }
        }
        val json = StringBuilder("{\"locale\":\"$locale\",\"width\":$WIDTH,\"height\":$HEIGHT,\"shots\":[")
        for ((i, text) in captions.withIndex()) {
            val n = i + 1
            val band = bands[i]
            val ink = textColorFor(band, lagoon.headline.first())
            // A number and its unit stay on one line ("300 m").
            val caption = text.replace(Regex("(\\d) (m)\\b"), "$1 $2")
            var size = 76
            var lines = 0
            while (true) {
                lines = renderCaption(caption, ink, size, File(out, "caption_$n.png"))
                if (lines <= 2 || size <= 64) break
                size -= 4
            }
            assertTrue("caption $n of $locale fits two lines at 64 px or more: \"$text\"", lines <= 2)
            if (i > 0) json.append(',')
            json.append("{\"n\":$n,\"caption\":\"").append(text.replace("\"", "\\\"")).append("\",\"fontPx\":$size,\"lines\":$lines")
                .append(",\"bandTop\":\"").append(hex(band.top)).append("\",\"bandBottom\":\"").append(hex(band.bottom))
                .append("\",\"background\":\"").append(hex(band.background)).append("\",\"ink\":\"").append(hex(ink))
                .append("\",\"contrast\":").append("%.2f".format(java.util.Locale.ROOT, min(contrast(ink, band.top), contrast(ink, band.bottom))))
                .append(",\"scene\":").append(n <= 5).append('}')
        }
        json.append("]}")
        File(out, "shots.json").writeText(json.toString())
    }

    private class Band(val top: Color, val bottom: Color, val background: Color)

    /** Dark ink or white, whichever reads at 4.5:1 on the whole band (dark first: the palette's headline). */
    private fun textColorFor(band: Band, ink: Color): Color {
        for (c in listOf(ink, Color.White)) if (min(contrast(c, band.top), contrast(c, band.bottom)) >= 4.5) return c
        error("no caption colour reads on the band")
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return max(la, lb).toDouble() / min(la, lb)
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    /** Draws [text] [px] high, centred in the frame's width, and returns its number of lines. */
    private fun renderCaption(text: String, color: Color, px: Int, file: File): Int {
        var lines = 0
        var heightPx = 0
        bare!!.onActivity { a ->
            a.setContent {
                val density = LocalDensity.current
                Box(Modifier.width(with(density) { FRAME_WIDTH.toDp() }).wrapContentHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text,
                        color = color,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontSize = with(density) { px.toSp() },
                            fontWeight = FontWeight.Bold,
                            lineHeight = 1.12.em,
                            letterSpacing = (-0.01).em,
                            lineBreak = LineBreak.Heading,
                        ),
                        onTextLayout = { r ->
                            lines = r.lineCount
                            heightPx = r.size.height
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
        bare!!.onActivity { a ->
            val full = captureWindows(a, transparent = true)
            android.graphics.Bitmap.createBitmap(full, 0, 0, FRAME_WIDTH, heightPx.coerceIn(1, full.height)).savePng(file)
        }
        return lines
    }

    companion object {
        const val WIDTH = 1200
        const val HEIGHT = 2400

        /** The game frame's width in the 1080 px wide store image (about 88%). */
        const val FRAME_WIDTH = 950
    }
}

/** The player in the store screenshots: about 50 species across three zones and a boat of their own. */
object StorePlayers {
    /** The battle's water: after two rounds, one each with close scores (checked by the test). */
    const val BATTLE_SEED = 42L

    /** A colourful design of options earned in play (points and progress), none bought with money. */
    val boat = BoatDesign(
        hull = BoatPalette.indexOf("Tangerine"),
        hullBottom = BoatPalette.indexOf("Navy"),
        trim = BoatPalette.indexOf("Sunflower"),
        cabin = BoatPalette.indexOf("Pearl"),
        sail = BoatPalette.indexOf("Seafoam"),
        flag = BoatPalette.indexOf("Rose"),
        pattern = HullPattern.STRIPE,
        sailStyle = SailStyle.TRIANGLE,
        flagStyle = FlagStyle.PENNANT,
        emblem = Emblem.STAR,
    )

    fun player(): PlayerProgress {
        val base = LayoutPlayers.midGame()
        val parts = listOf(
            BoatPart.color(boat.hull), BoatPart.color(boat.hullBottom), BoatPart.color(boat.trim), BoatPart.color(boat.cabin),
            BoatPart.color(boat.sail), BoatPart.color(boat.flag), BoatPart.of(boat.pattern), BoatPart.of(boat.sailStyle),
            BoatPart.of(boat.flagStyle), BoatPart.of(boat.emblem),
        )
        for (part in parts) {
            val r = BoatParts.requirement(part)
            check(r != PartRequirement.PremiumOnly && r != PartRequirement.ClubOnly) { "${part.key} is sold for money" }
        }
        // The Dex's first rows show all three states: Reed Perch only seen, Ribbon Eel not met yet
        // (two other species caught instead, so still 50).
        val seenOnly = "reed_perch"
        val unknown = "ribbon_eel"
        val extra = com.deepline.fishing.core.FishCatalog.all
            .filter { !it.lastBumpOnly && it.zone in setOf(DepthZone.SHALLOWS, DepthZone.TWILIGHT) && it.id !in base.collection && it.id !in base.seenSpecies }
            .take(2)
        val collection = base.collection - seenOnly - unknown + extra.associate { it.id to com.deepline.fishing.core.CollectionEntry(1, 0, it.minDepth + 12f, it.basePoints * 2) }
        return base.copy(
            collection = collection,
            seenSpecies = base.seenSpecies - unknown + extra.map { it.id },
            pointsBalance = 48_210,
            discoveredSecrets = emptySet(),
            boatDesign = boat,
            customBoatEquipped = true,
            unlockedBoatParts = parts.filter { BoatParts.requirement(it) is PartRequirement.Points }.map { it.key }.toSet(),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w400dp-h800dp-xxhdpi")
class StoreScreenshotsEnTest : StoreScreenshotsTest("en-US")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "fr-rFR-w400dp-h800dp-xxhdpi")
class StoreScreenshotsFrTest : StoreScreenshotsTest("fr-FR")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "es-rES-w400dp-h800dp-xxhdpi")
class StoreScreenshotsEsTest : StoreScreenshotsTest("es-ES")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "de-rDE-w400dp-h800dp-xxhdpi")
class StoreScreenshotsDeTest : StoreScreenshotsTest("de-DE")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "pt-rBR-w400dp-h800dp-xxhdpi")
class StoreScreenshotsPtTest : StoreScreenshotsTest("pt-BR")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "tr-rTR-w400dp-h800dp-xxhdpi")
class StoreScreenshotsTrTest : StoreScreenshotsTest("tr-TR")
