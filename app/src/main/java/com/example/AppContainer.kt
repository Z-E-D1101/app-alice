package com.example

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.ChatRepository
import com.example.data.ProviderRepository
import com.example.data.ToolRepository
import com.example.network.AgentApiClient

class AppContainer(private val context: Context) {
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(database.chatDao())
    }

    val providerRepository: ProviderRepository by lazy {
        ProviderRepository(database.providerDao())
    }

    val toolRepository: ToolRepository by lazy {
        ToolRepository(database.toolDao())
    }

    val apiClient: AgentApiClient by lazy {
        AgentApiClient()
    }
}
