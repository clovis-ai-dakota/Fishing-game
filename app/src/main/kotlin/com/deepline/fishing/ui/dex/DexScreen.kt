package com.deepline.fishing.ui.dex

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepline.fishing.DexSection
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.FishCatalog
import com.deepline.fishing.ui.TabScreen

/**
 * The Dex tab: numbered cards for every species, every secret (once one is found) and the skins.
 * The Secrets section only appears after the first secret is found; Skins (with boat
 * customisation, the shop and the atmosphere) is always there.
 */
@Composable
fun DexScreen(vm: GameViewModel) {
    val sections = vm.availableDexSections
    val section = vm.dexSection.takeIf { it in sections } ?: DexSection.FISH
    TabScreen(
        title = "Dex",
        subtitle = {
            Text(
                "${FishCatalog.all.size} species to find in the deep",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        if (sections.size > 1) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                sections.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = s == section,
                        onClick = { vm.openDex(s) },
                        shape = SegmentedButtonDefaults.itemShape(i, sections.size),
                    ) { Text(s.label, maxLines = 1) }
                }
            }
        }
        when (section) {
            DexSection.FISH -> SpeciesDex(vm)
            DexSection.SECRETS -> SecretDex(vm)
            DexSection.SKINS -> SkinDex(vm)
        }
    }
}
