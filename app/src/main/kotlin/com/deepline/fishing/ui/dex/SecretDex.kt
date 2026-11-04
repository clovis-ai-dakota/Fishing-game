package com.deepline.fishing.ui.dex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.Secrets
import com.deepline.fishing.ui.GameIcons
import com.deepline.fishing.ui.secrets.FishingSecret
import com.deepline.fishing.ui.secrets.FishingSecrets
import com.deepline.fishing.ui.secrets.SecretArt
import com.deepline.fishing.ui.secrets.SecretDetails

/**
 * The Secrets section: a numbered card for every secret, coloured by tier. Found ones show their
 * name and picture and open their explanation; locked ones only show their tier and reward,
 * never what they are or how to trigger them.
 */
@Composable
fun SecretDex(vm: GameViewModel) {
    val found = vm.progress.discoveredSecrets
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Discovered ${found.size} of ${Secrets.all.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (tier in 1..Secrets.MAX_TIER) TierChip(tier)
                }
            }
        }
        items(Secrets.all.withIndex().toList(), key = { it.value }) { (i, id) ->
            val secret = FishingSecrets.byId(id)
            if (id in found && secret != null) {
                FoundSecretCard(i + 1, secret) { open = id }
            } else {
                LockedSecretCard(i + 1, id)
            }
        }
    }
    open?.let { id ->
        FishingSecrets.byId(id)?.let { secret ->
            AlertDialog(
                onDismissRequest = { open = null },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecretDetails(secret)
                        FishingSecrets.rewardText(id)?.let {
                            Text("Discovery reward: $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun TierChip(tier: Int) {
    Surface(color = tierColor(tier).copy(alpha = 0.2f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, tierColor(tier))) {
        Text(
            if (tier >= Secrets.MAX_TIER) "★" else "$tier",
            style = MaterialTheme.typography.labelSmall,
            color = tierColor(tier),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CardTop(number: Int, tier: Int, locked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            dexNumberText(number, digits = 2),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (locked) {
            Icon(GameIcons.Lock, contentDescription = "Locked", modifier = Modifier.size(14.dp), tint = tierColor(tier))
            Box(Modifier.size(4.dp))
        }
        Text(
            tierName(tier),
            style = MaterialTheme.typography.labelSmall,
            color = tierColor(tier),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FoundSecretCard(number: Int, secret: FishingSecret, onClick: () -> Unit) {
    val tier = Secrets.tierOf(secret.id)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, tierColor(tier)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTop(number, tier, locked = false)
            SecretArt(secret, height = 72.dp)
            Text(
                secret.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LockedSecretCard(number: Int, id: String) {
    val tier = Secrets.tierOf(id)
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tierColor(tier).copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071726)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTop(number, tier, locked = true)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GameIcons.Lock, contentDescription = null, tint = tierColor(tier).copy(alpha = 0.35f), modifier = Modifier.size(40.dp))
            }
            Text(
                FishingSecrets.lockedRewardText(id)?.let { "Reward: $it" } ?: "Reward: the know-how",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
