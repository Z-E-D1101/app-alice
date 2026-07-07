package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Room Database Entities
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val blocksJson: String = "{}",
    val timestamp: Long = System.currentTimeMillis()
)

// UI Models
data class MessageBlock(
    val type: String,
    val text: String,
    val isCollapsed: Boolean = false,
    val toolName: String = "",
    val toolStatus: String = "",
    val toolInput: String = "",
    val toolOutput: String? = null,
    val toolDurationMs: Long = 0L,
    val toolTokens: Long = 0L
)

data class ToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val isEnabled: Boolean,
    val extraInfo: String = ""
)

data class ProviderProfile(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val endpointUrl: String,
    val apiKey: String,
    val protocolFormat: String = "openai",
    val activeModel: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val topP: Double = 0.9,
    val isSelected: Boolean = false
)

data class TokenUsage(
    val tokens: Int = 0,
    val toolsUsed: Int = 0
)

// Helper data classes for API responses
data class MoshiHelper {
    companion object {
        fun fromJson(json: String): List<MessageBlock> {
            return try {
                emptyList() // Simplified for now
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
