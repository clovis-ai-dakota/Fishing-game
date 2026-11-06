package com.deepline.fishing.ui

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

    SubScreen(title = "Online battle", onBack = vm::closeOnlineLobby) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(vm.onlineMode.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(vm.onlineMode.summary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Tournament rope: level ${OnlineProtocol.TOURNAMENT_ROPE_LEVEL} in every stat, for both players.",
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
                    label = { Text("Your name") },
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
                                Text("Share this room code", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    room,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 6.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text("Waiting for your opponent to join…", style = MaterialTheme.typography.bodySmall)
                                // A challenge to a friend: whether it reached them.
                                vm.social.challengeStatus?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("challenge_status"))
                                }
                            } else {
                                Text("Looking for an opponent…", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "You'll be paired with the next player who picks ${vm.onlineMode.title}.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            CircularProgressIndicator()
                            OutlinedButton(onClick = vm::cancelWaiting) { Text("Cancel") }
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
                        ) { Text("Create room") }
                        FilledTonalButton(
                            onClick = vm::quickMatch,
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) { Text("Quick match") }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase().take(OnlineProtocol.ROOM_CODE_LENGTH) },
                            label = { Text("Room code") },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { vm.joinRoom(code) }, enabled = !busy && code.isNotBlank()) { Text("Join") }
                    }
                }
            }

            item {
                val text = when (status) {
                    OnlineStatus.DISCONNECTED -> "Not connected. Creating, joining or quick matching connects you."
                    OnlineStatus.CONNECTING -> "Connecting…"
                    OnlineStatus.CONNECTED -> "Connected."
                    OnlineStatus.WAITING_ROOM, OnlineStatus.WAITING_QUICK -> "Connected · waiting."
                    OnlineStatus.IN_MATCH -> "In a match."
                }
                Text(text, style = MaterialTheme.typography.bodySmall)
                vm.onlineError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                TextButton(onClick = { showServer = !showServer }) {
                    Text(if (showServer) "Hide server settings" else "Server settings")
                }
                if (showServer) {
                    OutlinedTextField(
                        value = vm.serverUrl,
                        onValueChange = vm::updateServerUrl,
                        label = { Text("Server address") },
                        supportingText = { Text("For example wss://your-server.example.com/battle") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
