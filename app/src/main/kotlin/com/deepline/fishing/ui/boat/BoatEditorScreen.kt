package com.deepline.fishing.ui.boat

import com.deepline.fishing.ui.i18n.asString
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.DexSection
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.BoatDesign
import com.deepline.fishing.core.BoatPalette
import com.deepline.fishing.core.BoatPart
import com.deepline.fishing.core.BoatParts
import com.deepline.fishing.core.Emblem
import com.deepline.fishing.core.FlagStyle
import com.deepline.fishing.core.HullPattern
import com.deepline.fishing.core.PartRequirement
import com.deepline.fishing.core.SailStyle
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.SkinPreview
import com.deepline.fishing.ui.SubScreen
import com.deepline.fishing.ui.formatPoints
import com.deepline.fishing.ui.i18n.swatchLabel

/** One colour slot of the design: its label, and how to read and change it. */
private class ColorSlot(@StringRes val label: Int, val get: (BoatDesign) -> Int, val set: (BoatDesign, Int) -> BoatDesign)

private val hullColor = ColorSlot(R.string.boat_editor_hull_colour, { it.hull }, { d, i -> d.copy(hull = i) })
private val bottomColor = ColorSlot(R.string.boat_editor_bottom_colour, { it.hullBottom }, { d, i -> d.copy(hullBottom = i) })
private val trimColor = ColorSlot(R.string.boat_editor_trim_colour, { it.trim }, { d, i -> d.copy(trim = i) })
private val cabinColor = ColorSlot(R.string.boat_editor_cabin_colour, { it.cabin }, { d, i -> d.copy(cabin = i) })
private val sailColor = ColorSlot(R.string.boat_editor_sail_colour, { it.sail }, { d, i -> d.copy(sail = i) })
private val flagColor = ColorSlot(R.string.boat_editor_flag_colour, { it.flag }, { d, i -> d.copy(flag = i) })

/**
 * The boat editor: paint and rig your own boat. The editor is free; options unlock with points
 * (bought once) or progress. A locked option explains how to get it, and points-cost ones can be
 * bought on the spot. Saving wears the boat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoatEditorScreen(vm: GameViewModel) {
    val p = vm.progress
    var draft by remember { mutableStateOf(p.boatDesign) }
    // A locked option the player tapped, and what choosing it does to the draft.
    var asked by remember { mutableStateOf<Pair<BoatPart, BoatDesign>?>(null) }

    fun choose(part: BoatPart, next: BoatDesign) {
        if (BoatParts.isAvailable(vm.progress, part)) draft = next else asked = part to next
    }

    SubScreen(title = stringResource(R.string.boat_editor_title), onBack = vm::onBack) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SkinPreview(vm.skins.copy(boat = null, customBoat = draft), height = 190.dp)
            Text(
                stringResource(R.string.boat_editor_intro, formatPoints(p.pointsBalance)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SwatchRow(hullColor, draft, vm, ::choose)
            SwatchRow(bottomColor, draft, vm, ::choose)
            ChipRow(R.string.boat_editor_pattern, HullPattern.entries.map { BoatPart.of(it) to draft.copy(pattern = it) }, BoatPart.of(draft.pattern), vm, ::choose)
            SwatchRow(trimColor, draft, vm, ::choose)
            SwatchRow(cabinColor, draft, vm, ::choose)
            ChipRow(R.string.boat_editor_sail, SailStyle.entries.map { BoatPart.of(it) to draft.copy(sailStyle = it) }, BoatPart.of(draft.sailStyle), vm, ::choose)
            if (draft.sailStyle != SailStyle.NONE) SwatchRow(sailColor, draft, vm, ::choose)
            ChipRow(R.string.boat_editor_flag, FlagStyle.entries.map { BoatPart.of(it) to draft.copy(flagStyle = it) }, BoatPart.of(draft.flagStyle), vm, ::choose)
            if (draft.flagStyle != FlagStyle.NONE) SwatchRow(flagColor, draft, vm, ::choose)
            ChipRow(R.string.boat_editor_emblem, Emblem.entries.map { BoatPart.of(it) to draft.copy(emblem = it) }, BoatPart.of(draft.emblem), vm, ::choose)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = vm::onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_cancel)) }
            Button(
                onClick = { if (vm.saveBoatDesign(draft)) vm.openDex(DexSection.SKINS) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.boat_editor_save)) }
        }
    }

    asked?.let { (part, next) ->
        val r = BoatParts.requirement(part)
        val name = partName(part).asString()
        if (r is PartRequirement.Points) {
            val affordable = vm.progress.pointsBalance >= r.cost
            AlertDialog(
                onDismissRequest = { asked = null },
                title = { Text(stringResource(R.string.boat_editor_unlock_title, name)) },
                text = {
                    Text(
                        pluralStringResource(
                            if (affordable) R.plurals.boat_editor_unlock_body else R.plurals.boat_editor_unlock_body_short,
                            r.cost,
                            name,
                            formatPoints(r.cost),
                            formatPoints(vm.progress.pointsBalance),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = affordable,
                        onClick = {
                            if (vm.unlockBoatPart(part)) draft = next
                            asked = null
                        },
                    ) { Text(stringResource(R.string.boat_editor_unlock_buy, formatPoints(r.cost))) }
                },
                dismissButton = { TextButton(onClick = { asked = null }) { Text(stringResource(R.string.boat_editor_not_now)) } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { asked = null },
                title = { Text(stringResource(R.string.boat_editor_locked_title, name)) },
                text = { Text(unlockHintText(r).asString()) },
                confirmButton = { TextButton(onClick = { asked = null }) { Text(stringResource(R.string.common_ok)) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(slot: ColorSlot, draft: BoatDesign, vm: GameViewModel, choose: (BoatPart, BoatDesign) -> Unit) {
    val slotLabel = stringResource(slot.label)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(slotLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoatPalette.swatches.forEachIndexed { i, swatch ->
                val part = BoatPart.color(i)
                val unlocked = BoatParts.isAvailable(vm.progress, part)
                val selected = slot.get(draft) == i
                val description = stringResource(
                    if (unlocked) R.string.boat_editor_swatch_cd else R.string.boat_editor_swatch_cd_locked,
                    slotLabel,
                    swatchLabel(swatch.name),
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(swatch.argb))
                        .border(
                            if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            CircleShape,
                        )
                        .clickable(role = Role.RadioButton) { choose(part, slot.set(draft, i)) }
                        .semantics {
                            contentDescription = description
                            this.selected = selected
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!unlocked) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        )
                        Icon(GameIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    @StringRes label: Int,
    options: List<Pair<BoatPart, BoatDesign>>,
    current: BoatPart,
    vm: GameViewModel,
    choose: (BoatPart, BoatDesign) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Premium-only options belong to the shop's premade boats.
            for ((part, next) in options.filter { !BoatParts.isPremiumOnly(it.first) }) {
                val unlocked = BoatParts.isAvailable(vm.progress, part)
                FilterChip(
                    selected = part == current,
                    onClick = { choose(part, next) },
                    label = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(partName(part).asString())
                            if (!unlocked) {
                                Text(
                                    requirementText(BoatParts.requirement(part)).asString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = if (unlocked) null else {
                        { Icon(GameIcons.Lock, contentDescription = stringResource(R.string.boat_editor_locked_cd), modifier = Modifier.size(16.dp)) }
                    },
                )
            }
        }
    }
}
