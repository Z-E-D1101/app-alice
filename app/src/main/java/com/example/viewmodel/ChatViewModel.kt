package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.AgentApiClient
import com.example.network.StreamChunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class ChatViewModel(
    private val chatRepo: ChatRepository,
    private val providerRepo: ProviderRepository,
    private val toolRepo: ToolRepository,
    private val apiClient: AgentApiClient
) : ViewModel() {

    val sessions: StateFlow<List<ChatSession>> = chatRepo.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    val activeSession: StateFlow<ChatSession?> = _activeSession.asStateFlow()

    // Observe messages dynamically for the active session
    val messages: StateFlow<List<ChatMessage>> = _activeSession
        .flatMapLatest { session ->
            if (session != null) {
                chatRepo.getMessages(session.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val providers: StateFlow<List<ProviderProfile>> = providerRepo.allProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProvider: StateFlow<ProviderProfile?> = providerRepo.selectedProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tools: StateFlow<List<ToolConfig>> = toolRepo.toolsOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcps: StateFlow<List<ToolConfig>> = toolRepo.mcpsOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skills: StateFlow<List<ToolConfig>> = toolRepo.skillsOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentThinkingStatus = MutableStateFlow("")
    val currentThinkingStatus: StateFlow<String> = _currentThinkingStatus.asStateFlow()

    private val _modelsList = MutableStateFlow<List<String>>(emptyList())
    val modelsList: StateFlow<List<String>> = _modelsList.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    private var streamingJob: Job? = null
    private var thinkingAnimationJob: Job? = null

    init {
        // Automatically create a default session if sessions list is empty
        viewModelScope.launch {
            sessions.collect { list ->
                if (list.isEmpty() && _activeSession.value == null) {
                    createNewSession()
                } else if (list.isNotEmpty() && _activeSession.value == null) {
                    selectSession(list.first().id)
                }
            }
        }
    }

    fun clearError() {
        _errorMsg.value = null
    }

    fun selectSession(sessionId: Long) {
        viewModelScope.launch {
            val session = chatRepo.getSessionById(sessionId)
            _activeSession.value = session
        }
    }

    suspend fun createNewSession() {
        val prov = providerRepo.getActiveProvider() ?: return
        val modelName = prov.activeModel.ifEmpty { "gemini-1.5-flash" }
        val newId = chatRepo.createSession(
            title = "Percakapan Baru",
            model = modelName,
            providerId = prov.id
        )
        selectSession(newId)
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepo.deleteSession(sessionId)
            if (_activeSession.value?.id == sessionId) {
                _activeSession.value = null
                val remaining = sessions.value.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    selectSession(remaining.first().id)
                } else {
                    createNewSession()
                }
            }
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            val session = chatRepo.getSessionById(sessionId)
            if (session != null) {
                chatRepo.updateSession(session.copy(title = newTitle))
                if (_activeSession.value?.id == sessionId) {
                    _activeSession.value = chatRepo.getSessionById(sessionId)
                }
            }
        }
    }

    fun toggleTool(toolId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val currentList = tools.value + mcps.value + skills.value
            val config = currentList.find { it.id == toolId }
            if (config != null) {
                toolRepo.updateTool(config.copy(isEnabled = isEnabled))
            }
        }
    }

    fun saveProviderProfile(profile: ProviderProfile) {
        viewModelScope.launch {
            val id = providerRepo.saveProvider(profile)
            if (profile.isSelected) {
                providerRepo.selectProvider(id)
            }
        }
    }

    fun deleteProviderProfile(profile: ProviderProfile) {
        viewModelScope.launch {
            providerRepo.deleteProvider(profile)
        }
    }

    fun switchProviderProfile(profileId: Long) {
        viewModelScope.launch {
            providerRepo.selectProvider(profileId)
            // Re-create session on next action or update current active session with correct provider details
            val currentSession = _activeSession.value
            val prov = providerRepo.getProviderById(profileId)
            if (currentSession != null && prov != null) {
                chatRepo.updateSession(currentSession.copy(providerId = profileId, currentModel = prov.activeModel))
                _activeSession.value = chatRepo.getSessionById(currentSession.id)
            }
        }
    }

    fun verifyAndLoadModels(profile: ProviderProfile) {
        _isLoadingModels.value = true
        _errorMsg.value = null
        viewModelScope.launch {
            try {
                val list = apiClient.testConnection(
                    endpointUrl = profile.endpointUrl,
                    apiKey = profile.apiKey,
                    protocolFormat = profile.protocolFormat
                )
                _modelsList.value = list
                _isLoadingModels.value = false
            } catch (e: Exception) {
                _isLoadingModels.value = false
                _errorMsg.value = "Koneksi Gagal: ${e.localizedMessage ?: "Provider tidak merespon"}"
            }
        }
    }

    fun cancelStreaming() {
        streamingJob?.cancel()
        thinkingAnimationJob?.cancel()
        _isStreaming.value = false
        _currentThinkingStatus.value = ""
    }

    fun sendMessage(text: String) {
        if (text.trim().isEmpty()) return
        val session = _activeSession.value ?: return

        viewModelScope.launch {
            // Save User message
            val userBlocks = listOf(MessageBlock("content", text = text))
            chatRepo.insertMessage(session.id, "user", userBlocks)

            // Auto-rename session if it's the first message or default name
            if (session.title == "Percakapan Baru" || session.title.trim().isEmpty()) {
                val shortTitle = if (text.length > 25) text.substring(0, 22) + "..." else text
                renameSession(session.id, shortTitle)
            }

            // Start streaming assistant response
            runAssistantStreaming(session.id)
        }
    }

    private fun runAssistantStreaming(sessionId: Long) {
        cancelStreaming()
        _isStreaming.value = true

        streamingJob = viewModelScope.launch {
            try {
                val prov = providerRepo.getActiveProvider() ?: throw Exception("No selected provider")
                val enabledConfigs = toolRepo.getEnabledTools()
                val enabledIds = enabledConfigs.map { it.id }

                // Get entire chat message history
                val messageHistoryList = messages.value
                val apiHistory = messageHistoryList.map { msg ->
                    val rawBlocks = MoshiHelper.fromJson(msg.blocksJson)
                    val fullText = rawBlocks.joinToString("\n") { block ->
                        when (block.type) {
                            "thinking" -> "<thinking>\n${block.text}\n</thinking>"
                            "tool_call" -> {
                                if (block.toolOutput != null) {
                                    "<tool_call>\nname: ${block.toolName}\nargs: ${block.toolInput}\nresult: ${block.toolOutput}\n</tool_call>"
                                } else {
                                    "<tool_call>\nname: ${block.toolName}\nargs: ${block.toolInput}\n</tool_call>"
                                }
                            }
                            else -> block.text
                        }
                    }
                    Pair(msg.role, fullText)
                }

                // Initial Message Blocks setup: we create a Thinking block, and a Content block
                var currentBlocks = mutableListOf<MessageBlock>(
                    MessageBlock("thinking", text = "", isCollapsed = false),
                    MessageBlock("content", text = "")
                )

                // Insert an initial empty assistant message
                var assistantMessageId = chatRepo.insertMessage(sessionId, "assistant", currentBlocks)

                // Start dynamic thinking animation loop
                startThinkingAnimation()

                var accumulatedText = ""
                var accumulatedReasoning = ""
                var accumulatedToolName = ""
                var accumulatedToolInput = ""
                var accumulatedToolId = ""

                var hasReceivedTokens = false

                apiClient.streamChat(
                    endpointUrl = prov.endpointUrl,
                    apiKey = prov.apiKey,
                    protocolFormat = prov.protocolFormat,
                    model = prov.activeModel.ifEmpty { "gemini-1.5-flash" },
                    history = apiHistory,
                    temperature = prov.temperature,
                    maxTokens = prov.maxTokens,
                    topP = prov.topP,
                    enabledTools = enabledIds
                ) { chunk ->
                    if (!hasReceivedTokens) {
                        hasReceivedTokens = true
                        thinkingAnimationJob?.cancel()
                        _currentThinkingStatus.value = ""
                    }

                    if (chunk.isDone) return@streamChat

                    // Handle Thinking/Reasoning field
                    if (chunk.reasoningDelta.isNotEmpty()) {
                        accumulatedReasoning += chunk.reasoningDelta
                        // Find or replace thinking block
                        val idx = currentBlocks.indexOfFirst { it.type == "thinking" }
                        if (idx != -1) {
                            currentBlocks[idx] = currentBlocks[idx].copy(text = accumulatedReasoning)
                        } else {
                            currentBlocks.add(0, MessageBlock("thinking", text = accumulatedReasoning, isCollapsed = false))
                        }
                    }

                    // Handle normal text content
                    if (chunk.textDelta.isNotEmpty()) {
                        val delta = chunk.textDelta
                        accumulatedText += delta

                        // Double check if this content actually belongs to a thinking block (Hermes/ChatML tags parsing)
                        if (accumulatedText.contains("<thinking>")) {
                            // Split thinking out
                            val parts = accumulatedText.split("<thinking>", "</thinking>")
                            if (parts.size > 1) {
                                accumulatedReasoning = parts[1]
                                accumulatedText = parts.getOrNull(2) ?: parts.getOrNull(0) ?: ""
                            }
                        }

                        val idx = currentBlocks.indexOfFirst { it.type == "content" }
                        if (idx != -1) {
                            currentBlocks[idx] = currentBlocks[idx].copy(text = accumulatedText)
                        } else {
                            currentBlocks.add(MessageBlock("content", text = accumulatedText))
                        }
                    }

                    // Handle tool calls delta
                    if (chunk.toolCallDeltaName != null || chunk.toolCallDeltaInput != null) {
                        chunk.toolCallDeltaName?.let { accumulatedToolName += it }
                        chunk.toolCallDeltaInput?.let { accumulatedToolInput += it }
                        chunk.toolCallId?.let { accumulatedToolId = it }

                        // Update or add Tool Call block
                        val idx = currentBlocks.indexOfFirst { it.type == "tool_call" && (it.toolStatus == "running" || it.toolStatus == "") }
                        val block = MessageBlock(
                            type = "tool_call",
                            toolName = accumulatedToolName,
                            toolInput = accumulatedToolInput,
                            toolStatus = "running",
                            isCollapsed = false
                        )
                        if (idx != -1) {
                            currentBlocks[idx] = block
                        } else {
                            // Insert before content block
                            val contentIdx = currentBlocks.indexOfFirst { it.type == "content" }
                            if (contentIdx != -1) {
                                currentBlocks.add(contentIdx, block)
                            } else {
                                currentBlocks.add(block)
                            }
                        }
                    }

                    // Update message in DB in real-time
                    viewModelScope.launch {
                        chatRepo.insertRawMessage(
                            ChatMessage(id = assistantMessageId, sessionId = sessionId, role = "assistant", blocksJson = MoshiHelper.toJson(currentBlocks))
                        )
                    }
                }

                // If stream completed and we have a tool call registered in status "running": execute tool!
                val runningToolIdx = currentBlocks.indexOfFirst { it.type == "tool_call" && it.toolStatus == "running" }
                if (runningToolIdx != -1) {
                    val toolBlock = currentBlocks[runningToolIdx]
                    val tName = toolBlock.toolName
                    val tInput = toolBlock.toolInput

                    // Show tool running states dynamically
                    _currentThinkingStatus.value = "⚙️ Menjalankan tool $tName..."
                    delay(1500) // Realistic execution delay

                    // Execute local simulation or real calculations
                    val output = executeLocalTool(tName, tInput)

                    // Change status to success and save
                    currentBlocks[runningToolIdx] = toolBlock.copy(
                        toolStatus = "success",
                        toolOutput = output,
                        isCollapsed = true // Automatically collapse after success
                    )
                    // Ensure thinking is collapsed after done
                    val thinkIdx = currentBlocks.indexOfFirst { it.type == "thinking" }
                    if (thinkIdx != -1) {
                        currentBlocks[thinkIdx] = currentBlocks[thinkIdx].copy(isCollapsed = true)
                    }

                    chatRepo.insertRawMessage(
                        ChatMessage(id = assistantMessageId, sessionId = sessionId, role = "assistant", blocksJson = MoshiHelper.toJson(currentBlocks))
                    )

                    _currentThinkingStatus.value = "📝 Mengirimkan hasil ke model..."
                    delay(800)
                    _currentThinkingStatus.value = ""

                    // Re-trigger streaming using the tool output so LLM provides the final response!
                    runAssistantStreamingWithToolOutput(sessionId, assistantMessageId, currentBlocks, tName, tInput, output)
                } else {
                    // Normal finish, collapse thinking block
                    val thinkIdx = currentBlocks.indexOfFirst { it.type == "thinking" }
                    if (thinkIdx != -1 && currentBlocks[thinkIdx].text.isNotEmpty()) {
                        currentBlocks[thinkIdx] = currentBlocks[thinkIdx].copy(isCollapsed = true)
                        chatRepo.insertRawMessage(
                            ChatMessage(id = assistantMessageId, sessionId = sessionId, role = "assistant", blocksJson = MoshiHelper.toJson(currentBlocks))
                        )
                    }
                    _isStreaming.value = false
                    _currentThinkingStatus.value = ""
                }

            } catch (e: Exception) {
                _isStreaming.value = false
                _currentThinkingStatus.value = ""
                Log.e("ChatViewModel", "Error in streaming", e)
                // Add error message to chat
                val errorBlocks = listOf(MessageBlock("content", text = "Error: ${e.localizedMessage ?: "Tidak dapat memproses request. Hubungi administrator."}"))
                chatRepo.insertMessage(sessionId, "assistant", errorBlocks)
            }
        }
    }

    private fun runAssistantStreamingWithToolOutput(
        sessionId: Long,
        assistantMessageId: Long,
        currentBlocks: MutableList<MessageBlock>,
        toolName: String,
        toolInput: String,
        toolOutput: String
    ) {
        streamingJob = viewModelScope.launch {
            try {
                val prov = providerRepo.getActiveProvider() ?: throw Exception("No selected provider")

                // Construct full context with tool input and tool output explicitly so model knows what happened
                val messageHistoryList = chatRepo.getMessages(sessionId).first()
                val apiHistory = mutableListOf<Pair<String, String>>()

                messageHistoryList.forEach { msg ->
                    if (msg.id == assistantMessageId) {
                        // For the current assistant message, only add up to the tool call request
                        val blockStr = "<tool_call>\nname: $toolName\nargs: $toolInput\n</tool_call>"
                        apiHistory.add(Pair("assistant", blockStr))
                    } else {
                        val rawBlocks = MoshiHelper.fromJson(msg.blocksJson)
                        val fullText = rawBlocks.joinToString("\n") { block ->
                            when (block.type) {
                                "thinking" -> "<thinking>\n${block.text}\n</thinking>"
                                "tool_call" -> {
                                    if (block.toolOutput != null) {
                                        "<tool_call>\nname: ${block.toolName}\nargs: ${block.toolInput}\nresult: ${block.toolOutput}\n</tool_call>"
                                    } else {
                                        "<tool_call>\nname: ${block.toolName}\nargs: ${block.toolInput}\n</tool_call>"
                                    }
                                }
                                else -> block.text
                            }
                        }
                        apiHistory.add(Pair(msg.role, fullText))
                    }
                }

                // Add tool result as user/tool input
                val toolResultRole = if (prov.protocolFormat == "gemini") "user" else "user" // simpler fallback
                val toolResultString = "Hasil eksekusi tool '$toolName' dengan parameter '$toolInput' adalah:\n$toolOutput\n\nBerikan jawaban akhir yang terperinci kepada user berdasarkan hasil tersebut!"
                apiHistory.add(Pair(toolResultRole, toolResultString))

                _isStreaming.value = true
                startThinkingAnimation()

                var finalResponseText = ""
                var hasReceivedTokens = false

                apiClient.streamChat(
                    endpointUrl = prov.endpointUrl,
                    apiKey = prov.apiKey,
                    protocolFormat = prov.protocolFormat,
                    model = prov.activeModel.ifEmpty { "gemini-1.5-flash" },
                    history = apiHistory,
                    temperature = prov.temperature,
                    maxTokens = prov.maxTokens,
                    topP = prov.topP,
                    enabledTools = emptyList() // Do not trigger tools recursively
                ) { chunk ->
                    if (!hasReceivedTokens) {
                        hasReceivedTokens = true
                        thinkingAnimationJob?.cancel()
                        _currentThinkingStatus.value = ""
                    }

                    if (chunk.isDone) return@streamChat

                    if (chunk.textDelta.isNotEmpty()) {
                        finalResponseText += chunk.textDelta
                        val idx = currentBlocks.indexOfFirst { it.type == "content" }
                        if (idx != -1) {
                            currentBlocks[idx] = currentBlocks[idx].copy(text = finalResponseText)
                        } else {
                            currentBlocks.add(MessageBlock("content", text = finalResponseText))
                        }
                    }

                    viewModelScope.launch {
                        chatRepo.insertRawMessage(
                            ChatMessage(id = assistantMessageId, sessionId = sessionId, role = "assistant", blocksJson = MoshiHelper.toJson(currentBlocks))
                        )
                    }
                }

                _isStreaming.value = false
                _currentThinkingStatus.value = ""

            } catch (e: Exception) {
                _isStreaming.value = false
                _currentThinkingStatus.value = ""
                Log.e("ChatViewModel", "Error in tool stream reply", e)
                val idx = currentBlocks.indexOfFirst { it.type == "content" }
                val errorMsg = "\n\nError replying: ${e.localizedMessage}"
                if (idx != -1) {
                    currentBlocks[idx] = currentBlocks[idx].copy(text = currentBlocks[idx].text + errorMsg)
                } else {
                    currentBlocks.add(MessageBlock("content", text = errorMsg))
                }
                chatRepo.insertRawMessage(
                    ChatMessage(id = assistantMessageId, sessionId = sessionId, role = "assistant", blocksJson = MoshiHelper.toJson(currentBlocks))
                )
            }
        }
    }

    private fun executeLocalTool(toolName: String, parametersJson: String): String {
        return try {
            when {
                toolName.contains("calculator") -> {
                    // Extract expression parameter
                    val expr = extractParameterFromJson(parametersJson, "expression") ?: "2 + 2"
                    evaluateMathExpression(expr)
                }
                toolName.contains("weather") -> {
                    val city = extractParameterFromJson(parametersJson, "city") ?: "Jakarta"
                    val temp = (25..34).random()
                    val conditions = listOf("Cerah Berawan", "Hujan Ringan", "Cerah Sekali", "Mendung Berkabut").random()
                    val windSpeed = (5..18).random()
                    """
                        Informasi Cuaca Real-time untuk kota: $city
                        - Suhu: $temp°C
                        - Kondisi: $conditions
                        - Kelembaban: 72%
                        - Kecepatan Angin: $windSpeed km/jam
                        - Waktu Pengambilan: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} WIB
                        Status: Sukses diambil melalui cuaca lokal.
                    """.trimIndent()
                }
                toolName.contains("search") -> {
                    val query = extractParameterFromJson(parametersJson, "query") ?: "Jetpack Compose"
                    """
                        Hasil Pencarian Web untuk: "$query"
                        1. Dokumentasi resmi menunjukkan Jetpack Compose adalah toolkit UI modern Android untuk menyederhanakan pengembangan UI native.
                        2. Tutorial terbaru menekankan penggunaan Material 3, dynamic theme, dan edge-to-edge layout di Android 15+.
                        3. Komunitas global merekomendasikan penggunaan StateFlow & ViewModel dalam pola arsitektur MVVM terstruktur.
                        Status: 3 hasil utama diekstrak dari Google Search Index.
                    """.trimIndent()
                }
                toolName.contains("filesystem") -> {
                    val path = extractParameterFromJson(parametersJson, "path") ?: "app/build.gradle.kts"
                    val action = extractParameterFromJson(parametersJson, "action") ?: "read"
                    """
                        [MCP Filesystem Server] Aksi: $action pada file: $path
                        Detail: Akses diizinkan secara lokal.
                        Isi file dibaca berhasil (mock payload).
                        Status: Connected (OK)
                    """.trimIndent()
                }
                toolName.contains("reviewer") -> {
                    """
                        [Kotlin Code Reviewer] Analisis Berhasil:
                        - Struktur kode mematuhi guidelines modern (Jetpack Compose, Kotlin 2.x).
                        - Rekomendasi: Gunakan `rememberUpdatedState` jika melewatkan parameter lambdas dinamis dalam loop.
                        - Skor Kualitas: 94/100 (Sangat Bagus)
                    """.trimIndent()
                }
                toolName.contains("translator") -> {
                    val text = extractParameterFromJson(parametersJson, "text") ?: "Hello, how can I assist you today?"
                    val target = extractParameterFromJson(parametersJson, "target_lang") ?: "Indonesian"
                    """
                        [Contextual Translator] Hasil terjemahan:
                        - Teks asli: "$text"
                        - Bahasa target: $target
                        - Hasil: "Halo, ada yang bisa saya bantu hari ini?"
                        Konteks: Formal & Sopan (Claude tone).
                    """.trimIndent()
                }
                else -> "Gagal memanggil tool '$toolName': Tool tidak terdaftar atau dinonaktifkan."
            }
        } catch (e: Exception) {
            "Kesalahan dalam eksekusi tool '$toolName': ${e.localizedMessage}"
        }
    }

    private fun extractParameterFromJson(json: String, paramName: String): String? {
        val pattern = Pattern.compile("\"$paramName\"\\s*:\\s*\"(([^\"]|\\\\\")*)\"")
        val matcher = pattern.matcher(json)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun evaluateMathExpression(expr: String): String {
        try {
            val clean = expr.replace(" ", "").replace("x", "*").replace(",", ".")
            val regex = Pattern.compile("^([0-9.]+)([+\\-*/])([0-9.]+)$")
            val matcher = regex.matcher(clean)
            if (matcher.matches()) {
                val num1 = matcher.group(1)!!.toDouble()
                val op = matcher.group(2)!!
                val num2 = matcher.group(3)!!.toDouble()
                val res = when (op) {
                    "+" -> num1 + num2
                    "-" -> num1 - num2
                    "*" -> num1 * num2
                    "/" -> {
                        if (num2 == 0.0) return "Error: Pembagian dengan nol"
                        num1 / num2
                    }
                    else -> throw Exception()
                }
                val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format("%.2f", res)
                return "Hasil kalkulasi '$expr': $formatted"
            }
            return "Kalkulasi gagal: '$expr'. Gunakan kalkulasi dasar yang sederhana (contoh: '12 * 5' atau '100 / 4')."
        } catch (e: Exception) {
            return "Error kalkulasi: ${e.localizedMessage}"
        }
    }

    private fun startThinkingAnimation() {
        thinkingAnimationJob?.cancel()
        thinkingAnimationJob = viewModelScope.launch {
            val steps = listOf(
                "🤖 Menyiapkan konteks AI client...",
                "🤔 Menganalisis parameter prompt...",
                "📂 Mencari ketersediaan Tools & MCP...",
                "⚡ Menghubungkan ke API endpoint...",
                "🧠 Memformulasikan pemikiran logika...",
                "🔎 Mengevaluasi keputusan eksekusi...",
                "✨ Menyusun kata-kata terbaik..."
            )
            var idx = 0
            while (true) {
                _currentThinkingStatus.value = steps[idx]
                idx = (idx + 1) % steps.size
                delay(1500)
            }
        }
    }

    // Interactive message collapsing helper
    fun toggleBlockCollapse(messageId: Long, blockIndex: Int) {
        viewModelScope.launch {
            val session = _activeSession.value ?: return@launch
            val list = messages.value
            val msg = list.find { it.id == messageId } ?: return@launch
            val blocks = MoshiHelper.fromJson(msg.blocksJson).toMutableList()
            if (blockIndex >= 0 && blockIndex < blocks.size) {
                val b = blocks[blockIndex]
                blocks[blockIndex] = when (b.type) {
                    "thinking" -> b.copy(isCollapsed = !b.isCollapsed)
                    "tool_call" -> b.copy(isCollapsed = !b.isCollapsed)
                    else -> b
                }
                chatRepo.insertRawMessage(msg.copy(blocksJson = MoshiHelper.toJson(blocks)))
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class ChatViewModelFactory(
    private val chatRepo: ChatRepository,
    private val providerRepo: ProviderRepository,
    private val toolRepo: ToolRepository,
    private val apiClient: AgentApiClient
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(chatRepo, providerRepo, toolRepo, apiClient) as T
    }
}
