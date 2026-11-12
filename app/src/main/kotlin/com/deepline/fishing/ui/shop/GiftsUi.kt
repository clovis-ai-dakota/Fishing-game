package com.deepline.fishing.ui.shop

import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.i18n.asString
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.R
import com.deepline.fishing.core.ShopSection
import com.deepline.fishing.core.online.AccountApi
import com.deepline.fishing.ui.compactPoints
import com.deepline.fishing.ui.formatEuros
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
        // The opponent's name as they typed it (never translated).
        title = { Text(stringResource(R.string.shop_gift_pick_title, target.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.shop_gift_pick_note), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 380.dp).testTag("gift_list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(gifts, key = { it.id }) { item ->
                        val points = vm.shopPointsPrice(item)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Button(onClick = { vm.sendGiftWithMoney(item, activity) }, modifier = Modifier.testTag("gift_eur_${item.id}")) {
                                Text(vm.billing.priceOf(AccountApi.giftProduct(item)) ?: formatEuros(item.euroCents))
                            }
                            OutlinedButton(
                                onClick = { vm.sendGiftWithPoints(item) },
                                enabled = vm.progress.pointsBalance >= points,
                                modifier = Modifier.testTag("gift_pts_${item.id}"),
                            ) { Text(stringResource(R.string.common_points, compactPoints(points))) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::closeGifts) { Text(stringResource(R.string.shop_close)) } },
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
        title = { Text(stringResource(R.string.shop_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.shop_account_player_id, who.id), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.shop_account_gifts_note), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.shop_account_cloud_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (vm.canCloudSave) {
                    Text(
                        if (vm.lastBackupMs > 0) {
                            stringResource(
                                R.string.shop_account_last_backup,
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(vm.lastBackupMs)),
                            )
                        } else {
                            stringResource(R.string.shop_account_no_backup)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { vm.backupNow() }, enabled = !vm.accountBusy, modifier = Modifier.testTag("backup_now")) {
                        Text(stringResource(R.string.shop_account_backup_now))
                    }
                } else {
                    Text(stringResource(R.string.shop_account_club_perk), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.shop_account_code_note), style = MaterialTheme.typography.bodySmall)
                if (showCode) {
                    Text(who.recoveryCode, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("recovery_code"))
                    val clipLabel = stringResource(R.string.shop_account_code_clip)
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.setPrimaryClip(ClipData.newPlainText(clipLabel, who.recoveryCode))
                    }) { Text(stringResource(R.string.shop_account_copy_code)) }
                } else {
                    TextButton(onClick = { showCode = true }) { Text(stringResource(R.string.shop_account_show_code)) }
                }
                if (restoring) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(40) },
                        label = { Text(stringResource(R.string.shop_account_code_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth().testTag("restore_code"),
                    )
                    Text(
                        stringResource(R.string.shop_account_restore_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { vm.restoreFromCode(code) }, enabled = !vm.accountBusy && code.isNotBlank()) {
                        Text(stringResource(R.string.shop_account_restore))
                    }
                } else {
                    TextButton(onClick = { restoring = true }) { Text(stringResource(R.string.shop_account_restore_open)) }
                }
                if (vm.accountBusy) CircularProgressIndicator(Modifier.padding(4.dp))
                vm.shopMessage?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.shop_close)) } },
    )
}
