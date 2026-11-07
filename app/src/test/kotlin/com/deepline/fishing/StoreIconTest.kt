package com.deepline.fishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The launcher icon is an adaptive icon with a monochrome layer (themed icons), and the 512 x 512
 * Play Store icon is drawn from the same layers. Writes `build/store/play-icon-512.png` (copied to
 * docs/store/graphics/ by tools/store-graphics/make-store-graphics.sh) and previews of the masked
 * and themed icons.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StoreIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val out = File("build/store").apply { mkdirs() }

    private fun adaptive(): AdaptiveIconDrawable = context.getDrawable(R.drawable.ic_launcher) as AdaptiveIconDrawable

    /** Draws [layer] (a 108-unit adaptive icon layer) so that units [from]..[from]+[span] fill [size] px. */
    private fun Canvas.drawLayer(layer: Drawable, size: Int, from: Float, span: Float) {
        val s = size / span
        layer.setBounds((-from * s).toInt(), (-from * s).toInt(), ((108f - from) * s).toInt(), ((108f - from) * s).toInt())
        layer.draw(this)
    }

    private fun save(bitmap: Bitmap, name: String) =
        File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun theLauncherIconIsAdaptiveWithAMonochromeLayer() {
        val icon = adaptive()
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        assertNotNull("themed icons need a monochrome layer", icon.monochrome)
    }

    @Test
    fun playStoreIcon() {
        val icon = adaptive()
        val size = 512
        // Full square, no transparency, no rounded corners: Play applies the mask and shadow.
        // Units 12..96 of the 108-unit layers: the safe zone and a little more, so the art fills it.
        val play = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(play).apply {
            drawLayer(icon.background, size, 12f, 84f)
            drawLayer(icon.foreground, size, 12f, 84f)
        }
        save(play, "play-icon-512.png")
        for ((x, y) in listOf(0 to 0, size - 1 to 0, 0 to size - 1, size - 1 to size - 1, size / 2 to size / 2)) {
            assertEquals("the Play icon is opaque at ($x, $y)", 255, Color.alpha(play.getPixel(x, y)))
        }

        // What launchers show: the middle 72 units under a circle and a rounded square, and the
        // themed version (monochrome layer tinted on a dark tonal background).
        val preview = Bitmap.createBitmap(size * 3, size, Bitmap.Config.ARGB_8888)
        Canvas(preview).apply {
            drawColor(Color.rgb(236, 241, 245))
            val masks = listOf(
                Path().apply { addCircle(size / 2f, size / 2f, size * 0.42f, Path.Direction.CW) },
                Path().apply { addRoundRect(RectF(size * 0.08f, size * 0.08f, size * 0.92f, size * 0.92f), size * 0.2f, size * 0.2f, Path.Direction.CW) },
            )
            masks.forEachIndexed { i, mask ->
                save()
                translate(i * size.toFloat(), 0f)
                clipPath(mask)
                // The mask covers units 18..90 (72 units) scaled to 84% of the cell.
                val span = 72f / 0.84f
                drawLayer(icon.background, size, 54f - span / 2f, span)
                drawLayer(icon.foreground, size, 54f - span / 2f, span)
                restore()
            }
            save()
            translate(2f * size, 0f)
            clipPath(masks[0])
            drawColor(Color.rgb(32, 54, 64))
            val mono = icon.monochrome!!.mutate()
            mono.colorFilter = PorterDuffColorFilter(Color.rgb(176, 236, 248), PorterDuff.Mode.SRC_IN)
            val span = 72f / 0.84f
            drawLayer(mono, size, 54f - span / 2f, span)
            restore()
        }
        save(preview, "launcher-icon-preview.png")

        // The themed layer isn't empty.
        var lit = 0
        for (x in 0 until size step 8) for (y in 0 until size step 8) {
            if (Color.red(preview.getPixel(2 * size + x, y)) > 150) lit++
        }
        assertTrue("the themed icon shows the line, hook and fish ($lit samples)", lit > 40)
    }
}
