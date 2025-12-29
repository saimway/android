package com.example.sonic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sonic.ui.screens.HomeScreen
import com.example.sonic.ui.screens.PlayerScreen
import com.example.sonic.ui.viewmodel.MainViewModel
import com.example.sonic.ui.theme.SonicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SonicTheme {
                SonicAppContent()
            }
        }
    }
}

@Composable
fun SonicAppContent() {
    val viewModel: MainViewModel = hiltViewModel()
    var showPlayer by remember { mutableStateOf(false) }

    // Listen to current song changes to auto-show player if needed, or manage state better
    // For now, simple state management

    if (showPlayer) {
        PlayerScreen(
            viewModel = viewModel,
            onBack = { showPlayer = false }
        )
    } else {
        HomeScreen(
            viewModel = viewModel,
            onSongClick = { showPlayer = true }
        )
    }
}
