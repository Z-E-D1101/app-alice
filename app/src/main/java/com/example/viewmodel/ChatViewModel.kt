package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.AgentApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val apiClient = AgentApiClient()

    // Chat state
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    val activeSession: StateFlow<ChatSession?> = _activeSession

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _currentThinkingStatus = MutableStateFlow("")
    val currentThinkingStatus: StateFlow<String> = _currentThinkingStatus

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg

    private val _tokenUsage = MutableStateFlow(TokenUsage())
    val tokenUsage: StateFlow<TokenUsage> = _tokenUsage

    // Provider state
    private val _providers = MutableStateFlow<List<ProviderProfile>>(emptyList())
    val providers: StateFlow<List<ProviderProfile>> = _providers

    private val _selectedProvider = MutableStateFlow<ProviderProfile?>(null)
    val selectedProvider: StateFlow<ProviderProfile?> = _selectedProvider

    private val _modelsList = MutableStateFlow<List<String>>(emptyList())
    val modelsList: StateFlow<List<String>> = _modelsList

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels

    // Tools state
    private val _tools = MutableStateFlow<List<ToolConfig>>(emptyList())
    val tools: StateFlow<List<ToolConfig>> = _tools

    private val _mcps = MutableStateFlow<List<ToolConfig>>(emptyList())
    val mcps: StateFlow<List<ToolConfig>> = _mcps

    private val _skills = MutableStateFlow<List<ToolConfig>>(emptyList())
    val skills: StateFlow<List<ToolConfig>> = _skills

    init {
        initializeMockData()
    }

    private fun initializeMockData() {
        // Initialize with mock data
        _tools.value = listOf(
            ToolConfig("tool_calculator", "Calculator", "Evaluate math expressions", "tool", true),
            ToolConfig("tool_weather", "Weather", "Get weather data", "tool", true),
            ToolConfig("tool_code_executor", "Code Executor", "Execute code", "tool", false)
        )
        _mcps.value = listOf(
            ToolConfig("mcp_github", "GitHub MCP", "GitHub integration", "mcp", true),
            ToolConfig("mcp_web", "Web MCP", "Web search", "mcp", false)
        )
        _skills.value = listOf(
            ToolConfig("skill_reasoning", "Reasoning", "Advanced reasoning", "skill", true),
            ToolConfig("skill_analysis", "Analysis", "Data analysis", "skill", true)
        )
        _providers.value = listOf(
            ProviderProfile(1, "OpenAI", "https://api.openai.com/v1", "", "openai", "gpt-4o-mini", isSelected = true),
            ProviderProfile(2, "Local", "http://localhost:11434", "", "openai", "llama3.2", isSelected = false)
        )
        _selectedProvider.value = _providers.value.firstOrNull { it.isSelected }
    }

    fun createNewSession() {
        val newSession = ChatSession(title = "New Chat")
        _sessions.value = _sessions.value + newSession
        _activeSession.value = newSession
        _messages.value = emptyList()
    }

    fun selectSession(id: Long) {
        _activeSession.value = _sessions.value.find { it.id == id }
    }

    fun deleteSession(id: Long) {
        _sessions.value = _sessions.value.filter { it.id != id }
        if (_activeSession.value?.id == id) {
            _activeSession.value = _sessions.value.firstOrNull()
        }
    }

    fun renameSession(id: Long, newTitle: String) {
        _sessions.value = _sessions.value.map {
            if (it.id == id) it.copy(title = newTitle) else it
        }
    }

    fun sendMessage(content: String) {
        val session = _activeSession.value ?: return
        if (content.isEmpty()) return

        val userMessage = ChatMessage(
            sessionId = session.id,
            role = "user",
            content = content
        )
        _messages.value = _messages.value + userMessage
        _isStreaming.value = true
        _currentThinkingStatus.value = "Processing..."

        viewModelScope.launch {
            try {
                val response = ChatMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = "This is a mock response to: $content"
                )
                _messages.value = _messages.value + response
                _tokenUsage.value = TokenUsage(tokens = content.length, toolsUsed = 0)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending message", e)
                _errorMsg.value = e.message
            } finally {
                _isStreaming.value = false
                _currentThinkingStatus.value = ""
            }
        }
    }

    fun toggleTool(id: String, enabled: Boolean) {
        _tools.value = _tools.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
    }

    fun toggleBlockCollapse(messageId: Long, blockIndex: Int) {
        // Implementation for collapsing/expanding blocks
    }

    fun cancelStreaming() {
        _isStreaming.value = false
        _currentThinkingStatus.value = ""
    }

    fun clearError() {
        _errorMsg.value = null
    }

    fun saveProviderProfile(profile: ProviderProfile) {
        val existing = _providers.value.find { it.id == profile.id }
        _providers.value = if (existing != null) {
            _providers.value.map { if (it.id == profile.id) profile else it }
        } else {
            _providers.value + profile
        }
    }
}
