package com.deepline.fishing.ui.kit

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** True when the player asked for less motion: depth effects stay still (see docs/ui-design.md). */
val LocalCalmMotion = staticCompositionLocalOf { false }

// ---- Cards that lean under the finger ---------------------------------------------------------------

/** How far a pressed card leans, in degrees, and how close the "camera" is (smaller = more depth). */
private const val TILT_DEGREES = 7f
private const val TILT_CAMERA = 10f

/**
 * Makes a card feel like an object: pressed, it leans towards the finger (a few degrees, in
 * perspective) and a sheen crosses it, then it springs back. Looks only: taps go through to the
 * card as before. Still when the player asked for less motion.
 */
fun Modifier.pressTilt(sheen: Boolean = true): Modifier = composed {
    if (LocalCalmMotion.current) return@composed this
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val back = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // -1..1 from the card's centre: the touched corner goes down.
                val dx = ((down.position.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                val dy = ((down.position.y / size.height) * 2f - 1f).coerceIn(-1f, 1f)
                scope.launch { tiltY.animateTo(dx * TILT_DEGREES, spring(stiffness = Spring.StiffnessMedium)) }
                scope.launch { tiltX.animateTo(-dy * TILT_DEGREES, spring(stiffness = Spring.StiffnessMedium)) }
                waitForUpOrCancellation()
                scope.launch { tiltY.animateTo(0f, back) }
                scope.launch { tiltX.animateTo(0f, back) }
            }
        }
        .graphicsLayer {
            rotationX = tiltX.value
            rotationY = tiltY.value
            cameraDistance = TILT_CAMERA * density
            // Its own buffer while it leans, so the sheen stays inside the card's shape.
            compositingStrategy = if (tiltX.value != 0f || tiltY.value != 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .let { m ->
            if (!sheen) {
                m
            } else {
                m.drawWithContent {
                    drawContent()
                    val lean = (abs(tiltX.value) + abs(tiltY.value)) / (TILT_DEGREES * 2f)
                    if (lean > 0.01f) drawSheen(0.5f + tiltY.value / TILT_DEGREES * 0.5f, lean)
                }
            }
        }
}

/** A diagonal band of light at [at] (0..1 across the card), [strength] 0..1. */
private fun DrawScope.drawSheen(at: Float, strength: Float) {
    val w = size.width
    val x = at * w
    val band = w * 0.45f
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.3f * strength), Color.Transparent),
            start = Offset(x - band, 0f),
            end = Offset(x + band, size.height),
        ),
        blendMode = BlendMode.SrcAtop,
    )
}

// ---- Rewards that burst out of what was tapped -----------------------------------------------------

/** How long a burst lasts, in milliseconds. */
const val REWARD_BURST_MS = 800

/**
 * The coins that jump out of a claimed reward. A [GameButton] with the `CLAIM` role starts one
 * from its own centre (see [LocalRewardFx]); [RewardFxLayer] draws them above everything.
 */
@Stable
class RewardFx {
    internal class Burst(val origin: Offset, val seed: Int) {
        var startNanos = -1L
    }

    internal val bursts = mutableStateListOf<Burst>()
    private var count = 0

    /** Coins fly out of [origin] (in the window's coordinates). */
    fun burst(origin: Offset) {
        if (bursts.size < 4) bursts += Burst(origin, count++)
    }
}

/** Null where rewards shouldn't burst (reduced motion, previews). */
val LocalRewardFx = compositionLocalOf<RewardFx?> { null }

/** Draws [fx]'s bursts: put it above the screens, filling the window. Runs only while one is flying. */
@Composable
fun RewardFxLayer(fx: RewardFx, modifier: Modifier = Modifier) {
    if (fx.bursts.isEmpty()) return
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(fx) {
        while (fx.bursts.isNotEmpty()) {
            withFrameNanos { t ->
                now = t
                for (b in fx.bursts) if (b.startNanos < 0) b.startNanos = t
                fx.bursts.removeAll { t - it.startNanos > REWARD_BURST_MS * 1_000_000L }
            }
        }
    }
    Canvas(modifier) {
        for (b in fx.bursts) {
            if (b.startNanos < 0) continue
            drawBurst(b.origin, b.seed, ((now - b.startNanos) / (REWARD_BURST_MS * 1e6f)).coerceIn(0f, 1f))
        }
    }
}

private fun noise(k: Int, salt: Int): Float {
    var x = k * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/** Coins thrown up from [origin] in a fountain, spinning (their width breathes) and fading at the end. */
private fun DrawScope.drawBurst(origin: Offset, seed: Int, p: Float) {
    val u = 1.dp.toPx()
    val gold = Color(0xFFFFD54A)
    val edge = Color(0xFFB98300)
    // A flash where the reward was.
    if (p < 0.3f) drawCircle(Color.White.copy(alpha = 0.5f * (1f - p / 0.3f)), radius = (20f + 90f * p) * u, center = origin)
    for (k in 0 until 12) {
        val angle = (-PI / 2 + (noise(k, seed) - 0.5f) * 2.2f).toFloat()
        val speed = (150f + noise(k, seed + 7) * 150f) * u
        val t = p * 1.1f
        val x = origin.x + cos(angle) * speed * t
        // Up, then pulled back down.
        val y = origin.y + sin(angle) * speed * t + 330f * u * t * t
        val alpha = if (p > 0.7f) (1f - p) / 0.3f else 1f
        val r = (5f + noise(k, seed + 3) * 3f) * u
        val spin = abs(cos(p * 9f + k))
        val half = androidx.compose.ui.geometry.Size(r * 2f * (0.25f + 0.75f * spin), r * 2f)
        val topLeft = Offset(x - half.width / 2f, y - r)
        drawOval(edge.copy(alpha = alpha), topLeft = topLeft + Offset(0f, 1.5f * u), size = half)
        drawOval(gold.copy(alpha = alpha), topLeft = topLeft, size = half)
        drawOval(Color.White.copy(alpha = 0.55f * alpha), topLeft = topLeft + Offset(half.width * 0.2f, r * 0.3f), size = half * 0.35f)
    }
}

// ---- The scene shifts as the phone tilts -----------------------------------------------------------

/**
 * How the phone is tilted from the way it is usually held, -1..1 on each axis, smoothed: the menu
 * scene's layers shift by it (near ones more), which gives the flat scene depth. Zero where there
 * is no sensor, when [enabled] is false, and while the app is in the background (no battery used).
 * The neutral position follows the player slowly, so any way of holding the phone is "straight".
 */
@Composable
fun rememberDeviceTilt(enabled: Boolean): State<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, lifecycle) {
        val manager = if (enabled) context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager else null
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val listener = object : SensorEventListener {
            var restX = Float.NaN
            var restY = 0f
            var x = 0f
            var y = 0f

            override fun onSensorChanged(event: SensorEvent) {
                val gx = event.values[0] / SensorManager.GRAVITY_EARTH
                val gy = event.values[1] / SensorManager.GRAVITY_EARTH
                if (restX.isNaN()) {
                    restX = gx
                    restY = gy
                }
                // The rest position drifts towards where the phone is held.
                restX += (gx - restX) * 0.01f
                restY += (gy - restY) * 0.01f
                val tx = ((gx - restX) * 3f).coerceIn(-1f, 1f)
                val ty = ((gy - restY) * 3f).coerceIn(-1f, 1f)
                x += (tx - x) * 0.18f
                y += (ty - y) * 0.18f
                if (abs(x - tilt.value.x) > 0.004f || abs(y - tilt.value.y) > 0.004f) tilt.value = Offset(x, y)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false

        fun listen(on: Boolean) {
            if (on == registered) return
            registered = on
            if (on) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) else manager.unregisterListener(listener)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> listen(true)
                Lifecycle.Event.ON_PAUSE -> listen(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) listen(true)
        onDispose {
            lifecycle.removeObserver(observer)
            listen(false)
        }
    }
    return tilt
}
