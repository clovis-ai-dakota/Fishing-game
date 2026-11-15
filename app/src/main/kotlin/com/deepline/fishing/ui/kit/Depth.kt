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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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

// ---- Points that fly to and from the balance ------------------------------------------------------

/** How long a burst lasts, in milliseconds. */
const val REWARD_BURST_MS = 800

/**
 * Coins in motion, so the player sees where points come from and where they go without a word
 * (what Royal Match does for every resource): a claimed reward's coins fly from its button to
 * the balance, points earned by a cast fly to it from the button that collected them, and points
 * spent fly out of it to what was bought. Where no balance is on screen, a claim's coins just
 * jump up and fall. [RewardFxLayer] draws them above everything.
 */
@Stable
class RewardFx {
    internal class Burst(val from: Offset, val to: Offset?, val spending: Boolean, val seed: Int) {
        var startNanos = -1L
    }

    internal val bursts = mutableStateListOf<Burst>()
    private var count = 0

    /** Where the points balance is shown right now (see [rewardBalance]), and who said so. */
    internal var balanceAt: Offset? = null
    internal var balanceOwner: Any? = null

    /** The last button pressed that pays or collects: where spent points go, where earned ones come from. */
    internal var lastPressAt: Offset? = null

    private fun add(from: Offset, to: Offset?, spending: Boolean) {
        if (bursts.size < 4) bursts += Burst(from, to, spending, count++)
    }

    /** A reward was claimed at [origin] (in the root's coordinates): its coins go to the balance. */
    fun burst(origin: Offset) = add(origin, balanceAt, spending = false)

    /** The balance went up without a claim (a cast was collected): coins fly to it from the last press. */
    fun earned() {
        val to = balanceAt ?: return
        val from = lastPressAt ?: return
        if (bursts.isEmpty()) add(from, to, spending = false)
    }

    /** The balance went down: coins leave it for what was bought. */
    fun spent() {
        val from = balanceAt ?: return
        add(from, lastPressAt ?: (from + Offset(0f, 300f)), spending = true)
    }
}

/** Null where coins shouldn't fly (reduced motion, previews). */
val LocalRewardFx = compositionLocalOf<RewardFx?> { null }

/** Marks where the points balance is shown: coins fly to it and out of it. */
fun Modifier.rewardBalance(): Modifier = composed {
    val fx = LocalRewardFx.current ?: return@composed this
    val owner = remember { Any() }
    DisposableEffect(fx) {
        onDispose {
            if (fx.balanceOwner === owner) {
                fx.balanceOwner = null
                fx.balanceAt = null
            }
        }
    }
    onGloballyPositioned {
        fx.balanceOwner = owner
        fx.balanceAt = it.boundsInRoot().center
    }
}

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
            val p = ((now - b.startNanos) / (REWARD_BURST_MS * 1e6f)).coerceIn(0f, 1f)
            when {
                b.to == null -> drawFountain(b.from, b.seed, p)
                b.spending -> drawFlight(b.from, b.to, b.seed, p, coins = 6, scatter = 0f)
                else -> drawFlight(b.from, b.to, b.seed, p, coins = 10, scatter = 1f)
            }
        }
    }
}

private fun noise(k: Int, salt: Int): Float {
    var x = k * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

private val CoinGold = Color(0xFFFFD54A)
private val CoinEdge = Color(0xFFB98300)

/** One coin at [center], [spin] 0..1 of a turn (its width breathes as it spins). */
private fun DrawScope.drawCoin(center: Offset, r: Float, spin: Float, alpha: Float) {
    val u = 1.dp.toPx()
    val size = androidx.compose.ui.geometry.Size(r * 2f * (0.25f + 0.75f * abs(cos(spin * 2f * PI.toFloat()))), r * 2f)
    val topLeft = Offset(center.x - size.width / 2f, center.y - r)
    drawOval(CoinEdge.copy(alpha = alpha), topLeft = topLeft + Offset(0f, 1.5f * u), size = size)
    drawOval(CoinGold.copy(alpha = alpha), topLeft = topLeft, size = size)
    drawOval(Color.White.copy(alpha = 0.55f * alpha), topLeft = topLeft + Offset(size.width * 0.2f, r * 0.3f), size = size * 0.35f)
}

/**
 * Coins from [from] to [to], one after the other: with [scatter], they first jump out around
 * where they came from (a reward opening), then all rush to the same place.
 */
private fun DrawScope.drawFlight(from: Offset, to: Offset, seed: Int, p: Float, coins: Int, scatter: Float) {
    val u = 1.dp.toPx()
    if (scatter > 0f && p < 0.25f) drawCircle(Color.White.copy(alpha = 0.45f * (1f - p / 0.25f)), radius = (18f + 80f * p) * u, center = from)
    for (k in 0 until coins) {
        // Each coin leaves a little after the one before.
        val q = ((p - k * 0.035f) / 0.65f).coerceIn(0f, 1f)
        if (q <= 0f) continue
        val angle = noise(k, seed) * 2f * PI.toFloat()
        val out = from + Offset(cos(angle), sin(angle)) * ((30f + noise(k, seed + 7) * 50f) * u * scatter)
        val pos = if (q < 0.3f) {
            val e = 1f - (1f - q / 0.3f) * (1f - q / 0.3f)
            from + (out - from) * e
        } else {
            val t = (q - 0.3f) / 0.7f
            out + (to - out) * (t * t)
        }
        val arriving = ((q - 0.85f) / 0.15f).coerceIn(0f, 1f)
        drawCoin(pos, (5f + noise(k, seed + 3) * 3f) * u * (1f - 0.4f * arriving), q * 2.5f + k * 0.3f, 1f - arriving)
    }
    // The balance (or what was bought) blinks as the coins land.
    if (p > 0.6f) drawCircle(CoinGold.copy(alpha = 0.35f * (1f - abs(p - 0.8f) / 0.2f).coerceIn(0f, 1f)), radius = 22f * u, center = to)
}

/** Coins thrown up from [origin] in a fountain, spinning and fading at the end: a claim with no balance on screen. */
private fun DrawScope.drawFountain(origin: Offset, seed: Int, p: Float) {
    val u = 1.dp.toPx()
    if (p < 0.3f) drawCircle(Color.White.copy(alpha = 0.5f * (1f - p / 0.3f)), radius = (20f + 90f * p) * u, center = origin)
    for (k in 0 until 12) {
        val angle = (-PI / 2 + (noise(k, seed) - 0.5f) * 2.2f).toFloat()
        val speed = (150f + noise(k, seed + 7) * 150f) * u
        val t = p * 1.1f
        // Up, then pulled back down.
        val pos = Offset(origin.x + cos(angle) * speed * t, origin.y + sin(angle) * speed * t + 330f * u * t * t)
        drawCoin(pos, (5f + noise(k, seed + 3) * 3f) * u, p * 1.4f + k * 0.16f, if (p > 0.7f) (1f - p) / 0.3f else 1f)
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
