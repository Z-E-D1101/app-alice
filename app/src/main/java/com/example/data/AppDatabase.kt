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
                // Populate default Providers (Alice-neutral defaults)
                val openaiProfile = ProviderProfile(
                    name = "OpenAI Compatible",
                    endpointUrl = "https://api.openai.com/v1",
                    apiKey = "",
                    protocolFormat = "openai",
                    activeModel = "gpt-4o-mini",
                    isSelected = true
                )
                val ollamaProfile = ProviderProfile(
                    name = "Ollama Local",
                    endpointUrl = "http://localhost:11434/v1",
                    apiKey = "ollama",
                    protocolFormat = "openai",
                    activeModel = "llama3.2",
                    isSelected = false
                )
                db.providerDao().insertProvider(openaiProfile)
                db.providerDao().insertProvider(ollamaProfile)

                // Populate default Tools (40+ tools)
                val defaultTools = listOf(
                    // Core tools
                    ToolConfig("tool_calculator", "Calculator", "Evaluate mathematical expressions", "tool", true),
                    ToolConfig("tool_weather", "Weather Lookup", "Get real-time weather data for any city", "tool", true),
                    ToolConfig("tool_code_executor", "Code Executor", "Execute Python/JavaScript code snippets", "tool", true),
                    ToolConfig("tool_translator", "Translator", "Translate text between 50+ languages", "tool", true),
                    ToolConfig("tool_summarizer", "Summarizer", "Summarize long text into key points", "tool", true),
                    ToolConfig("tool_email_drafter", "Email Drafter", "Draft professional emails", "tool", true),
                    ToolConfig("tool_sql_query", "SQL Query", "Execute SQL queries on databases", "tool", false),
                    ToolConfig("tool_api_tester", "API Tester", "Test HTTP API endpoints", "tool", true),
                    ToolConfig("tool_sentiment_analyzer", "Sentiment Analyzer", "Analyze text sentiment (positive/negative/neutral)", "tool", true),
                    ToolConfig("tool_password_generator", "Password Generator", "Generate secure random passwords", "tool", true),
                    ToolConfig("tool_url_shortener", "URL Shortener", "Shorten long URLs", "tool", false),
                    ToolConfig("tool_qr_generator", "QR Generator", "Generate QR codes from text or URLs", "tool", true),
                    ToolConfig("tool_unit_converter", "Unit Converter", "Convert between units (length, weight, temperature)", "tool", true),
                    ToolConfig("tool_json_formatter", "JSON Formatter", "Format, validate, and prettify JSON", "tool", true),
                    ToolConfig("tool_xml_converter", "XML Converter", "Convert XML to JSON or JSON to XML", "tool", false),
                    ToolConfig("tool_csv_analyzer", "CSV Analyzer", "Analyze CSV data: columns, row count, statistics", "tool", true),
                    ToolConfig("tool_markdown_parser", "Markdown Parser", "Parse Markdown to HTML or extract structure", "tool", true),
                    ToolConfig("tool_regex_tester", "Regex Tester", "Test regex patterns against text", "tool", true),
                    ToolConfig("tool_uuid_generator", "UUID Generator", "Generate UUIDs (v1, v4, v7)", "tool", true),
                    ToolConfig("tool_hash_generator", "Hash Generator", "Generate MD5, SHA1, SHA256, SHA512 hashes", "tool", true),
                    ToolConfig("tool_base64_tool", "Base64 Tool", "Encode or decode Base64 strings", "tool", true),
                    ToolConfig("tool_timestamp_converter", "Timestamp Converter", "Convert Unix timestamps to human-readable dates", "tool", true),
                    ToolConfig("tool_ip_lookup", "IP Lookup", "Lookup IP address geolocation and info", "tool", false),
                    ToolConfig("tool_user_agent_parser", "User-Agent Parser", "Parse User-Agent strings for browser/OS info", "tool", true),
                    ToolConfig("tool_cidr_calculator", "CIDR Calculator", "Calculate CIDR ranges, subnet masks, host counts", "tool", false),
                    ToolConfig("tool_http_header_analyzer", "HTTP Header Analyzer", "Analyze HTTP headers for security and compatibility", "tool", true),
                    ToolConfig("tool_dns_lookup", "DNS Lookup", "Perform DNS lookups (A, AAAA, CNAME, MX, TXT)", "tool", false),
                    ToolConfig("tool_cron_generator", "Cron Generator", "Generate cron expressions from natural language", "tool", true),
                    ToolConfig("tool_jwt_decoder", "JWT Decoder", "Decode JWT tokens and extract claims", "tool", true),
                    ToolConfig("tool_csv_to_json", "CSV to JSON", "Convert CSV data to JSON format", "tool", true),
                    ToolConfig("tool_html_to_markdown", "HTML to Markdown", "Convert HTML content to Markdown", "tool", true),
                    ToolConfig("tool_color_picker", "Color Picker", "Convert colors between HEX, RGB, HSL formats", "tool", true),
                    ToolConfig("tool_web_search", "Web Search", "Search the web via SearXNG, Exa, or Firecrawl", "tool", true),
                    // MCP servers
                    ToolConfig("mcp_filesystem", "Filesystem Server", "Access, read, and write local filesystem safely", "mcp", true, "http://localhost:3011 - Connected"),
                    ToolConfig("mcp_postgres", "PostgreSQL Server", "Query PostgreSQL databases", "mcp", false, "http://localhost:5432 - Disconnected"),
                    ToolConfig("mcp_sqlite", "SQLite Server", "Query SQLite databases", "mcp", false, "http://localhost:3012 - Disconnected"),
                    ToolConfig("mcp_github", "GitHub Server", "Access GitHub repositories and issues", "mcp", false, "https://api.github.com - Disconnected"),
                    ToolConfig("mcp_playwright", "Playwright Server", "Browser automation and web scraping", "mcp", false, "http://localhost:3000 - Disconnected"),
                    ToolConfig("mcp_fetch", "Fetch Server", "Fetch and parse web content", "mcp", true, "http://localhost:3001 - Connected"),
                    // Skills
                    ToolConfig("skill_reviewer", "Code Reviewer", "Code review and quality analysis", "skill", true),
                    ToolConfig("skill_translator", "Translator", "Contextual translation with cultural awareness", "skill", true)
                )

                for (tool in defaultTools) {
                    db.toolDao().insertTool(tool)
                }
            }
        }
    }
}
