package com.deepline.fishing.ui

import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.secretTitle
import com.deepline.fishing.ui.i18n.label
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Notice
import com.deepline.fishing.core.CastPhase
import com.deepline.fishing.core.CastResult
import com.deepline.fishing.core.CastSession
import com.deepline.fishing.core.GameConfig
import com.deepline.fishing.ui.secrets.FishingSecrets
import com.deepline.fishing.ui.i18n.UiText
import com.deepline.fishing.ui.secrets.SecretDiscoveredDialog
import kotlin.math.roundToInt

/** Latest finger position, read by the game loop without causing recomposition. */
private class SteerInput {
    /** The finger being followed, so a second finger or a lift-and-retouch can't confuse it. */
    var pointerId: PointerId? = null
    var pointerX: Float? = null
    var widthPx: Float = 0f

    /** Where the finger is in world meters, or null when nobody is touching (or before layout). */
    fun targetX(): Float? {
        val x = pointerX ?: return null
        if (widthPx <= 1f) return null
        return x / widthPx * GameConfig.WORLD_WIDTH
    }
}

@Composable
fun CastScreen(vm: GameViewModel) {
    val session = vm.session ?: return
    val steer = remember(session) { SteerInput() }
    var time by remember(session) { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()
    val labels = rememberCastLabels()
    val skins = vm.skins
    // The 3D renderer where the phone supports it (and it hasn't failed), otherwise 2D.
    val context = androidx.compose.ui.platform.LocalContext.current
    // Store screenshots draw the 3D frame elsewhere: here only what goes on top of it.
    val overlayOnly = com.deepline.fishing.ui.render3d.gl.Render3d.overlayOnly
    val use3d = !overlayOnly && remember { com.deepline.fishing.ui.render3d.gl.Render3d.isSupported(context) } &&
        !com.deepline.fishing.ui.render3d.gl.Render3d.failed
    val scene3d = remember(session) { com.deepline.fishing.ui.render3d.gl.Scene3dHandle() }
    val sceneBuilder = remember(session) { com.deepline.fishing.ui.render3d.SceneBuilder() }
    if (use3d) {
        // Build this cast's fish meshes ahead of time, off the main thread.
        LaunchedEffect(session) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                session.world.fish.map { it.species }.distinct().forEach { com.deepline.fishing.ui.render3d.FishModels.mesh(it) }
            }
        }
    }

    // Leaving the cast screen (results collected, cast quit, battle turn over): menu music again.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.feel.onCastEnd() } }

    // The loop only runs while the cast is live: no frames are spent on the pause dialog or
    // the results panel. [time] keeps counting from where it stopped, and slows down with the
    // game during a (solo) slow-motion moment.
    val running = !vm.paused && vm.pendingResult == null && vm.secretPopup == null
    LaunchedEffect(session, running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    vm.onFrame(dt, steer.targetX())
                    time += dt * vm.feel.timeScale
                }
                last = now
                if (use3d) {
                    val fx = vm.feel.fx(session)
                    scene3d.render { w, h -> sceneBuilder.build(session, time, w, h, skins, fx = fx) }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (use3d) {
            com.deepline.fishing.ui.render3d.gl.Scene3d(scene3d, Modifier.fillMaxSize())
            // Show the paused scene too (the loop above only runs during play).
            LaunchedEffect(session, running) {
                if (!running) {
                    val fx = vm.feel.fx(session)
                    scene3d.render { w, h -> sceneBuilder.build(session, time, w, h, skins, fx = fx) }
                }
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { steer.widthPx = it.width.toFloat() }
                // Let steering drags near the screen edges steer instead of triggering the system
                // back gesture (Android honors this for part of each edge).
                .systemGestureExclusion()
                .pointerInput(session) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            // Follow one finger: keep the one we follow while it's down, otherwise
                            // pick up any finger that is down. When none is, the hook holds still.
                            val followed = event.changes.firstOrNull { it.id == steer.pointerId && it.pressed }
                                ?: event.changes.firstOrNull { it.pressed }
                            steer.pointerId = followed?.id
                            steer.pointerX = followed?.position?.x
                        }
                    }
                },
        ) {
            // Until the 3D view has drawn its first frame, the 2D scene covers it (no black flash).
            val detect = vm.detectorTargets
            // Shake, flash and particles (render only: steering and hits never move).
            val fx = vm.feel.fx(session)
            if (overlayOnly || (use3d && scene3d.ready)) drawCastOverlay(session, time, textMeasurer, aimX = steer.targetX(), detect = detect, fx = fx, labels = labels)
            else drawCastScene(session, time, textMeasurer, aimX = steer.targetX(), skins = skins, detect = detect, fx = fx, labels = labels)
        }

        val turn = vm.battleTurn
        val battleLabel = turn?.let { t ->
            val b = vm.battle
            val name = b?.players?.getOrNull(t.playerIndex)?.name ?: stringResource(R.string.cast_hud_player)
            val perPlayer = b?.mode?.castsPerPlayer
            when {
                t.round > 0 -> stringResource(R.string.cast_hud_battle_round, name, t.round)
                perPlayer != null -> stringResource(R.string.cast_hud_battle_cast_of, name, t.castNumber, perPlayer)
                else -> stringResource(R.string.cast_hud_battle_cast, name, t.castNumber)
            }
        } ?: vm.tournamentAttempt?.let { stringResource(R.string.cast_hud_tournament_attempt, it.attempt, GameConfig.TOURNAMENT_ATTEMPTS) }
        // Reading [time] subscribes this screen to every frame; the HUD then gets plain values
        // so it recomposes whenever one of them changes.
        @Suppress("UNUSED_VARIABLE")
        val frameTick = time
        val hud = HudState.of(session)
        CastHud(
            hud = hud,
            notice = vm.notice,
            battleLabel = battleLabel,
            timeLeft = vm.battleTimeLeft(),
            opponentLive = if (vm.isOnlineBattle) opponentLiveText(vm)?.asString() else null,
            onPause = vm::pause,
        )
        // All bumps used, or the line is full: offer to haul it straight up (thumb zone).
        if (hud.canFastReel && !vm.paused && vm.pendingResult == null) {
            Button(
                onClick = vm::fastReel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(bottom = 28.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(
                    stringResource(if (hud.lineFull) R.string.cast_fast_reel_line_full else R.string.cast_fast_reel),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (vm.paused) {
            val inBattle = turn != null
            AlertDialog(
                onDismissRequest = vm::resume,
                title = { Text(stringResource(R.string.cast_pause_title)) },
                text = {
                    Text(
                        stringResource(
                            when {
                                inBattle && vm.isOnlineBattle -> R.string.cast_pause_body_online
                                inBattle -> R.string.cast_pause_body_battle
                                vm.tournamentAttempt != null -> R.string.cast_pause_body_tournament
                                else -> R.string.cast_pause_body_solo
                            },
                        ),
                    )
                },
                confirmButton = { TextButton(onClick = vm::resume) { Text(stringResource(R.string.cast_pause_resume)) } },
                dismissButton = {
                    TextButton(onClick = vm::abandonCast) {
                        Text(stringResource(if (inBattle) R.string.cast_pause_forfeit else R.string.cast_pause_quit))
                    }
                },
            )
        }

        vm.pendingResult?.let {
            val collect = if (turn != null || vm.tournamentAttempt != null) R.string.cast_results_continue else R.string.cast_results_collect
            ResultsPanel(it, collectLabel = stringResource(collect), onCollect = vm::collectResult)
        }
    }
}

@Composable
private fun HudChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(Color(0x99001421), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

/** Plain snapshot of what the HUD shows, taken from the (non-observable) session each frame. */
private data class HudState(
    val depthM: Int,
    val phase: CastPhase,
    val points: Int,
    val touchesLeft: Int,
    val carriedKg: Float,
    val maxKg: Float,
    val lanternLit: Boolean,
    val elapsedSeconds: Float,
    val nothingCaughtYet: Boolean,
    val bumpFree: Boolean,
    /** The fast reel button shows (all bumps used, or the line can take no more fish). */
    val canFastReel: Boolean,
    val lineFull: Boolean,
    /** Bumps allowed this cast (more with a Lucky charm). */
    val touchLimit: Int,
) {
    companion object {
        fun of(s: CastSession) = HudState(
            depthM = s.hookDepth.roundToInt(),
            phase = s.phase,
            points = s.pointsSoFar,
            touchesLeft = s.touchesLeft,
            carriedKg = s.carriedWeightKg,
            maxKg = s.specs.maxWeightKg,
            lanternLit = s.lanternActive && s.hookDepth > GameConfig.DARK_DEPTH,
            elapsedSeconds = s.elapsedSeconds,
            nothingCaughtYet = s.catches.isEmpty(),
            bumpFree = s.isBumpFree,
            canFastReel = s.canFastReel,
            lineFull = s.lineIsFull,
            touchLimit = s.touchLimit,
        )
    }
}

@Composable
private fun CastHud(
    hud: HudState,
    notice: Notice?,
    battleLabel: String?,
    timeLeft: Float?,
    opponentLive: String?,
    onPause: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudChip(metersLabel(hud.depthM))
            HudChip(
                stringResource(
                    when (hud.phase) {
                        CastPhase.DESCENDING -> R.string.cast_hud_phase_sinking
                        CastPhase.ASCENDING -> R.string.cast_hud_phase_reeling
                        CastPhase.SURFACED -> R.string.cast_hud_phase_surfaced
                    },
                ),
            )
            Spacer(Modifier.weight(1f))
            HudChip(pointsLabel(hud.points.toLong()))
            val pause = stringResource(R.string.cast_hud_pause)
            FilledTonalButton(onClick = onPause, modifier = Modifier.semantics { contentDescription = pause }) {
                Text("II", fontWeight = FontWeight.Black) // i18n-ignore: the pause sign, not a word (read out as cast_hud_pause)
            }
        }
        Row(
            modifier = Modifier
                .background(Color(0x99001421), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.cast_hud_bumps), color = Color.White, style = MaterialTheme.typography.labelMedium)
            repeat(hud.touchLimit) { i ->
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (i < hud.touchesLeft) Color(0xFF80DEEA) else Color(0x33FFFFFF)),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.cast_hud_load, oneDecimal(hud.carriedKg), formatDecimal(hud.maxKg.toDouble(), 0)),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
            LinearProgressIndicator(
                progress = { (hud.carriedKg / hud.maxKg).coerceIn(0f, 1f) },
                modifier = Modifier.width(70.dp),
            )
        }
        if (battleLabel != null || timeLeft != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                battleLabel?.let { HudChip(it) }
                timeLeft?.let {
                    Text(
                        "⏱ ${formatClock(it)}",
                        modifier = Modifier
                            .background(
                                if (it < 15f) Color(0xCCB71C1C) else Color(0x99001421),
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        opponentLive?.let {
            Text(
                it,
                modifier = Modifier
                    .background(Color(0x99001421), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color(0xFFFFCC80),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (hud.bumpFree) {
            Text(
                stringResource(R.string.cast_hud_bump_free, GameConfig.BUMP_FREE_DEPTH.toInt()),
                modifier = Modifier
                    .background(Color(0x99003D1F), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color(0xFF9CFFB0),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (hud.lanternLit) {
            HudChip(stringResource(R.string.cast_hud_lantern_lit), Modifier.align(Alignment.Start))
        }

        val hint = when {
            hud.elapsedSeconds < 3.5f ->
                stringResource(R.string.cast_hint_steer, GameConfig.BUMP_FREE_DEPTH.toInt(), GameConfig.DESCENT_TOUCH_LIMIT)
            hud.phase == CastPhase.ASCENDING && hud.nothingCaughtYet && notice == null ->
                stringResource(R.string.cast_hint_heads)
            else -> null
        }
        val activeNotice = notice?.takeIf { hud.elapsedSeconds - it.shownAtSeconds < 1.8f }
        val message = activeNotice?.text?.asString() ?: hint
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .background(Color(0x77001421), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                // On the dark notice band over the water, whatever the menu theme (a light theme's
                // secondary colour is dark and couldn't be read there).
                color = if (activeNotice != null) SparkleGold else Color.White,
                style = if (activeNotice != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (activeNotice != null) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** How deep the cast went, counting what was caught (a hooked rock's center can sit below the hook). */
private fun deepestPoint(result: CastResult): Float =
    maxOf(result.maxDepth, result.catches.maxOfOrNull { it.depth } ?: 0f, result.snags.maxOfOrNull { it.depth } ?: 0f)

/** "312 m": whole meters. */
@Composable
@ReadOnlyComposable
private fun metersLabel(meters: Int): String = stringResource(R.string.common_meters, formatPoints(meters))

/** A weight with exactly one decimal ("4.0", "4,0" in French), so a changing value keeps its width. */
private fun oneDecimal(kg: Float): String = "%.1f".format(kg)

/** "4.5 kg", with exactly one decimal. */
internal fun castWeightText(kg: Float): UiText = UiText.res(R.string.common_kg, oneDecimal(kg))

@Composable
private fun ResultsPanel(result: CastResult, collectLabel: String, onCollect: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cast_results_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val fish = result.catches.size
                val objects = result.snags.size
                Text(
                    listOfNotNull(
                        stringResource(R.string.cast_results_deepest, metersLabel(deepestPoint(result).roundToInt())),
                        pluralStringResource(R.plurals.cast_results_fish, fish, fish),
                        if (objects > 0) pluralStringResource(R.plurals.cast_results_objects, objects, objects) else null,
                        castWeightText(result.totalWeightKg).asString(),
                    ).joinToString(ITEM_SEPARATOR),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val byLastBump = stringResource(R.string.cast_results_by_last_bump)
                if (result.catches.isEmpty() && result.snags.isEmpty()) {
                    Text(
                        stringResource(R.string.cast_results_nothing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(result.snags) { sn ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(width = 48.dp, height = 28.dp)) {
                                    drawObstacleIcon(sn.kind, Offset(size.width / 2f, size.height / 2f), size.height * 0.45f)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sn.kind.label(), color = com.deepline.fishing.ui.rewardTextColor(), fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${metersLabel(sn.depth.roundToInt())} · $byLastBump",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text("+${formatPoints(sn.points)}", fontWeight = FontWeight.Bold, color = com.deepline.fishing.ui.rewardTextColor())
                            }
                        }
                        items(result.catches) { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(width = 48.dp, height = 28.dp)) {
                                    drawFish(
                                        species = c.species,
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        lengthPx = size.width * 0.9f,
                                        direction = 1,
                                        sparkling = c.sparkling,
                                        timeSeconds = 0f,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (c.sparkling) stringResource(R.string.cast_results_sparkling, c.species.label()) else c.species.label(),
                                        color = if (c.sparkling) rewardTextColor() else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        listOfNotNull(
                                            metersLabel(c.depth.roundToInt()),
                                            castWeightText(c.species.weightKg).asString(),
                                            byLastBump.takeIf { c.byLastBump },
                                        ).joinToString(ITEM_SEPARATOR),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text("+${formatPoints(c.points)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                for (b in result.secretBonuses) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.cast_results_secret, secretTitle(b.secretId)),
                            color = com.deepline.fishing.ui.rewardTextColor(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("+${formatPoints(b.points)}", fontWeight = FontWeight.Bold, color = com.deepline.fishing.ui.rewardTextColor())
                    }
                }
                Text(
                    stringResource(R.string.cast_results_total, formatPoints(result.totalPoints)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = onCollect, modifier = Modifier.fillMaxWidth()) { Text(collectLabel) }
            }
        }
    }
}
