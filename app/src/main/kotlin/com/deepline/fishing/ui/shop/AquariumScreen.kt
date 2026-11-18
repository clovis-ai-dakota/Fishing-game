package com.deepline.fishing.ui.shop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import com.deepline.fishing.ui.i18n.label
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.Grant
import com.deepline.fishing.core.ShopCatalog
import com.deepline.fishing.core.ShopItem
import com.deepline.fishing.core.ShopSection
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.badgeText
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.onClick
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.deepline.fishing.core.Decor
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.core.FishSpecies
import com.deepline.fishing.core.PlayerProgress
import com.deepline.fishing.core.Showcase
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.drawFish
import com.deepline.fishing.ui.waterColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The aquarium: caught fish swimming among the decor the player owns. Pick which species swim
 * here (the tank holds [Showcase.tankSize]); decor and a bigger tank are sold here, with the same
 * two prices as in the Shop. Looks only.
 */
@Composable
fun AquariumScreen(vm: GameViewModel) {
    val p = vm.progress
    val caught = FishCatalog.all.filter { p.isDiscovered(it) }
    val inTank = Showcase.tankFish(p).map { it.id }.toSet()
    var confirm by remember { mutableStateOf<ShopItem?>(null) }
    val actions = ShopActions(vm, LocalContext.current.findActivity(), onPoints = { confirm = it })
    val forSale = ShopCatalog.inSection(ShopSection.AQUARIUM)
    SubScreen(title = stringResource(R.string.shop_aquarium_title), onBack = vm::onBack) {
        // The tank takes half the screen; the lists fold away under it (one open at a time).
        var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
        fun toggle(key: String) { open = if (open == key) null else key }
        val tankHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).coerceAtLeast(300f).dp
        val owned = Decor.entries.filter { Showcase.ownsDecor(p, it) }
        val decorForSale = forSale.filter { (it.grants.single() as? Grant.Unlock)?.key?.startsWith("decor:") == true }
        val bigTank = forSale.filter { (it.grants.single() as? Grant.Unlock)?.key == "aquarium:big" }
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("aquarium_list"),
        ) {
            item(key = "tank") {
                // The live tank (3D where available): a double tap opens it full screen (screen
                // readers get it as the tank's action).
                val fullScreen = stringResource(R.string.shop_aquarium_fullscreen)
                // Framed like the full screen (most of the tank, on its stand), without its gestures.
                val cardView = remember { com.deepline.fishing.ui.render3d.AquariumScene.Camera(widthShare = 0.62f) }
                AquariumView(
                    p,
                    camera = cardView,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(tankHeight)
                        .tourTarget(TourTarget.AQUARIUM_TANK)
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { vm.openAquariumFull() }) }
                        .semantics { onClick(label = fullScreen) { vm.openAquariumFull(); true } }
                        .testTag("aquarium_tank"),
                )
            }
            item(key = "decor") {
                com.deepline.fishing.ui.kit.FoldingSection(
                    title = stringResource(R.string.shop_aquarium_decor),
                    icon = com.deepline.fishing.ui.kit.KitIcons.Tank,
                    choice = stringResource(R.string.shop_aquarium_fold_count, owned.count { it.name !in p.hiddenDecor }, owned.size),
                    expanded = open == "decor",
                    onToggle = { toggle("decor") },
                    modifier = Modifier.testTag("aquarium_decor_picker"),
                ) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (d in owned) {
                            FilterChip(
                                selected = d.name !in p.hiddenDecor,
                                onClick = { vm.toggleDecor(d) },
                                label = { Text(d.label(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("decor_${d.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
            item(key = "catches") {
                com.deepline.fishing.ui.kit.FoldingSection(
                    title = stringResource(R.string.shop_aquarium_catches),
                    icon = com.deepline.fishing.ui.GameIcons.Fish,
                    choice = stringResource(R.string.shop_aquarium_fold_count, inTank.size, Showcase.tankSize(p)),
                    expanded = open == "catches",
                    onToggle = { toggle("catches") },
                    modifier = Modifier.testTag("aquarium_catches_picker"),
                    info = stringResource(
                        if (caught.isEmpty()) R.string.shop_aquarium_count_empty else R.string.shop_aquarium_count,
                        inTank.size,
                        Showcase.tankSize(p),
                    ),
                ) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (s in caught) {
                            FilterChip(
                                selected = s.id in inTank,
                                onClick = { vm.toggleTankFish(s.id) },
                                label = { Text(s.label(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("tank_${s.id}"),
                            )
                        }
                    }
                }
            }
            item(key = "for_sale") {
                // What's for sale: the bigger tank, then each piece of decor in a tank of its own.
                val all = bigTank + decorForSale
                com.deepline.fishing.ui.kit.FoldingSection(
                    title = stringResource(R.string.shop_aquarium_for_sale),
                    icon = com.deepline.fishing.ui.GameIcons.Shop,
                    choice = stringResource(R.string.shop_aquarium_fold_count, all.count { ShopCatalog.owns(p, it.id) }, all.size),
                    expanded = open == "for_sale",
                    onToggle = { toggle("for_sale") },
                    modifier = Modifier.testTag("aquarium_shop_picker"),
                    info = stringResource(R.string.shop_aquarium_page_note),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (item in bigTank) ItemCard(vm, item, actions)
                        for (pair in decorForSale.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (item in pair) {
                                    val decor = Decor.entries.first { it.unlockKey == (item.grants.single() as Grant.Unlock).key }
                                    Box(Modifier.weight(1f)) {
                                        Tile(item.label(), item.badgeText()?.asString(), preview = { AquariumTank(p, height = 120.dp, extraDecor = decor) }) {
                                            if (ShopCatalog.owns(p, item.id)) OwnedRow() else PriceButtons(item, actions)
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    confirm?.let { item -> PointsConfirmDialog(vm, item, onDone = { confirm = null }) }
}

/**
 * The tank as it lives: in 3D where the phone can draw it ([com.deepline.fishing.ui.render3d.AquariumScene]),
 * otherwise the 2D [AquariumTank]. Fills [modifier]'s size, whatever its shape.
 */
@Composable
fun AquariumView(
    p: PlayerProgress,
    modifier: Modifier = Modifier,
    camera: com.deepline.fishing.ui.render3d.AquariumScene.Camera? = null,
    // About the card's own width on most phones: drawn at full sharpness, not stretched.
    maxRenderWidth: Int = 1080,
) {
    val context = LocalContext.current
    val supported = remember { com.deepline.fishing.ui.render3d.gl.Render3d.isSupported(context) } && !com.deepline.fishing.ui.render3d.gl.OffscreenGl.failed
    val fish = Showcase.tankFish(p)
    val description = pluralStringResource(R.plurals.shop_aquarium_cd_tank, fish.size, fish.size)
    if (!supported) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier) { AquariumTank(p, height = maxHeight) }
        return
    }
    // What the render thread draws: the latest fish and decor, where the player put it.
    val looks = remember { arrayOfNulls<Triple<List<FishSpecies>, List<com.deepline.fishing.ui.render3d.AquariumScene.Piece>, Boolean>>(1) }
    looks[0] = Triple(fish, piecesOf(p), Showcase.hasBigTank(p))
    val source = remember {
        val scene = com.deepline.fishing.ui.render3d.AquariumScene()
        com.deepline.fishing.ui.render3d.gl.FrameSource { w, h, t -> looks[0]!!.let { (f, d, big) -> scene.build(w, h, t, f, d, big, camera) } }
    }
    androidx.compose.foundation.layout.Box(modifier.semantics { contentDescription = description }) {
        com.deepline.fishing.ui.render3d.gl.LivePreview(
            source,
            transparent = false,
            modifier = Modifier.matchParentSize(),
            key = "aquarium",
            maxRenderWidth = maxRenderWidth,
        )
    }
}

/** The decor shown in the tank, each where the player put it. */
internal fun piecesOf(p: PlayerProgress) = Showcase.shownDecor(p).map { com.deepline.fishing.ui.render3d.AquariumScene.Piece(it, Showcase.decorSpot(p, it)) }

/**
 * The aquarium full screen, upright: the system bars hide while it shows. Drag to look around,
 * pinch to zoom; double-tap a fish and it reacts (five ways), double-tap the water to zoom in or
 * back out. Press and hold a piece of decor to pick it up, then drag it: let go in the water and
 * it sinks to the sand; it stays where it was put (saved), and new pieces never move it.
 */
@Composable
fun AquariumFullScreen(vm: GameViewModel) {
    val activity = LocalContext.current.findActivity()
    androidx.compose.runtime.DisposableEffect(activity) {
        val window = activity?.window
        val insets = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { insets?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    val camera = remember { com.deepline.fishing.ui.render3d.AquariumScene.Camera() }
    val pieces = androidx.compose.runtime.rememberUpdatedState(piecesOf(vm.progress))
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black).testTag("aquarium_full")) {
        AquariumView(
            vm.progress,
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hit = camera.pick(down.position.x, down.position.y, pieces.value)
                        // A piece of decor moves after a press held still for the system's long-press
                        // time (400 ms unless the player changed it in the accessibility settings):
                        // a plain drag, even starting on the decor, looks around the tank.
                        var held = false
                        if (hit != null) {
                            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val finger = event.changes.firstOrNull { it.id == down.id }
                                    if (finger == null || !finger.pressed) return@withTimeoutOrNull false
                                    if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull false
                                    if ((finger.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                                }
                                @Suppress("UNREACHABLE_CODE") false
                            }
                            held = outcome == null
                        }
                        if (held && hit != null) {
                            // Lifted: it follows the finger until it's let go, then sinks.
                            camera.grab(hit, down.position.x, down.position.y, pieces.value)
                            while (true) {
                                val event = awaitPointerEvent()
                                val finger = event.changes.firstOrNull { it.id == down.id }
                                if (finger == null || !finger.pressed || event.changes.count { it.pressed } > 1) break
                                if (finger.positionChanged()) {
                                    camera.move(finger.position.x, finger.position.y)
                                    finger.consume()
                                }
                            }
                            camera.release()?.let { (d, spot) -> vm.placeDecor(d, spot) }
                        } else {
                            // The water: look around and zoom.
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f) camera.zoomBy(zoom)
                                if (pan != androidx.compose.ui.geometry.Offset.Zero) camera.drag(pan.x, pan.y)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    }
                }
                // A double tap: on a fish it reacts, on the water the view zooms in or back out.
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { camera.doubleTap(it.x, it.y) }) },
            camera = camera,
            // Full screen: drawn at the phone's own width, not stretched from a small image.
            maxRenderWidth = 1080,
        )
        com.deepline.fishing.ui.kit.RoundIconButton(
            com.deepline.fishing.ui.kit.KitIcons.Close,
            stringResource(R.string.common_back),
            onClick = vm::onBack,
            modifier = Modifier.padding(16.dp).testTag("aquarium_full_close"),
            diameter = 40.dp,
        )
    }
}

/** The tank itself, animated (2D): the fallback for [AquariumView] and the decor previews. */
@Composable
fun AquariumTank(p: PlayerProgress, height: Dp, modifier: Modifier = Modifier, extraDecor: Decor? = null, big: Boolean = Showcase.hasBigTank(p)) {
    val t by rememberInfiniteTransition(label = "tank").animateFloat(
        initialValue = 0f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
        label = "tank time",
    )
    val fish = Showcase.tankFish(p)
    val decor = (Showcase.shownDecor(p) + listOfNotNull(extraDecor)).distinct()
    val description = pluralStringResource(R.plurals.shop_aquarium_cd_tank, fish.size, fish.size)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = description },
    ) { drawTank(fish, decor, t, big) }
}

private fun DrawScope.drawTank(fish: List<FishSpecies>, decor: List<Decor>, t: Float, big: Boolean) {
    val w = size.width
    val h = size.height
    // Sunlit water from the colour script, a sandy floor and the glass's frame.
    drawRect(Brush.verticalGradient(listOf(waterColor(5f), waterColor(if (big) 60f else 45f))))
    val floor = h * 0.84f
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE6CF9A), Color(0xFFC9A96E)), startY = floor, endY = h), topLeft = Offset(0f, floor), size = Size(w, h - floor))
    for (k in 0 until 5) {
        val x = w * (0.1f + 0.2f * k) + sin(t * 0.4f + k) * w * 0.02f
        val ray = Path().apply { moveTo(x - w * 0.03f, 0f); lineTo(x + w * 0.03f, 0f); lineTo(x + w * 0.1f, floor); lineTo(x - w * 0.02f, floor); close() }
        drawPath(ray, Color.White.copy(alpha = 0.06f))
    }
    for (d in decor) drawDecor(d, w, h, floor, t)
    // Fish swim back and forth at their own heights and speeds.
    val n = fish.size.coerceAtLeast(1)
    fish.forEachIndexed { i, s ->
        val lane = (i + 0.5f) / n
        val y = h * (0.12f + 0.62f * lane)
        val speed = 0.05f + 0.03f * ((i * 37) % 5)
        val phase = (t * speed + i * 0.37f) % 2f
        val forward = phase < 1f
        val u = if (forward) phase else 2f - phase
        val len = (h * 0.11f * (0.7f + 0.6f * ((s.bodyLengthM.coerceIn(0.2f, 3f)) / 3f))).coerceAtMost(w * 0.22f)
        val x = len + u * (w - 2f * len)
        drawFish(s, Offset(x, y + sin(t * 1.3f + i) * h * 0.02f), len, if (forward) 1 else -1, sparkling = false, timeSeconds = t, phase = i * 1.3f)
    }
    // Bubbles rising.
    for (k in 0 until 10) {
        val p = (t * 0.12f + k / 10f) % 1f
        val x = w * (0.08f + 0.09f * k) + sin(p * 8f + k) * 6f
        drawCircle(Color.White.copy(alpha = 0.5f * (1f - p)), radius = 3f + 4f * p, center = Offset(x, floor - p * floor), style = Stroke(width = 1.5f))
    }
    drawRect(Color.White.copy(alpha = 0.25f), style = Stroke(width = 6f))
}

private fun DrawScope.drawDecor(d: Decor, w: Float, h: Float, floor: Float, t: Float) {
    when (d) {
        Decor.PLANTS -> for (k in 0 until 4) {
            val x = w * (0.06f + 0.29f * k)
            val tall = h * (0.25f + 0.08f * (k % 2))
            val sway = sin(t * 1.2f + k) * w * 0.015f
            val leaf = Path().apply {
                moveTo(x, floor)
                quadraticTo(x - w * 0.03f + sway, floor - tall * 0.5f, x + sway * 2f, floor - tall)
                quadraticTo(x + w * 0.03f + sway, floor - tall * 0.5f, x + w * 0.012f, floor)
                close()
            }
            drawPath(leaf, Color(0xFF43A047).copy(alpha = 0.9f))
        }
        Decor.PEBBLES -> for (k in 0 until 12) {
            val x = w * ((k * 0.083f + 0.03f) % 1f)
            drawOval(listOf(Color(0xFF9E9E9E), Color(0xFFBCAAA4), Color(0xFF8D6E63))[k % 3], topLeft = Offset(x, floor + h * 0.03f + (k % 3) * 3f), size = Size(w * 0.035f, h * 0.025f))
        }
        Decor.SAND_CASTLE -> {
            val x = w * 0.70f
            val bw = w * 0.14f
            val bh = h * 0.14f
            drawRect(Color(0xFFE0C07E), topLeft = Offset(x, floor - bh), size = Size(bw, bh))
            for (k in 0 until 3) drawRect(Color(0xFFD7B46A), topLeft = Offset(x + k * bw * 0.4f, floor - bh * 1.45f), size = Size(bw * 0.22f, bh * 0.45f))
            drawRect(Color(0xFF6D4C41), topLeft = Offset(x + bw * 0.4f, floor - bh * 0.45f), size = Size(bw * 0.2f, bh * 0.45f))
        }
        Decor.BUBBLER -> {
            val c = Offset(w * 0.18f, floor - h * 0.07f)
            drawCircle(Color(0xFFB0BEC5), radius = h * 0.06f, center = c)
            drawCircle(Color(0xFF4FC3F7), radius = h * 0.035f, center = c + Offset(h * 0.01f, -h * 0.005f))
            for (k in 0 until 6) {
                val p = (t * 0.5f + k / 6f) % 1f
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - p)), radius = 3f + 5f * p, center = c + Offset(sin(p * 9f) * 5f, -h * 0.07f - p * floor * 0.9f), style = Stroke(width = 1.5f))
            }
        }
        Decor.TREASURE_CHEST -> {
            val x = w * 0.42f
            val cw = w * 0.13f
            val ch = h * 0.08f
            val open = (0.5f + 0.5f * sin(t * 0.8f)).coerceIn(0f, 1f)
            drawRect(Color(0xFF6D4C41), topLeft = Offset(x, floor - ch), size = Size(cw, ch))
            drawRect(Color(0xFFFFD54F), topLeft = Offset(x + cw * 0.05f, floor - ch * 1.05f), size = Size(cw * 0.9f, ch * 0.2f * open))
            drawRect(Color(0xFF8D6E63), topLeft = Offset(x, floor - ch - ch * 0.5f * open - ch * 0.3f), size = Size(cw, ch * 0.3f))
            drawRect(Color(0xFFFFC107), topLeft = Offset(x + cw * 0.45f, floor - ch * 0.75f), size = Size(cw * 0.1f, ch * 0.25f))
        }
        Decor.CORAL_GARDEN -> for (k in 0 until 3) {
            val base = Offset(w * (0.52f + 0.1f * k), floor)
            val col = listOf(Color(0xFFFF8A80), Color(0xFFFFAB40), Color(0xFFF48FB1))[k]
            for (b in 0 until 4) {
                val a = -PI.toFloat() / 2f + (b - 1.5f) * 0.4f
                val end = base + Offset(cos(a), sin(a)) * (h * (0.1f + 0.03f * b))
                drawLine(col, base, end, strokeWidth = w * 0.012f, cap = StrokeCap.Round)
                drawCircle(col, radius = w * 0.01f, center = end)
            }
        }
        Decor.LANTERN_LIGHTS -> for (k in 0 until 7) {
            val x = w * (0.08f + 0.14f * k)
            val y = h * 0.05f + sin(k * 1.1f) * h * 0.015f
            val glow = 0.6f + 0.4f * sin(t * 2f + k)
            drawCircle(Color(0xFFFFE082).copy(alpha = 0.25f * glow), radius = h * 0.04f, center = Offset(x, y))
            drawCircle(Color(0xFFFFCA28), radius = h * 0.015f, center = Offset(x, y))
        }
        Decor.SUNKEN_SHIP -> {
            val x = w * 0.05f
            val hull = Path().apply {
                moveTo(x, floor - h * 0.12f)
                lineTo(x + w * 0.3f, floor - h * 0.17f)
                lineTo(x + w * 0.27f, floor)
                lineTo(x + w * 0.03f, floor)
                close()
            }
            drawPath(hull, Color(0xFF4E342E))
            drawLine(Color(0xFF3E2723), Offset(x + w * 0.15f, floor - h * 0.15f), Offset(x + w * 0.2f, floor - h * 0.42f), strokeWidth = w * 0.012f)
            for (k in 0 until 3) drawCircle(Color(0xFF1B0F0B), radius = h * 0.015f, center = Offset(x + w * (0.08f + 0.07f * k), floor - h * 0.08f))
        }
    }
}
