package com.example.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getMessages(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getSessionById(sessionId: Long): ChatSession? {
        return chatDao.getSessionById(sessionId)
    }

    suspend fun createSession(title: String, model: String, providerId: Long): Long {
        val session = ChatSession(title = title, currentModel = model, providerId = providerId)
        return chatDao.insertSession(session)
    }

    suspend fun updateSession(session: ChatSession) {
        chatDao.updateSession(session)
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteMessagesForSession(sessionId)
        chatDao.deleteSessionById(sessionId)
    }

    suspend fun insertMessage(sessionId: Long, role: String, blocks: List<MessageBlock>): Long {
        val message = ChatMessage(
            sessionId = sessionId,
            role = role,
            blocksJson = MoshiHelper.toJson(blocks)
        )
        return chatDao.insertMessage(message)
    }

    suspend fun insertRawMessage(message: ChatMessage): Long {
        return chatDao.insertMessage(message)
    }
}

class ProviderRepository(private val providerDao: ProviderDao) {
    val allProviders: Flow<List<ProviderProfile>> = providerDao.getAllProvidersFlow()
    val selectedProvider: Flow<ProviderProfile?> = providerDao.getSelectedProviderFlow()

    suspend fun getActiveProvider(): ProviderProfile? {
        return providerDao.getSelectedProvider()
    }

    suspend fun getProviderById(id: Long): ProviderProfile? {
        return providerDao.getProviderById(id)
    }

    suspend fun saveProvider(provider: ProviderProfile): Long {
        return if (provider.id == 0L) {
            providerDao.insertProvider(provider)
        } else {
            providerDao.updateProvider(provider)
            provider.id
        }
    }

    suspend fun selectProvider(id: Long) {
        providerDao.selectProviderOnly(id)
    }

    suspend fun deleteProvider(provider: ProviderProfile) {
        providerDao.deleteProvider(provider)
    }
}

class ToolRepository(private val toolDao: ToolDao) {
    val allTools: Flow<List<ToolConfig>> = toolDao.getAllToolsFlow()
    val toolsOnly: Flow<List<ToolConfig>> = toolDao.getToolsByTypeFlow("tool")
    val mcpsOnly: Flow<List<ToolConfig>> = toolDao.getToolsByTypeFlow("mcp")
    val skillsOnly: Flow<List<ToolConfig>> = toolDao.getToolsByTypeFlow("skill")

    suspend fun getEnabledTools(): List<ToolConfig> {
        return toolDao.getEnabledTools()
    }

    suspend fun updateTool(tool: ToolConfig) {
        toolDao.updateTool(tool)
    }
}
