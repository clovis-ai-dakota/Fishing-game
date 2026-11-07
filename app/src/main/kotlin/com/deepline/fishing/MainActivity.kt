package com.deepline.fishing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.deepline.fishing.ui.AppRoot
import com.deepline.fishing.ui.DeepLineTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Analytics and crash reports: Firebase if configured, collecting only after consent.
        com.deepline.fishing.analytics.Telemetry.start(this)
        enableEdgeToEdge()
        setContent {
            val night = viewModel.nightMode.isNight(phoneIsDark = isSystemInDarkTheme())
            DeepLineTheme(viewModel.menuTheme, night) {
                // Dark status bar icons over a light theme's sky (casts keep light ones).
                val light = com.deepline.fishing.ui.theme.LocalMenuPalette.current.light && viewModel.screen != Screen.CAST
                val view = LocalView.current
                SideEffect { WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = light }
                AppRoot(viewModel)
                com.deepline.fishing.analytics.TrackScreens(viewModel)
                // Google Play's review card, right after a happy moment once the screen is calm.
                viewModel.reviewToAsk?.let { moment ->
                    LaunchedEffect(moment) { viewModel.review.launch(this@MainActivity) }
                }
            }
        }
        // Opened from a reminder: go to the screen it talks about.
        if (savedInstanceState == null) openFromNotification(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromNotification(intent)
    }

    private fun openFromNotification(intent: Intent?) {
        val key = intent?.getStringExtra(com.deepline.fishing.daily.ReminderNotifications.EXTRA_OPEN) ?: return
        intent.removeExtra(com.deepline.fishing.daily.ReminderNotifications.EXTRA_OPEN)
        viewModel.openFromNotification(key)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPaused()
    }

    // Sound stops while the game is in the background and comes back with it.
    override fun onStart() {
        super.onStart()
        viewModel.feel.onForeground()
    }

    override fun onStop() {
        viewModel.feel.onBackground()
        super.onStop()
    }
}
