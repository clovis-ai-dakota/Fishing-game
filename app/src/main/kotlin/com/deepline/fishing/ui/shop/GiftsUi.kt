package com.deepline.fishing.ui.shop

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.core.ShopSection
import com.deepline.fishing.core.online.AccountApi
import com.deepline.fishing.ui.compactPoints
import java.text.DateFormat
import java.util.Date

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Picks a gift for an online opponent: tackle packs (sized for their rope) or looks. Paid like any
 * purchase, with euros or points; something they own already reaches them as its points price.
 */
@Composable
fun GiftDialog(vm: GameViewModel) {
    val target = vm.giftTarget ?: return
    val activity = LocalContext.current.activity()
    val gifts = AccountApi.giftable.sortedWith(compareBy({ it.section != ShopSection.TACKLE }, { it.euroCents }))
    AlertDialog(
        onDismissRequest = vm::closeGifts,
        title = { Text("A gift for ${target.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "They'll find it the next time they open the game. Looks only, or tackle for their casts.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).testTag("gift_list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(gifts, key = { it.id }) { item ->
                        val points = vm.shopPointsPrice(item)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Button(onClick = { vm.sendGiftWithMoney(item, activity) }, modifier = Modifier.testTag("gift_eur_${item.id}")) {
                                Text(vm.billing.priceOf(AccountApi.giftProduct(item)) ?: item.euroLabel)
                            }
                            OutlinedButton(
                                onClick = { vm.sendGiftWithPoints(item) },
                                enabled = vm.progress.pointsBalance >= points,
                                modifier = Modifier.testTag("gift_pts_${item.id}"),
                            ) { Text("${compactPoints(points)} pts") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::closeGifts) { Text("Close") } },
    )
}

/**
 * The player's account: their public id (opponents send gifts to it), the recovery code that
 * restores a cloud save on another phone, and cloud save for Captain's Club members.
 */
@Composable
fun AccountDialog(vm: GameViewModel, onClose: () -> Unit) {
    val who = vm.playerIdentity
    val context = LocalContext.current
    var showCode by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Your account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Player id: ${who.id}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(
                    "Online opponents can send you gifts; they arrive when you open the game or the Shop.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Cloud save", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                if (vm.canCloudSave) {
                    Text(
                        if (vm.lastBackupMs > 0) "Last backup: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(vm.lastBackupMs))}. " +
                            "Your progress is backed up after your casts." else "Not backed up yet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { vm.backupNow() }, enabled = !vm.accountBusy, modifier = Modifier.testTag("backup_now")) { Text("Back up now") }
                } else {
                    Text("A Captain's Club perk: back up your progress and restore it on a new phone.", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Your recovery code restores a backup on another phone. Keep it private: it's the key to your progress.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (showCode) {
                    Text(who.recoveryCode, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("recovery_code"))
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.setPrimaryClip(ClipData.newPlainText("Deep Line recovery code", who.recoveryCode))
                    }) { Text("Copy the code") }
                } else {
                    TextButton(onClick = { showCode = true }) { Text("Show my recovery code") }
                }
                if (restoring) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(40) },
                        label = { Text("Recovery code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth().testTag("restore_code"),
                    )
                    Text("This replaces the progress on this phone with the backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.restoreFromCode(code) }, enabled = !vm.accountBusy && code.isNotBlank()) { Text("Restore") }
                } else {
                    TextButton(onClick = { restoring = true }) { Text("Restore from another phone") }
                }
                if (vm.accountBusy) CircularProgressIndicator(Modifier.padding(4.dp))
                vm.shopMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
