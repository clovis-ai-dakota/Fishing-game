package com.deepline.fishing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.deepline.fishing.ui.AppRoot
import com.deepline.fishing.ui.DeepLineTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeepLineTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPaused()
    }
}
