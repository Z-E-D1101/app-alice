package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSession(session: ChatSession)

    @Delete
    suspend fun deleteSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM provider_profiles")
    fun getAllProvidersFlow(): Flow<List<ProviderProfile>>

    @Query("SELECT * FROM provider_profiles")
    suspend fun getAllProviders(): List<ProviderProfile>

    @Query("SELECT * FROM provider_profiles WHERE isSelected = 1 LIMIT 1")
    fun getSelectedProviderFlow(): Flow<ProviderProfile?>

    @Query("SELECT * FROM provider_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProvider(): ProviderProfile?

    @Query("SELECT * FROM provider_profiles WHERE id = :id")
    suspend fun getProviderById(id: Long): ProviderProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: ProviderProfile): Long

    @Update
    suspend fun updateProvider(provider: ProviderProfile)

    @Delete
    suspend fun deleteProvider(provider: ProviderProfile)

    @Query("UPDATE provider_profiles SET isSelected = (id = :selectedId)")
    suspend fun selectProviderOnly(selectedId: Long)
}

@Dao
interface ToolDao {
    @Query("SELECT * FROM tool_configs")
    fun getAllToolsFlow(): Flow<List<ToolConfig>>

    @Query("SELECT * FROM tool_configs WHERE type = :type")
    fun getToolsByTypeFlow(type: String): Flow<List<ToolConfig>>

    @Query("SELECT * FROM tool_configs WHERE isEnabled = 1")
    suspend fun getEnabledTools(): List<ToolConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTool(tool: ToolConfig)

    @Update
    suspend fun updateTool(tool: ToolConfig)
}

@Database(
    entities = [ChatSession::class, ChatMessage::class, ProviderProfile::class, ToolConfig::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun providerDao(): ProviderDao
    abstract fun toolDao(): ToolDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_agent_client_db"
                )
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val appDb = getDatabase(context)
                    populateDefaultData(appDb)
                }
            }

            private suspend fun populateDefaultData(db: AppDatabase) {
                // Populate default Providers
                val geminiProfile = ProviderProfile(
                    name = "AI Studio Gemini",
                    endpointUrl = "https://generativelanguage.googleapis.com",
                    apiKey = "", // Injected at runtime via BuildConfig or user input
                    protocolFormat = "gemini",
                    activeModel = "gemini-1.5-flash",
                    isSelected = true
                )
                val openaiProfile = ProviderProfile(
                    name = "Local Mock Server",
                    endpointUrl = "http://10.0.2.2:8000/v1", // Standard Android emulator localhost loopback
                    apiKey = "sk-mock-key-123",
                    protocolFormat = "openai",
                    activeModel = "gpt-4o-mini",
                    isSelected = false
                )
                db.providerDao().insertProvider(geminiProfile)
                db.providerDao().insertProvider(openaiProfile)

                // Populate default Tools
                val defaultTools = listOf(
                    ToolConfig("tool_calculator", "Calculator", "Melakukan perhitungan matematika dasar (tambah, kurang, kali, bagi)", "tool", true),
                    ToolConfig("tool_weather", "Weather Lookup", "Mengambil data cuaca real-time untuk kota tertentu", "tool", true),
                    ToolConfig("tool_search", "Web Search", "Mencari informasi terbaru di internet melalui Google Search", "tool", false),

                    ToolConfig("mcp_filesystem", "Filesystem Server", "Mengakses, membaca, dan menulis file system lokal secara aman", "mcp", true, "http://localhost:3011 - Connected"),
                    ToolConfig("mcp_postgres", "PostgreSQL Server", "Berinteraksi, melakukan query, dan merelasikan database PostgreSQL lokal", "mcp", false, "http://localhost:5432 - Disconnected"),

                    ToolConfig("skill_reviewer", "Kotlin Code Reviewer", "Menganalisis, mengevaluasi, dan mereview implementasi kode Kotlin dan Jetpack Compose", "skill", true),
                    ToolConfig("skill_translator", "Contextual Translator", "Menerjemahkan teks antar bahasa asing secara akurat dengan mempertahankan konteks budaya", "skill", true)
                )

                for (tool in defaultTools) {
                    db.toolDao().insertTool(tool)
                }
            }
        }
    }
}
