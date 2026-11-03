package com.deepline.fishing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.roundToInt

/** Latest finger position, read by the game loop without causing recomposition. */
private class SteerInput {
    var pointerX: Float? = null
    var widthPx: Float = 1f
}

@Composable
fun CastScreen(vm: GameViewModel) {
    val session = vm.session ?: return
    val steer = remember(session) { SteerInput() }
    var time by remember(session) { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()

    // The loop only runs while the cast is live: no frames are spent on the pause dialog or
    // the results panel. [time] keeps counting from where it stopped.
    val running = !vm.paused && vm.pendingResult == null
    LaunchedEffect(session, running) {
        if (!running) return@LaunchedEffect
        val resumedAt = time
        var start = 0L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    val target = steer.pointerX?.let { it / steer.widthPx * GameConfig.WORLD_WIDTH }
                    vm.onFrame(dt, target)
                }
                last = now
                time = resumedAt + (now - start) / 1_000_000_000f
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { steer.widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(session) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            steer.pointerX = event.changes.firstOrNull { it.pressed }?.position?.x
                        }
                    }
                },
        ) {
            drawCastScene(session, time, textMeasurer)
        }

        val turn = vm.battleTurn
        val battleLabel = turn?.let { t ->
            val b = vm.battle
            val name = b?.players?.getOrNull(t.playerIndex)?.name ?: "Player"
            val perPlayer = b?.mode?.castsPerPlayer
            when {
                t.round > 0 -> "$name · Round ${t.round}"
                perPlayer != null -> "$name · Cast ${t.castNumber}/$perPlayer"
                else -> "$name · Cast ${t.castNumber}"
            }
        }
        // Reading [time] subscribes this screen to every frame; the HUD then gets plain values
        // so it recomposes whenever one of them changes.
        @Suppress("UNUSED_VARIABLE")
        val frameTick = time
        CastHud(
            hud = HudState.of(session),
            notice = vm.notice,
            battleLabel = battleLabel,
            timeLeft = vm.battleTimeLeft(),
            opponentLive = if (vm.isOnlineBattle) opponentLiveText(vm) else null,
            onPause = vm::pause,
        )

        if (vm.paused) {
            val inBattle = turn != null
            AlertDialog(
                onDismissRequest = vm::resume,
                title = { Text("Paused") },
                text = {
                    Text(
                        if (inBattle) {
                            if (vm.isOnlineBattle) {
                                "Quitting forfeits the battle. Online, your opponent keeps fishing while you're paused."
                            } else {
                                "Quitting forfeits the battle. The battle clock is stopped while paused."
                            }
                        } else {
                            "Quitting ends this cast without scoring any of the fish on the line."
                        },
                    )
                },
                confirmButton = { TextButton(onClick = vm::resume) { Text("Resume") } },
                dismissButton = {
                    TextButton(onClick = vm::abandonCast) { Text(if (inBattle) "Forfeit battle" else "Quit cast") }
                },
            )
        }

        vm.pendingResult?.let {
            ResultsPanel(it, collectLabel = if (turn != null) "Continue" else "Collect", onCollect = vm::collectResult)
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
            HudChip("${hud.depthM} m")
            HudChip(
                when (hud.phase) {
                    CastPhase.DESCENDING -> "Sinking"
                    CastPhase.ASCENDING -> "Reeling up"
                    CastPhase.SURFACED -> "Surfaced"
                },
            )
            Spacer(Modifier.weight(1f))
            HudChip("${formatPoints(hud.points)} pts")
            FilledTonalButton(onClick = onPause) { Text("II", fontWeight = FontWeight.Black) }
        }
        Row(
            modifier = Modifier
                .background(Color(0x99001421), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Bumps", color = Color.White, style = MaterialTheme.typography.labelMedium)
            repeat(GameConfig.DESCENT_TOUCH_LIMIT) { i ->
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (i < hud.touchesLeft) Color(0xFF80DEEA) else Color(0x33FFFFFF)),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Load %.1f / %.0f kg".format(hud.carriedKg, hud.maxKg),
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
                "Bump-free zone · bumps count below ${GameConfig.BUMP_FREE_DEPTH.toInt()} m",
                modifier = Modifier
                    .background(Color(0x99003D1F), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color(0xFF9CFFB0),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (hud.lanternLit) {
            HudChip("Lantern lit", Modifier.align(Alignment.Start))
        }

        val hint = when {
            hud.elapsedSeconds < 3.5f ->
                "Drag to steer. Below ${GameConfig.BUMP_FREE_DEPTH.toInt()} m, avoid bumping things: " +
                    "${GameConfig.DESCENT_TOUCH_LIMIT} bumps and the line comes up."
            hud.phase == CastPhase.ASCENDING && hud.nothingCaughtYet && notice == null ->
                "Nudge fish on the HEAD to hook them. Tails don't count."
            else -> null
        }
        val activeNotice = notice?.takeIf { hud.elapsedSeconds - it.shownAtSeconds < 1.8f }
        val message = activeNotice?.text ?: hint
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .background(Color(0x77001421), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                color = if (activeNotice != null) MaterialTheme.colorScheme.secondary else Color.White,
                style = if (activeNotice != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (activeNotice != null) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

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
                Text("Cast complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Deepest point ${result.maxDepth.roundToInt()} m · ${result.catches.size} fish · %.1f kg"
                        .format(result.totalWeightKg),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result.catches.isEmpty()) {
                    Text(
                        "Nothing on the line this time. On the way up, steer into fish so the hook meets their head.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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
                                        (if (c.sparkling) "✦ Sparkling " else "") + c.species.name,
                                        color = if (c.sparkling) SparkleGold else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${c.depth.roundToInt()} m · %.1f kg".format(c.species.weightKg),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text("+${formatPoints(c.points)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text(
                    "Total  +${formatPoints(result.totalPoints)} pts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = onCollect, modifier = Modifier.fillMaxWidth()) { Text(collectLabel) }
            }
        }
    }
}
