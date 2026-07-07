package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val currentModel: String = "",
    val providerId: Long = 0
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "user", "assistant"
    val timestamp: Long = System.currentTimeMillis(),
    val blocksJson: String // Serialized List<MessageBlock>
)

data class MessageBlock(
    val type: String, // "thinking", "tool_call", "content"
    val text: String = "", // text content or thinking text
    val toolName: String = "",
    val toolInput: String = "",
    val toolOutput: String? = null,
    val toolStatus: String = "", // "running", "success", "error"
    val isCollapsed: Boolean = true
)

@Entity(tableName = "provider_profiles")
data class ProviderProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val endpointUrl: String,
    val apiKey: String,
    val protocolFormat: String, // "openai", "hermes", "gemini"
    val activeModel: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val topP: Double = 0.9,
    val isSelected: Boolean = false
)

@Entity(tableName = "tool_configs")
data class ToolConfig(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: String, // "tool", "mcp", "skill"
    val isEnabled: Boolean,
    val extraInfo: String = "" // e.g., URL/Command for MCP, or connection status
)
