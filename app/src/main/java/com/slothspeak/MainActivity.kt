package com.slothspeak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.slothspeak.ui.screens.HistoryScreen
import com.slothspeak.ui.screens.MainScreen
import com.slothspeak.ui.screens.SettingsScreen
import com.slothspeak.ui.theme.SlothSpeakTheme
import com.slothspeak.viewmodel.ConversationViewModel
import com.slothspeak.viewmodel.HistoryViewModel
import com.slothspeak.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlothSpeakTheme {
                SlothSpeakNavigation()
            }
        }
    }
}

@Composable
fun SlothSpeakNavigation() {
    val navController = rememberNavController()
    val conversationViewModel: ConversationViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                viewModel = conversationViewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("history") }
            )
        }

        composable("settings") {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("history") {
            val historyViewModel: HistoryViewModel = viewModel()
            HistoryScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() },
                onLoadConversation = { conversationId ->
                    conversationViewModel.loadConversation(conversationId)
                    navController.popBackStack()
                }
            )
        }
    }
}
