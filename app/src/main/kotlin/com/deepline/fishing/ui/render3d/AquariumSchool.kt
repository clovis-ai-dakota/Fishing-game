package com.deepline.fishing.ui.render3d

import com.deepline.fishing.core.FishSpecies
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The aquarium's fish living together, a little like real ones (Reynolds' boids, plus the usual
 * extensions games add for fish): small fish school (they keep their distance, swim the same
 * way and stay together), the big ones cruise alone and now and then chase a small one, which
 * scatters the school until it regroups; two fish that meet stop nose to nose for a moment; a fish
 * goes to nibble at a piece of decor; a small fish tags along behind a big one. Looks only: it
 * runs on the render thread, frame by frame, and nothing in it is saved.
 */
class AquariumSchool(seed: Int = 7) {
    private val rnd = Random(seed)

    enum class Mood { CRUISE, SCHOOL, CHASE, FLEE, MEET, NIBBLE, FOLLOW }

    /**
     * What a fish does when the player double-taps it, in turn: darts off, rolls over, puffs up,
     * loops the loop, or stops to blow bubbles at the glass.
     */
    enum class Reaction(val seconds: Float) { DART(0.9f), SPIN(0.8f), PUFF(1.6f), LOOP(1.1f), BUBBLES(1.8f) }

    class Swimmer(val species: FishSpecies, val len: Float, val big: Boolean) {
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
        var mood = Mood.CRUISE
        /** Seconds left in the current mood, and before the next social moment. */
        var moodLeft = 0f
        var cooldown = 0f
        /** Who it chases, flees, meets or follows; where it nibbles or wanders to. */
        var other: Swimmer? = null
        var tx = 0f; var ty = 0f; var tz = 0f
        /** The tail's beat, faster when swimming fast. */
        var phase = 0f
        var yaw = SceneBuilder.FACING_RIGHT
        val speed: Float get() = sqrt(vx * vx + vy * vy + vz * vz)

        /** The reaction under way (see [react]) and how far into it (0..1). */
        var reaction: Reaction? = null
            internal set
        var reactionT = 0f
            internal set

        private fun ease(u: Float) = u * u * (3f - 2f * u)

        /** Rolled over its back (SPIN), in radians. */
        val roll: Float get() = if (reaction == Reaction.SPIN) 2f * PI.toFloat() * ease(reactionT) else 0f

        /** Puffed up (PUFF): how much bigger. */
        val puff: Float get() = if (reaction == Reaction.PUFF) 1f + 0.5f * kotlin.math.sin(PI.toFloat() * min(1f, reactionT * 1.15f)).coerceAtLeast(0f) else 1f

        /** Looping the loop (LOOP): the extra tilt. */
        val loop: Float get() = if (reaction == Reaction.LOOP) 2f * PI.toFloat() * ease(reactionT) * (if (kotlin.math.cos(yaw) >= 0f) 1f else -1f) else 0f

        /** The body's tilt (rotZ) to match climbing or diving, whichever way it faces. */
        val pitch: Float get() {
            val h = sqrt(vx * vx + vz * vz)
            val p = atan2(vy, max(h, 0.05f)).coerceIn(-0.45f, 0.45f)
            return if (kotlin.math.cos(yaw) >= 0f) p else -p
        }
    }

    val swimmers = ArrayList<Swimmer>()
    private var nextReaction = 0

    /** [s] was double-tapped: it reacts (each tap the next of the five reactions). */
    fun react(s: Swimmer) {
        val r = Reaction.entries[nextReaction++ % Reaction.entries.size]
        s.reaction = r
        s.reactionT = 0f
        s.other = null
        s.mood = if (s.big) Mood.CRUISE else Mood.SCHOOL
        s.cooldown = max(s.cooldown, 3f)
        when (r) {
            // A startled dash, back the way it came and away from the glass.
            Reaction.DART -> { val dir = if (s.vx >= 0f) -1f else 1f; s.vx = dir * 2.1f; s.vz = -0.9f; s.vy = 0.3f }
            Reaction.LOOP -> { s.vy = 0f }
            else -> Unit
        }
    }

    /** The fish nearest the front whose body is around ([x], [y]) on the plane at its depth (see [Camera.unproject]). */
    fun fishAt(hit: (Swimmer) -> Boolean): Swimmer? = swimmers.sortedByDescending { it.z }.firstOrNull(hit)
    private var width = 8f
    private var spots: List<Vec3> = emptyList()

    /**
     * Keeps the school in step with the tank: [fish] (new ones come in from a side, gone ones
     * leave), the tank's [tankWidth] and the [decor] tops a fish can nibble at.
     */
    fun sync(fish: List<FishSpecies>, tankWidth: Float, decor: List<Vec3>) {
        width = tankWidth
        spots = decor
        swimmers.removeAll { s -> fish.none { it.id == s.species.id } }
        val lens = fish.map { lengthOf(it) }
        val small = lens.minOrNull() ?: 1f
        for ((i, f) in fish.withIndex()) {
            if (swimmers.any { it.species.id == f.id }) continue
            val len = lens[i]
            // The big ones: clearly bigger than the smallest fish in the tank.
            val s = Swimmer(f, len, big = len >= small * 1.25f && fish.size > 1)
            s.x = (rnd.nextFloat() - 0.5f) * (width - 2f)
            s.y = 1.0f + rnd.nextFloat() * (AquariumScene.HEIGHT - 2f)
            s.z = (rnd.nextFloat() - 0.5f) * (AquariumScene.DEPTH - 1.2f)
            s.vx = if (rnd.nextBoolean()) 0.5f else -0.5f
            s.cooldown = 2f + rnd.nextFloat() * 6f
            s.phase = rnd.nextFloat() * 10f
            pickWander(s)
            swimmers += s
        }
    }

    /** Moves everyone on by [dt] seconds (in small steps). */
    fun step(dt: Float) {
        var left = dt.coerceIn(0f, 0.25f)
        while (left > 1e-4f) {
            val h = min(left, 1f / 30f)
            tick(h)
            left -= h
        }
    }

    private fun tick(dt: Float) {
        for (s in swimmers) moods(s, dt)
        for (s in swimmers) steer(s, dt)
        for (s in swimmers) s.reaction?.let { r ->
            s.reactionT += dt / r.seconds
            when (r) {
                // Still for a moment: puffed up, or blowing bubbles at the glass.
                Reaction.PUFF, Reaction.BUBBLES -> { s.vx *= 0.85f; s.vy *= 0.85f; s.vz *= 0.85f }
                Reaction.LOOP -> { s.vy = kotlin.math.sin(2f * PI.toFloat() * s.reactionT) * 1.6f }
                else -> Unit
            }
            if (s.reactionT >= 1f) { s.reaction = null; s.reactionT = 0f }
        }
        for (s in swimmers) {
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.z += s.vz * dt
            // Never through the glass, the sand or the surface.
            val wx = width / 2f - 0.5f
            s.x = s.x.coerceIn(-wx, wx)
            s.y = s.y.coerceIn(MIN_Y, AquariumScene.HEIGHT - 0.35f)
            s.z = s.z.coerceIn(-AquariumScene.DEPTH / 2f + 0.45f, AquariumScene.DEPTH / 2f - 0.45f)
            s.phase += dt * (3f + 5f * s.speed)
            // Face where it's going, turning at most so fast (through the front, like in a cast).
            if (s.vx * s.vx + s.vz * s.vz > 0.0025f || s.mood == Mood.MEET || s.reaction == Reaction.BUBBLES) {
                val want = when {
                    // Blowing bubbles: nose to the glass, towards the player.
                    s.reaction == Reaction.BUBBLES -> -PI.toFloat() / 2f
                    s.mood == Mood.MEET -> s.other?.let { o -> atan2(-(o.z - s.z), o.x - s.x) } ?: s.yaw
                    else -> atan2(-s.vz, s.vx)
                }
                var d = want - s.yaw
                while (d > PI) d -= 2f * PI.toFloat()
                while (d < -PI) d += 2f * PI.toFloat()
                s.yaw += d.coerceIn(-SceneBuilder.TURN_SPEED * 0.5f * dt, SceneBuilder.TURN_SPEED * 0.5f * dt)
            }
        }
    }

    /** Starts and ends the social moments. */
    private fun moods(s: Swimmer, dt: Float) {
        s.moodLeft -= dt
        s.cooldown -= dt
        if (s.mood != Mood.CRUISE && s.mood != Mood.SCHOOL && s.moodLeft <= 0f) {
            s.mood = if (s.big) Mood.CRUISE else Mood.SCHOOL
            s.other = null
            s.cooldown = 4f + rnd.nextFloat() * 6f
            pickWander(s)
        }
        if (s.mood == Mood.CRUISE || s.mood == Mood.SCHOOL) {
            if (!s.big) s.mood = Mood.SCHOOL
            if (s.cooldown <= 0f) startSomething(s)
            // Reached its wander point: another one.
            if (dist(s, s.tx, s.ty, s.tz) < 0.6f) pickWander(s)
        }
        // A small fish near a hunting big one flees, whatever it was doing.
        if (!s.big) {
            val hunter = swimmers.firstOrNull { it.big && it.mood == Mood.CHASE && dist(s, it.x, it.y, it.z) < FLEE_RADIUS }
            if (hunter != null && s.mood != Mood.FLEE) {
                s.mood = Mood.FLEE
                s.other = hunter
                s.moodLeft = 1.6f
            }
        }
    }

    private fun startSomething(s: Swimmer) {
        val roll = rnd.nextFloat()
        val small = swimmers.filter { !it.big && it !== s }
        val near = swimmers.filter { it !== s && it.mood.idle() && dist(s, it.x, it.y, it.z) < 2.5f }
        when {
            // A big fish goes after a small one.
            s.big && roll < 0.55f && small.isNotEmpty() -> {
                s.mood = Mood.CHASE
                s.other = small[rnd.nextInt(small.size)]
                s.moodLeft = 2.5f + rnd.nextFloat() * 1.5f
            }
            // Two fish meet nose to nose.
            roll < 0.35f && near.isNotEmpty() -> {
                val o = near[rnd.nextInt(near.size)]
                for ((a, b) in listOf(s to o, o to s)) {
                    a.mood = Mood.MEET
                    a.other = b
                    a.moodLeft = 2.2f
                }
            }
            // A small fish tags along behind a big one.
            !s.big && roll < 0.55f && swimmers.any { it.big } -> {
                val leaders = swimmers.filter { it.big }
                s.mood = Mood.FOLLOW
                s.other = leaders[rnd.nextInt(leaders.size)]
                s.moodLeft = 4f + rnd.nextFloat() * 3f
            }
            // Off to nibble at the decor.
            spots.isNotEmpty() && roll < 0.85f -> {
                val p = spots[rnd.nextInt(spots.size)]
                s.mood = Mood.NIBBLE
                s.tx = p.x + (rnd.nextFloat() - 0.5f) * 0.4f
                s.ty = (p.y + 0.25f).coerceIn(MIN_Y, AquariumScene.HEIGHT - 0.5f)
                s.tz = p.z + 0.45f
                s.moodLeft = 5f
            }
            else -> s.cooldown = 2f + rnd.nextFloat() * 3f
        }
    }

    private fun Mood.idle() = this == Mood.CRUISE || this == Mood.SCHOOL

    /** The forces of the moment, turned into a new velocity. */
    private fun steer(s: Swimmer, dt: Float) {
        var ax = 0f; var ay = 0f; var az = 0f
        var cruise = if (s.big) 0.45f else 0.7f
        fun toward(x: Float, y: Float, z: Float, w: Float) {
            val dx = x - s.x; val dy = y - s.y; val dz = z - s.z
            val d = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-3f)
            ax += dx / d * w; ay += dy / d * w; az += dz / d * w
        }
        when (s.mood) {
            Mood.CRUISE -> toward(s.tx, s.ty, s.tz, 0.6f)
            Mood.SCHOOL -> {
                toward(s.tx, s.ty, s.tz, 0.35f)
                // Cohesion and alignment with the other small fish nearby.
                var n = 0; var cx = 0f; var cy = 0f; var cz = 0f; var hx = 0f; var hy = 0f; var hz = 0f
                for (o in swimmers) if (o !== s && !o.big && dist(s, o.x, o.y, o.z) < SCHOOL_RADIUS) {
                    n++; cx += o.x; cy += o.y; cz += o.z; hx += o.vx; hy += o.vy; hz += o.vz
                }
                if (n > 0) {
                    toward(cx / n, cy / n, cz / n, 0.5f)
                    ax += (hx / n - s.vx) * 0.8f; ay += (hy / n - s.vy) * 0.8f; az += (hz / n - s.vz) * 0.8f
                }
            }
            Mood.CHASE -> s.other?.let { toward(it.x, it.y, it.z, 1.6f); cruise = 1.25f }
            Mood.FLEE -> s.other?.let { h ->
                toward(h.x, h.y, h.z, -2.6f)
                cruise = 1.6f
            }
            Mood.MEET -> s.other?.let { o ->
                // Up to each other, then still, nose to nose.
                val d = dist(s, o.x, o.y, o.z)
                if (d > 0.5f) toward(o.x, o.y, o.z, 0.9f)
                cruise = if (d > 0.5f) 0.45f else 0.03f
            }
            Mood.FOLLOW -> s.other?.let { l ->
                // Behind the leader, a body length back.
                val back = if (l.vx >= 0f) -1f else 1f
                toward(l.x + back * (l.len + 0.3f), l.y + 0.15f, l.z, 1.1f)
                cruise = max(0.5f, l.speed * 1.05f)
            }
            Mood.NIBBLE -> {
                val d = dist(s, s.tx, s.ty, s.tz)
                toward(s.tx, s.ty, s.tz, 0.9f)
                cruise = if (d > 0.35f) 0.55f else 0.05f
            }
        }
        if (s.reaction == Reaction.DART) cruise = 2.1f
        // Everyone keeps a little room (separation), except a pair meeting.
        for (o in swimmers) if (o !== s && !(s.mood == Mood.MEET && o === s.other)) {
            val d = dist(s, o.x, o.y, o.z)
            val room = (s.len + o.len) * 0.55f + 0.25f
            if (d < room) toward(o.x, o.y, o.z, -1.4f * (1f - d / room))
        }
        // The glass, the sand and the surface push back before they're reached.
        val wx = width / 2f - 1.2f
        if (s.x > wx) ax -= (s.x - wx) * 2f
        if (s.x < -wx) ax += (-wx - s.x) * 2f
        if (s.y < MIN_Y + 0.5f) ay += (MIN_Y + 0.5f - s.y) * 2f
        if (s.y > AquariumScene.HEIGHT - 0.9f) ay -= (s.y - AquariumScene.HEIGHT + 0.9f) * 2f
        val wz = AquariumScene.DEPTH / 2f - 0.8f
        if (s.z > wz) az -= (s.z - wz) * 2f
        if (s.z < -wz) az += (-wz - s.z) * 2f
        // Fish swim mostly level: up and down is slower.
        ay *= 0.6f
        s.vx += ax * dt * 2f
        s.vy += ay * dt * 2f
        s.vz += az * dt * 2f
        // Ease towards the mood's cruising speed.
        val sp = s.speed
        if (sp > 1e-3f) {
            val k = (1f + (cruise / sp - 1f) * min(1f, dt * 1.5f))
            s.vx *= k; s.vy *= k; s.vz *= k
        }
        // Never faster than a dart.
        val now = s.speed
        if (now > MAX_SPEED) {
            val k = MAX_SPEED / now
            s.vx *= k; s.vy *= k; s.vz *= k
        }
    }

    private fun pickWander(s: Swimmer) {
        s.tx = (rnd.nextFloat() - 0.5f) * (width - 2.4f)
        s.ty = MIN_Y + 0.4f + rnd.nextFloat() * (AquariumScene.HEIGHT - MIN_Y - 1.2f)
        s.tz = (rnd.nextFloat() - 0.5f) * (AquariumScene.DEPTH - 1.6f)
    }

    private fun dist(s: Swimmer, x: Float, y: Float, z: Float): Float {
        val dx = x - s.x; val dy = y - s.y; val dz = z - s.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        /** No fish swims lower than this (m above the tank's floor): the decor stands below. */
        const val MIN_Y = 0.75f
        private const val SCHOOL_RADIUS = 2.6f
        private const val FLEE_RADIUS = 1.8f
        private const val MAX_SPEED = 2.2f

        /** How long a fish is drawn in the tank (m): bigger species are bigger, within limits. */
        fun lengthOf(s: FishSpecies) = (0.55f + 0.18f * kotlin.math.ln(1f + s.bodyLengthM)).coerceIn(0.5f, 1.1f)
    }
}
