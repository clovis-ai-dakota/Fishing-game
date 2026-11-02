package com.deepline.fishing.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepline.fishing.GameViewModel
import com.deepline.fishing.Screen

@Composable
fun AppRoot(vm: GameViewModel) {
    BackHandler(enabled = vm.screen != Screen.HOME) { vm.onBack() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (vm.screen) {
            Screen.HOME -> HomeScreen(vm)
            Screen.CAST -> CastScreen(vm)
            Screen.UPGRADES -> UpgradesScreen(vm)
            Screen.COLLECTION -> CollectionScreen(vm)
            Screen.MILESTONES -> MilestonesScreen(vm)
            Screen.BATTLE_SETUP -> BattleSetupScreen(vm)
            Screen.BATTLE -> BattleScreen(vm)
            Screen.ONLINE_LOBBY -> OnlineLobbyScreen(vm)
        }
    }
}

/** Shared layout for the secondary screens: a back button, a title, then the content. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

fun formatPoints(value: Long): String = "%,d".format(value)

fun formatPoints(value: Int): String = formatPoints(value.toLong())
