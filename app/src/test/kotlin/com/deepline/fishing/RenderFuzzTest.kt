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
                    drawCastScene(session, t, measurer)
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
