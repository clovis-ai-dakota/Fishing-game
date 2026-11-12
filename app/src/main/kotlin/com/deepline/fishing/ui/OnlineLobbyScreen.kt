package com.deepline.fishing.ui

import com.deepline.fishing.R
import com.deepline.fishing.ui.i18n.label
import com.deepline.fishing.ui.i18n.asString
import com.deepline.fishing.ui.i18n.rules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.OnlineStatus
import com.deepline.fishing.core.online.OnlineProtocol

@Composable
fun OnlineLobbyScreen(vm: GameViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    var showServer by rememberSaveable { mutableStateOf(false) }
    val status = vm.onlineStatus
    val waiting = status == OnlineStatus.WAITING_ROOM || status == OnlineStatus.WAITING_QUICK
    val busy = waiting || status == OnlineStatus.CONNECTING

    SubScreen(title = stringResource(R.string.online_title), onBack = vm::closeOnlineLobby) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(vm.onlineMode.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(vm.onlineMode.rules(), style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(R.string.online_tournament_rope, OnlineProtocol.TOURNAMENT_ROPE_LEVEL),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = vm.playerName,
                    onValueChange = vm::updatePlayerName,
                    label = { Text(stringResource(R.string.online_your_name)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (waiting) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val room = vm.roomCode
                            if (status == OnlineStatus.WAITING_ROOM && room != null) {
                                Text(stringResource(R.string.online_share_code), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    room,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 6.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(stringResource(R.string.online_waiting_join), style = MaterialTheme.typography.bodySmall)
                                // A challenge to a friend: whether it reached them.
                                vm.social.challengeStatus?.let {
                                    Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("challenge_status"))
                                }
                            } else {
                                Text(stringResource(R.string.online_looking), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.online_paired_with, vm.onlineMode.label()),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            CircularProgressIndicator()
                            OutlinedButton(onClick = vm::cancelWaiting) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = vm::createRoom,
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) { Text(stringResource(R.string.online_create_room)) }
                        FilledTonalButton(
                            onClick = vm::quickMatch,
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) { Text(stringResource(R.string.online_quick_match)) }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase().take(OnlineProtocol.ROOM_CODE_LENGTH) },
                            label = { Text(stringResource(R.string.online_room_code)) },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { vm.joinRoom(code) }, enabled = !busy && code.isNotBlank()) { Text(stringResource(R.string.online_join)) }
                    }
                }
            }

            item {
                val text = when (status) {
                    OnlineStatus.DISCONNECTED -> R.string.online_status_disconnected
                    OnlineStatus.CONNECTING -> R.string.online_status_connecting
                    OnlineStatus.CONNECTED -> R.string.online_status_connected
                    OnlineStatus.WAITING_ROOM, OnlineStatus.WAITING_QUICK -> R.string.online_status_waiting
                    OnlineStatus.IN_MATCH -> R.string.online_status_in_match
                }
                Text(stringResource(text), style = MaterialTheme.typography.bodySmall)
                vm.onlineError?.let {
                    Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                TextButton(onClick = { showServer = !showServer }) {
                    Text(stringResource(if (showServer) R.string.online_server_hide else R.string.online_server_show))
                }
                if (showServer) {
                    OutlinedTextField(
                        value = vm.serverUrl,
                        onValueChange = vm::updateServerUrl,
                        label = { Text(stringResource(R.string.online_server_address)) },
                        supportingText = { Text(stringResource(R.string.online_server_example, "wss://your-server.example.com/battle")) },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
