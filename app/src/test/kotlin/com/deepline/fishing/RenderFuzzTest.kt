package com.deepline.fishing

import android.content.Context
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.deepline.fishing.core.BotBrain
import com.deepline.fishing.core.BotDifficulty
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.RopeSpecs
import com.deepline.fishing.core.RopeStat
import com.deepline.fishing.ui.drawCastOverlay
import com.deepline.fishing.ui.drawCastScene
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Draws EVERY frame of many complete casts, the way a phone does, so any exception thrown
 * while rendering (at any depth, scroll position or screen size) fails the test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class RenderFuzzTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun playAndRenderEveryFrame(
        specs: RopeSpecs,
        seed: Long,
        lantern: Boolean,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        skins: com.deepline.fishing.ui.Skins = com.deepline.fishing.ui.Skins.CLASSIC,
        detect: Set<String>? = null,
    ) {
        val d = Density(density)
        val measurer = TextMeasurer(createFontFamilyResolver(context), d, LayoutDirection.Ltr)
        val bitmap = ImageBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)
        val drawScope = CanvasDrawScope()
        val session = CastSession.create(specs, lantern, Random(seed))
        val brain = BotBrain(BotDifficulty.HARD, Random(seed))
        var t = 0f
        var frame = 0
        while (session.phase != CastPhase.SURFACED && frame < 20_000) {
            val dt = 1f / 60f
            session.update(dt, brain.steer(session, dt))
            t += dt
            try {
                drawScope.draw(d, LayoutDirection.Ltr, canvas, Size(widthPx.toFloat(), heightPx.toFloat())) {
                    drawCastScene(session, t, measurer, aimX = session.hookX + 1f, skins = skins, detect = detect)
                    // The 2D layer drawn over the 3D renderer.
                    drawCastOverlay(session, t, measurer, aimX = session.hookX + 1f, detect = detect)
                }
            } catch (e: Throwable) {
                throw AssertionError(
                    "Render crash at frame $frame (depth ${session.hookDepth} m, ${session.phase}, " +
                        "screen ${widthPx}x$heightPx, rope ${specs.lengthM} m, seed $seed): $e",
                    e,
                )
            }
            frame++
        }
    }

    private fun rope(level: Int) = RopeSpecs.fromLevels(RopeStat.entries.associateWith { level })

    /** A real phone screen (1080x2400): the starting rope, which crashed before the fix. */
    @Test
    fun startingRopeOnAPhoneScreen() {
        playAndRenderEveryFrame(rope(0), seed = 1, lantern = false, widthPx = 1080, heightPx = 2400, density = 2.625f)
        // And with every skin on.
        for (set in listOf("Giant-slayer", "Prismatic", "Sovereign")) {
            playAndRenderEveryFrame(rope(0), seed = 1, lantern = false, widthPx = 540, heightPx = 1200, density = 1.3f, skins = com.deepline.fishing.ui.Skins.ofSet(set))
        }
        // The shop's gear, and a boat with the new paints and prints.
        val C = com.deepline.fishing.core.Cosmetic.entries.filter { it.fromShop && it.slot != com.deepline.fishing.core.CosmeticSlot.TITLE }
        for (i in 0 until 3) {
            val gear = C.filterIndexed { k, _ -> k % 3 == i }.associateBy { it.slot }
            val boat = com.deepline.fishing.core.PremiumSkins.all[i * 4 % com.deepline.fishing.core.PremiumSkins.all.size].design
            playAndRenderEveryFrame(rope(0), seed = 10L + i, lantern = false, widthPx = 540, heightPx = 1200, density = 1.3f, skins = com.deepline.fishing.ui.Skins.of(gear, customBoat = boat))
        }
    }

    /** Every menu theme's scene, by day and at night, on several screens and over time. */
    @Test
    fun everyThemeBackdropDraws() {
        val d = Density(2f)
        for ((w, h) in listOf(1080 to 2400, 360 to 800, 200 to 150, 2 to 2)) {
            val bitmap = ImageBitmap(w, h)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            for (theme in com.deepline.fishing.core.MenuTheme.entries) for (night in listOf(false, true)) {
                val palette = com.deepline.fishing.ui.theme.MenuPalettes.of(theme, night)
                for (t in listOf(0f, 3.7f, 59.9f, 599f)) {
                    try {
                        scope.draw(d, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                            com.deepline.fishing.ui.theme.drawBackdropForTest(this, palette, t)
                        }
                    } catch (e: Throwable) {
                        throw AssertionError("Backdrop crash: $theme night=$night ${w}x$h t=$t: $e", e)
                    }
                }
            }
        }
    }

    /** The rare fish detector's markers, deep in the dark (every species counts as uncaught). */
    @Test
    fun detectorMarksInTheDark() {
        val all = com.deepline.fishing.core.FishCatalog.all.map { it.id }.toSet()
        playAndRenderEveryFrame(rope(10), seed = 21, lantern = true, widthPx = 360, heightPx = 800, density = 1f, detect = all)
    }

    /**
     * Deeper ropes (dark zone, lantern, rope limit far down) on small screens. Software rendering
     * cost grows with pixel count, and the same drawing code runs at any size.
     */
    @Test
    fun deeperRopesOnSmallScreens() {
        val screens = listOf(Triple(360, 800, 1f), Triple(320, 712, 0.9f))
        var seed = 2L
        for (level in listOf(0, 5, 10)) {
            for ((w, h, density) in screens) {
                playAndRenderEveryFrame(rope(level), seed, lantern = seed % 2 == 0L, w, h, density)
                seed++
            }
        }
    }
}
