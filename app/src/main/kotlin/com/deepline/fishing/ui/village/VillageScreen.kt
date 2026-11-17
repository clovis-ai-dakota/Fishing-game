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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.deepline.fishing.Building
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.ui.GameIcons
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
 * The village, the home screen: an island seen from above, where each building opens a menu. Drag
 * to move around, pinch to zoom (within the island's waters); tap a building. Nothing is written on
 * the island: each building's shape says what it is (the tour names them), a red dot shows where
 * something waits. Around the map: the points and the daily rewards on the left, milestones,
 * themes and settings on the right.
 */
@Composable
fun VillageScreen(vm: GameViewModel) {
    val density = LocalDensity.current
    val camera = vm.villageCamera
    // Bumped by every gesture: the map and the plates read it, so a drag redraws and re-places
    // them without recomposing the screen.
    var moved by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // The HUD around the map: the six buildings are framed inside it the first time (the top bar
    // and the daily button under the points stay over the sea).
    val insets = with(density) { floatArrayOf(20.dp.toPx(), 150.dp.toPx(), 20.dp.toPx(), 40.dp.toPx()) }
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
            // 60 fps under the finger; at rest only the sea, the gulls and the fish move, slowly
            // enough for 20 fps, which keeps the full-screen scene's GPU load near the Fish menu's.
            pace = { if (System.nanoTime() - lastMove[0] < 900_000_000L) 1 else 3 },
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
                val spot = VillageLayout.spots.getValue(b)
                BuildingSpot(
                    b,
                    badge = b == Building.SHOP && (vm.clubPouchReady || vm.starterOfferOpen || vm.seasonRewardsReady),
                    place = {
                        moved
                        camera.toScreen(spot.center, size.width.toFloat(), size.height.toFloat())
                    },
                    badgeAt = {
                        moved
                        camera.toScreen(spot.plate, size.width.toFloat(), size.height.toFloat())
                    },
                    onClick = { at -> open(b, at) },
                )
            }
        }
        VillageTopBar(vm, Modifier.align(Alignment.TopCenter))
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
        looks[0] = VillageScene.Looks(palette, vm.skins, com.deepline.fishing.core.Showcase.tankFish(vm.progress), IslandDress.of(vm.progress))
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
                // Sharp enough with 4x MSAA (the plates and HUD are drawn at full size over it),
                // and 40% fewer pixels to shade than 720: the island's baked light and water
                // cost their share.
                maxRenderWidth = 560,
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
 * What makes a building a button without drawing anything on the island: an invisible target over
 * the building (its name for screen readers and tests, `village_<building>`), and a small red dot
 * floating above it when something there is waiting. Both follow the map as it moves ([place] and
 * [badgeAt]: where on screen, or null when off it). Tapping the building itself works the same.
 */
@Composable
private fun BuildingSpot(
    b: Building,
    badge: Boolean,
    place: () -> Pair<Float, Float>?,
    badgeAt: () -> Pair<Float, Float>?,
    onClick: (Offset?) -> Unit,
) {
    val name = stringResource(b.labelRes)
    var anchor by remember { mutableStateOf<Offset?>(null) }
    Layout(
        content = {
            Box(
                Modifier
                    .size(56.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = name,
                    ) { onClick(anchor) }
                    .semantics { contentDescription = name }
                    .testTag("village_${b.name.lowercase()}"),
            )
            if (badge) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(GameColors.Badge, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .testTag("village_badge_${b.name.lowercase()}"),
                )
            }
        },
        // As big as the map, so the target can sit anywhere on it (it only catches touches on itself).
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val target = measurables[0].measure(loose)
        val dot = measurables.getOrNull(1)?.measure(loose)
        val at = place()
        val dotAt = badgeAt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (at != null) {
                anchor = Offset(at.first, at.second)
                target.place(IntOffset((at.first - target.width / 2f).toInt(), (at.second - target.height / 2f).toInt()))
            }
            if (dot != null && dotAt != null) {
                dot.place(IntOffset((dotAt.first - dot.width / 2f).toInt(), (dotAt.second - dot.height).toInt()))
            }
        }
    }
}
