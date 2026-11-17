package com.deepline.fishing.ui.village

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.deepline.fishing.Building
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.VillageEventRail
import com.deepline.fishing.ui.VillageTopBar
import com.deepline.fishing.ui.kit.GameColors
import com.deepline.fishing.ui.kit.KitIcons
import com.deepline.fishing.ui.kit.deepColor
import com.deepline.fishing.ui.theme.LocalMenuPalette
import com.deepline.fishing.ui.tour.TourTarget
import com.deepline.fishing.ui.tour.tourTarget

/** The pictogram on a building's plate: the same as its menu's everywhere else. */
val Building.icon: ImageVector
    get() = when (this) {
        Building.FISHING -> GameIcons.Fish
        Building.DEX -> GameIcons.Dex
        Building.SHOP -> GameIcons.Shop
        Building.DUEL -> GameIcons.Battle
        Building.ROPE -> KitIcons.Rope
        Building.AQUARIUM -> KitIcons.Tank
    }

/**
 * The village, the home screen: a cove seen from above, where each building opens a menu. Drag to
 * move around, pinch to zoom (within the village's grounds); tap a building or its name plate.
 * The plates carry the menu's pictogram and name, so every building says what it's for. Around
 * the map: the points, milestones, themes and settings at the top, and what's on now on the left.
 */
@Composable
fun VillageScreen(vm: GameViewModel) {
    val density = LocalDensity.current
    val camera = vm.villageCamera
    // Bumped by every gesture: the map and the plates read it, so a drag redraws and re-places
    // them without recomposing the screen.
    var moved by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // The HUD around the map: the six buildings are framed inside it the first time.
    // The six fill the width; the plates keep clear of the left rail of buttons (see BuildingPlate).
    val insets = with(density) { floatArrayOf(20.dp.toPx(), 150.dp.toPx(), 20.dp.toPx(), 40.dp.toPx()) }
    val railRight = with(density) { 88.dp.toPx() }
    if (size.width > 0 && vm.villageFramedFor != size.width to size.height) {
        camera.fit(size.width.toFloat(), size.height.toFloat(), insets)
        vm.villageFramedFor = size.width to size.height
    }

    // The map covers the whole window: once it has painted, the menus' scene behind it isn't
    // drawn for nothing.
    val cover = com.deepline.fishing.ui.theme.LocalBackdropCover.current
    val settled = com.deepline.fishing.ui.theme.LocalScreenSettled.current
    var painted by remember { mutableStateOf(false) }
    DisposableEffect(cover, settled && painted) {
        cover?.covered = settled && painted
        onDispose { cover?.covered = false }
    }
    // While a finger moves the map, the 3D view draws every frame; at rest, every other one.
    val lastMove = remember { longArrayOf(0L) }

    fun open(b: Building, at: Offset?) {
        val origin = at?.let { (it.x / size.width.coerceAtLeast(1)) to (it.y / size.height.coerceAtLeast(1)) }
        vm.openBuilding(b, origin)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .testTag("village"),
    ) {
        VillageMap(
            vm,
            camera,
            { moved },
            onPainted = { painted = it },
            pace = { if (System.nanoTime() - lastMove[0] < 900_000_000L) 1 else 2 },
            modifier = Modifier
                .fillMaxSize()
                .tourTarget(TourTarget.VILLAGE_MAP)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        camera.drag(pan.x, pan.y, size.height.toFloat())
                        camera.zoom(zoom)
                        lastMove[0] = System.nanoTime()
                        moved++
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        camera.pick(p.x, p.y, size.width.toFloat(), size.height.toFloat())?.let { open(it, p) }
                    }
                },
        )
        if (size.width > 0) {
            for (b in Building.entries) {
                BuildingPlate(
                    b,
                    badge = b == Building.SHOP && (vm.clubPouchReady || vm.starterOfferOpen || vm.seasonRewardsReady),
                    place = {
                        moved
                        camera.toScreen(VillageLayout.spots.getValue(b).plate, size.width.toFloat(), size.height.toFloat())
                    },
                    minLeft = railRight,
                    onClick = { at -> open(b, at) },
                )
            }
        }
        VillageTopBar(vm, Modifier.align(Alignment.TopCenter))
        VillageEventRail(vm, Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
    }
}

/**
 * The map itself: the 3D village where the phone can draw it, otherwise the same village as a
 * flat drawing (the fallback, and what the UI tests see). [moved] changes with every gesture.
 */
@Composable
private fun VillageMap(
    vm: GameViewModel,
    camera: VillageCamera,
    moved: () -> Int,
    onPainted: (Boolean) -> Unit,
    pace: () -> Int,
    modifier: Modifier,
) {
    val palette = LocalMenuPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val supported = remember { com.deepline.fishing.ui.render3d.gl.Render3d.isSupported(context) } && !com.deepline.fishing.ui.render3d.gl.OffscreenGl.failed
    if (supported) {
        // What the render thread draws: the latest palette, skins and tank, read every frame.
        val looks = remember { arrayOfNulls<VillageScene.Looks>(1) }
        looks[0] = VillageScene.Looks(palette, vm.skins, com.deepline.fishing.core.Showcase.tankFish(vm.progress))
        val source = remember {
            val scene = VillageScene()
            com.deepline.fishing.ui.render3d.gl.FrameSource { w, h, t -> scene.build(w, h, t, camera, looks[0]!!) }
        }
        androidx.compose.foundation.layout.Box(modifier) {
            com.deepline.fishing.ui.render3d.gl.LivePreview(
                source,
                transparent = false,
                modifier = Modifier.fillMaxSize(),
                key = "village",
                onPainted = onPainted,
                maxRenderWidth = 720,
                pace = pace,
            )
        }
        return
    }
    androidx.compose.runtime.SideEffect { onPainted(true) }
    val time by rememberInfiniteTransition(label = "village").animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing)),
        label = "village time",
    )
    Canvas(modifier) {
        moved()
        drawVillageMap(camera, palette, time)
    }
}

/**
 * A building's name plate, floating above it: the menu's pictogram and name on deep water, a red
 * dot when something there is waiting. It follows the building as the map moves ([place]: where,
 * or null when off screen). Tapping it opens the building, like tapping the building.
 */
@Composable
private fun BuildingPlate(b: Building, badge: Boolean, place: () -> Pair<Float, Float>?, minLeft: Float, onClick: (Offset?) -> Unit) {
    val name = stringResource(b.labelRes)
    var anchor by remember { mutableStateOf<Offset?>(null) }
    Layout(
        content = {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(deepColor().copy(alpha = 0.88f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClickLabel = name) { onClick(anchor) }
                    .testTag("village_${b.name.lowercase()}")
                    .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(b.icon, contentDescription = null, tint = GameColors.OnDeep, modifier = Modifier.size(20.dp))
                Text(name, color = GameColors.OnDeep, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                if (badge) Box(Modifier.size(9.dp).background(GameColors.Badge, CircleShape))
            }
        },
        // As big as the map, so the plate can sit anywhere on it (it only catches touches on itself).
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val at = place()
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (at != null) {
                anchor = Offset(at.first, at.second)
                // Centred over the building, its bottom at the plate's anchor point; never under
                // the left rail of buttons, never off the right edge.
                val left = (at.first - placeable.width / 2f).coerceAtMost(constraints.maxWidth - placeable.width - 8f).coerceAtLeast(minLeft)
                placeable.place(IntOffset(left.toInt(), (at.second - placeable.height).toInt()))
            }
        }
    }
}
