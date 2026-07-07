package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.ChatViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val context = LocalContext.current
        val appContainer = (context.applicationContext as MainApplication).container
        
        val chatViewModel: ChatViewModel = viewModel(
          factory = ChatViewModelFactory(
            chatRepo = appContainer.chatRepository,
            providerRepo = appContainer.providerRepository,
            toolRepo = appContainer.toolRepository,
            apiClient = appContainer.apiClient
          )
        )

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          MainScreen(
            viewModel = chatViewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}

