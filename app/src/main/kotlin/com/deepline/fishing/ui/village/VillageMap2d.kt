package com.deepline.fishing.ui.village

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.deepline.fishing.Building
import com.deepline.fishing.ui.render3d.Vec3
import com.deepline.fishing.ui.theme.MenuPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The village as a flat drawing through the same camera as the 3D view: the fallback where 3D
 * isn't available, and what the UI tests see. The sea, the island and its paths, then each
 * place (the lighthouse, halls as painted boxes, the pier and the arena), far ones first.
 */
fun DrawScope.drawVillageMap(camera: VillageCamera, p: MenuPalette, t: Float) {
    val w = size.width
    val h = size.height
    if (w < 2f || h < 2f) return
    val vp = camera.viewProj(w, h)
    fun at(x: Float, y: Float, z: Float): Offset? = camera.toScreen(Vec3(x, y, z), w, h, vp)?.let { Offset(it.first, it.second) }
    fun poly(points: List<Vec3>): Path? {
        val path = Path()
        for ((i, q) in points.withIndex()) {
            val s = at(q.x, q.y, q.z) ?: return null
            if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
        }
        path.close()
        return path
    }
    val night = p.night
    // The sea everywhere, darker further out.
    val sea = p.sea
    drawRect(Brush.verticalGradient(listOf(sea.last(), sea[1], sea.first())))
    // Gentle swell lines on the water.
    for (k in 0 until 24) {
        val z = -70f + k * 5f
        val path = Path()
        var started = false
        var x = -70f
        while (x <= 70f) {
            val s = at(x, 0f, z + sin(x / 7f + t * 0.6f + k) * 0.6f)
            if (s != null) { if (!started) { path.moveTo(s.x, s.y); started = true } else path.lineTo(s.x, s.y) }
            x += 4f
        }
        drawPath(path, Color.White.copy(alpha = if (night) 0.07f else 0.16f), style = Stroke(width = 1.5f))
    }
    // The island: shallows, a beach all round, the grass, and the lighthouse's rocks.
    fun ring(inset: Float, y: Float, wobble: Float = 0f): List<Vec3> = (0 until 72).map { k ->
        val a = k / 72f * 2f * PI.toFloat()
        val r = VillageLayout.islandRadius(a) - inset + wobble * sin(t * 1.4f + k)
        Vec3(VillageLayout.ISLAND_X + cos(a) * r, y, VillageLayout.ISLAND_Z + sin(a) * r)
    }
    val shallows = if (night) Color(0xFF1F5E73) else Color(0xFF5ED3D8)
    val sand = if (night) Color(0xFF8C7B5E) else Color(0xFFF1DEAD)
    val grass = if (night) Color(0xFF2E4A3A) else Color(0xFF86C862)
    poly(ring(-6f, 0f))?.let { drawPath(it, shallows.copy(alpha = 0.8f)) }
    poly(ring(-0.4f, 0f, 0.3f))?.let { drawPath(it, Color.White.copy(alpha = if (night) 0.35f else 0.85f)) }
    poly(ring(0f, 0.2f))?.let { drawPath(it, sand) }
    poly(ring(3.5f, 0.6f))?.let { drawPath(it, grass) }
    val dex = VillageLayout.spots.getValue(Building.DEX).center
    poly((0 until 16).map { k -> val a = k / 16f * 2f * PI.toFloat(); Vec3(dex.x + cos(a) * 6f, 1.5f, dex.z + sin(a) * 6f) })
        ?.let { drawPath(it, if (night) Color(0xFF4F4A44) else Color(0xFFA9A196)) }
    // The paths from the square to each place.
    val road = if (night) Color(0xFF6E6352) else Color(0xFFE6D3A8)
    fun street(a: Vec3, b: Vec3, width: Float) {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val len = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
        val nx = -dz / len * width / 2f
        val nz = dx / len * width / 2f
        poly(listOf(Vec3(a.x + nx, 0.8f, a.z + nz), Vec3(b.x + nx, 0.8f, b.z + nz), Vec3(b.x - nx, 0.8f, b.z - nz), Vec3(a.x - nx, 0.8f, a.z - nz)))?.let { drawPath(it, road) }
    }
    val sq = VillageLayout.square
    poly((0 until 24).map { k -> val a = k / 24f * 2f * PI.toFloat(); Vec3(sq.x + cos(a) * VillageLayout.SQUARE_R, 0.8f, sq.z + sin(a) * VillageLayout.SQUARE_R) })?.let { drawPath(it, road) }
    street(sq, Vec3(dex.x, 0f, dex.z + 3.2f), 2.2f)
    street(sq, Vec3(-11f, 0f, -13.6f), 2.4f)
    street(sq, Vec3(11f, 0f, -12.6f), 2.4f)
    street(sq, Vec3(-6.2f, 0f, -1.4f), 2.2f)
    street(sq, Vec3(VillageLayout.pierFrom.x, 0f, VillageLayout.pierFrom.z + 0.5f), 2.6f)
    street(Vec3(-4f, 0f, -6.5f), Vec3(VillageLayout.jettyFrom.x, 0f, VillageLayout.jettyFrom.z + 0.5f), 2f)

    // The pier and the duelling platform's jetty, out over the water.
    val wood = if (night) Color(0xFF5D4632) else Color(0xFFA77B4F)
    val woodDark = lerp(wood, Color.Black, 0.3f)
    fun deck(from: Vec3, to: Vec3, halfWidth: Float) {
        val top = listOf(Vec3(from.x - halfWidth, from.y, from.z), Vec3(to.x - halfWidth, to.y, to.z), Vec3(to.x + halfWidth, to.y, to.z), Vec3(from.x + halfWidth, from.y, from.z))
        poly(top.map { Vec3(it.x, it.y - 0.6f, it.z + 0.4f) })?.let { drawPath(it, woodDark) }
        poly(top)?.let { drawPath(it, wood) }
    }
    deck(VillageLayout.pierFrom, VillageLayout.pierTo, 1.6f)
    deck(VillageLayout.jettyFrom, VillageLayout.jettyTo, 1.2f)

    // Buildings, far ones first, each in its own colours.
    for (b in Building.entries.sortedBy { VillageLayout.spots.getValue(it).center.z }) {
        when (b) {
            Building.FISHING -> drawBoat2d(::at, night, t)
            Building.DUEL -> drawArena2d(::at, night, t)
            Building.DEX -> drawLighthouse2d(::at, night, t)
            else -> drawHouse2d(b, ::at, night)
        }
    }
}

private val roofs = mapOf(
    Building.SHOP to Color(0xFFD9573B),
    Building.ROPE to Color(0xFF8A5A3B),
    Building.AQUARIUM to Color(0xFFBFE9F2),
)
private val walls = mapOf(
    Building.SHOP to Color(0xFFF6EBD3),
    Building.ROPE to Color(0xFFCFB28A),
    Building.AQUARIUM to Color(0xFF9ED8E6),
)

/** A building as a box with a pitched roof: front and side walls, then the roof. */
private fun DrawScope.drawHouse2d(b: Building, at: (Float, Float, Float) -> Offset?, night: Boolean) {
    val s = VillageLayout.spots.getValue(b)
    val c = s.center
    val hx = s.half.x
    val hz = s.half.z
    val wallTop = c.y + s.half.y * 0.35f
    val ridge = c.y + s.half.y
    val shade = if (night) 0.45f else 0f
    val wall = lerp(walls.getValue(b), Color(0xFF1B2733), shade)
    val roof = lerp(roofs.getValue(b), Color(0xFF1B2733), shade)
    fun quad(vararg q: Triple<Float, Float, Float>): Path? {
        val path = Path()
        for ((i, v) in q.withIndex()) {
            val o = at(v.first, v.second, v.third) ?: return null
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        path.close()
        return path
    }
    // Front wall (facing the sea), then the side facing the camera's middle.
    quad(Triple(c.x - hx, 0.3f, c.z + hz), Triple(c.x + hx, 0.3f, c.z + hz), Triple(c.x + hx, wallTop, c.z + hz), Triple(c.x - hx, wallTop, c.z + hz))?.let { drawPath(it, wall) }
    val sideX = if (c.x < 0f) c.x + hx else c.x - hx
    quad(Triple(sideX, 0.3f, c.z + hz), Triple(sideX, 0.3f, c.z - hz), Triple(sideX, wallTop, c.z - hz), Triple(sideX, wallTop, c.z + hz))?.let { drawPath(it, lerp(wall, Color.Black, 0.18f)) }
    // Windows along the front, lit at night.
    val glass = if (night) Color(0xFFFFD27A) else Color(0xFF6FA8C7)
    for (k in 0 until 3) {
        val x0 = c.x - hx + (k + 0.5f) * (2f * hx / 3f) - 0.8f
        quad(Triple(x0, wallTop * 0.35f, c.z + hz + 0.05f), Triple(x0 + 1.6f, wallTop * 0.35f, c.z + hz + 0.05f), Triple(x0 + 1.6f, wallTop * 0.75f, c.z + hz + 0.05f), Triple(x0, wallTop * 0.75f, c.z + hz + 0.05f))?.let { drawPath(it, glass) }
    }
    if (b == Building.AQUARIUM) {
        // A glass dome instead of a roof, with fish shapes behind it.
        val top = at(c.x, ridge, c.z) ?: return
        val left = at(c.x - hx, wallTop, c.z + hz) ?: return
        val right = at(c.x + hx, wallTop, c.z + hz) ?: return
        val dome = Path().apply {
            moveTo(left.x, left.y)
            quadraticTo(left.x, top.y, top.x, top.y)
            quadraticTo(right.x, top.y, right.x, right.y)
            close()
        }
        drawPath(dome, roof.copy(alpha = 0.85f))
        drawPath(dome, Color.White.copy(alpha = 0.6f), style = Stroke(width = 2f))
        return
    }
    // A pitched roof: the ridge runs east to west, overhanging a little.
    val o = 0.8f
    quad(Triple(c.x - hx - o, wallTop, c.z + hz + o), Triple(c.x + hx + o, wallTop, c.z + hz + o), Triple(c.x + hx + o, ridge, c.z), Triple(c.x - hx - o, ridge, c.z))?.let { drawPath(it, roof) }
    quad(Triple(c.x - hx - o, wallTop, c.z - hz - o), Triple(c.x + hx + o, wallTop, c.z - hz - o), Triple(c.x + hx + o, ridge, c.z), Triple(c.x - hx - o, ridge, c.z))?.let { drawPath(it, lerp(roof, Color.Black, 0.25f)) }
    // What makes each one recognisable.
    when (b) {
        Building.SHOP -> {
            // A striped awning over the stalls.
            for (k in 0 until 6) {
                val x0 = c.x - hx + k * (2f * hx / 6f)
                quad(Triple(x0, wallTop * 0.9f, c.z + hz + 2f), Triple(x0 + 2f * hx / 6f, wallTop * 0.9f, c.z + hz + 2f), Triple(x0 + 2f * hx / 6f, wallTop, c.z + hz), Triple(x0, wallTop, c.z + hz))
                    ?.let { drawPath(it, if (k % 2 == 0) Color(0xFFE53935) else Color.White) }
            }
        }
        Building.ROPE -> at(c.x + hx * 0.6f, 1.8f, c.z + hz + 2.2f)?.let { spool ->
            // The giant rope spool in front of the workshop.
            val r = (at(c.x + hx * 0.6f + 1.8f, 1.8f, c.z + hz + 2.2f)?.x ?: (spool.x + 20f)) - spool.x
            drawCircle(Color(0xFF8D6E63), radius = r, center = spool)
            drawCircle(Color(0xFFD7B98E), radius = r * 0.78f, center = spool)
            for (k in 0 until 4) drawCircle(Color(0xFFA1887F), radius = r * (0.7f - k * 0.13f), center = spool, style = Stroke(width = 2f))
        }
        else -> Unit
    }
}

/** Your boat moored at the end of the pier, its mast and a line in the water. */
private fun DrawScope.drawBoat2d(at: (Float, Float, Float) -> Offset?, night: Boolean, t: Float) {
    val c = VillageLayout.boatAt
    val bob = sin(t * 1.3f) * 0.08f
    val hull = Path()
    val pts = listOf(Triple(c.x - 1.2f, 0.9f + bob, c.z - 2.6f), Triple(c.x + 1.2f, 0.9f + bob, c.z - 2.6f), Triple(c.x + 1.1f, 0.9f + bob, c.z + 2.2f), Triple(c.x, 0.9f + bob, c.z + 3f), Triple(c.x - 1.1f, 0.9f + bob, c.z + 2.2f))
    for ((i, v) in pts.withIndex()) {
        val o = at(v.first, v.second, v.third) ?: return
        if (i == 0) hull.moveTo(o.x, o.y) else hull.lineTo(o.x, o.y)
    }
    hull.close()
    drawPath(hull, if (night) Color(0xFF1C4A5A) else Color(0xFF23708C))
    drawPath(hull, Color.White.copy(alpha = 0.8f), style = Stroke(width = 2f))
    val cabinA = at(c.x - 0.7f, 2.2f + bob, c.z - 1f)
    val cabinB = at(c.x + 0.7f, 0.9f + bob, c.z + 0.6f)
    if (cabinA != null && cabinB != null) drawRect(Color(0xFFF8F3EA), topLeft = Offset(minOf(cabinA.x, cabinB.x), minOf(cabinA.y, cabinB.y)), size = androidx.compose.ui.geometry.Size(kotlin.math.abs(cabinB.x - cabinA.x), kotlin.math.abs(cabinB.y - cabinA.y)))
    val mastFoot = at(c.x, 1f + bob, c.z + 1.4f)
    val mastTop = at(c.x, 6.5f + bob, c.z + 1.4f)
    if (mastFoot != null && mastTop != null) drawLine(Color(0xFF5D4037), mastFoot, mastTop, strokeWidth = 3f)
}

/** The duelling platform: a ring of planks on the water, two rival pennants. */
private fun DrawScope.drawArena2d(at: (Float, Float, Float) -> Offset?, night: Boolean, t: Float) {
    val s = VillageLayout.spots.getValue(Building.DUEL)
    val c = s.center
    val r = s.half.x
    val ring = Path()
    for (k in 0..32) {
        val a = k / 32f * 2f * PI.toFloat()
        val o = at(c.x + cos(a) * r, 0.9f, c.z + sin(a) * r) ?: return
        if (k == 0) ring.moveTo(o.x, o.y) else ring.lineTo(o.x, o.y)
    }
    ring.close()
    drawPath(ring, if (night) Color(0xFF5D4632) else Color(0xFFB98A57))
    drawPath(ring, Color(0xFF4E342E), style = Stroke(width = 3f))
    for ((k, flag) in listOf(Color(0xFF4FC3F7), Color(0xFFFF5A4A)).withIndex()) {
        val x = c.x + if (k == 0) -r * 0.6f else r * 0.6f
        val foot = at(x, 0.9f, c.z) ?: continue
        val top = at(x, 7f, c.z) ?: continue
        drawLine(Color(0xFF4E342E), foot, top, strokeWidth = 3f)
        val wave = sin(t * 3f + k) * 4f
        val tip = at(x + if (k == 0) 2.8f else -2.8f, 6.3f, c.z) ?: continue
        val low = at(x, 5.6f, c.z) ?: continue
        drawPath(Path().apply { moveTo(top.x, top.y); lineTo(tip.x, tip.y + wave); lineTo(low.x, low.y); close() }, flag)
    }
}

/** The lighthouse (the Dex): a tapering tower in red and white bands, the gallery, the lantern and its dome. */
private fun DrawScope.drawLighthouse2d(at: (Float, Float, Float) -> Offset?, night: Boolean, t: Float) {
    val c = VillageLayout.spots.getValue(Building.DEX).center
    val base = 3.6f
    val top = base + 12f
    fun r(y: Float) = 2.15f - 0.6f * (y - base) / 12f
    val bands = 5
    for (k in 0 until bands) {
        val lo = base + k * 12f / bands
        val hi = lo + 12f / bands
        val a = at(c.x - r(lo), lo, c.z) ?: return
        val b = at(c.x + r(lo), lo, c.z) ?: return
        val e = at(c.x + r(hi), hi, c.z) ?: return
        val d = at(c.x - r(hi), hi, c.z) ?: return
        val col = if (k % 2 == 0) Color(0xFFFFF9EE) else Color(0xFFD8473A)
        drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(e.x, e.y); lineTo(d.x, d.y); close() }, lerp(col, Color(0xFF1B2733), if (night) 0.4f else 0f))
    }
    val gl = at(c.x - 2.75f, top, c.z) ?: return
    val gr = at(c.x + 2.75f, top, c.z) ?: return
    drawLine(Color(0xFF2F3E46), gl, gr, strokeWidth = 6f)
    val lamp = at(c.x, top + 1.6f, c.z) ?: return
    val lr = (gr.x - gl.x) * 0.22f
    drawCircle(if (night) Color(0xFFFFE9A0) else Color(0xFFFFF1B0), radius = lr, center = lamp)
    if (night) drawCircle(Color(0xFFFFE9A0).copy(alpha = 0.25f + 0.1f * sin(t * 2f)), radius = lr * 2.6f, center = lamp)
    val dome = at(c.x, top + 3.6f, c.z) ?: return
    drawPath(Path().apply { moveTo(lamp.x - lr * 1.3f, lamp.y - lr); quadraticTo(lamp.x, dome.y - lr, lamp.x + lr * 1.3f, lamp.y - lr); close() }, Color(0xFFD8473A))
}
