package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.data.MessageBlock
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class StreamChunk(
    val textDelta: String = "",
    val reasoningDelta: String = "",
    val toolCallDeltaName: String? = null,
    val toolCallDeltaInput: String? = null,
    val toolCallId: String? = null,
    val isDone: Boolean = false
)

class AgentApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(endpointUrl: String, apiKey: String, protocolFormat: String): List<String> = withContext(Dispatchers.IO) {
        val models = mutableListOf<String>()
        try {
            if (protocolFormat == "gemini") {
                // For Gemini, we test connectivity to standard v1beta models endpoint
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.ifEmpty { BuildConfig.GEMINI_API_KEY }}"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")
                    val bodyString = response.body?.string() ?: ""
                    // Simple parse models
                    val matcher = Pattern.compile("\"name\"\\s*:\\s*\"models/([^\"]+)\"").matcher(bodyString)
                    while (matcher.find()) {
                        val mName = matcher.group(1) ?: ""
                        if (mName.contains("gemini")) {
                            models.add("models/$mName")
                        }
                    }
                    if (models.isEmpty()) {
                        models.addAll(listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.5-flash"))
                    }
                }
            } else {
                // OpenAI-compatible endpoint for models
                val cleanUrl = endpointUrl.removeSuffix("/")
                val url = "$cleanUrl/models"
                val requestBuilder = Request.Builder().url(url).get()
                if (apiKey.isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")
                    val bodyString = response.body?.string() ?: ""
                    val matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(bodyString)
                    while (matcher.find()) {
                        matcher.group(1)?.let { models.add(it) }
                    }
                    if (models.isEmpty()) {
                        models.addAll(listOf("gpt-4o-mini", "gpt-4o", "deepseek-chat", "deepseek-reasoner"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AgentApiClient", "testConnection failed", e)
            // Add default fallback models on error so user is not stuck
            if (protocolFormat == "gemini") {
                models.addAll(listOf("gemini-1.5-flash", "gemini-1.5-pro"))
            } else {
                models.addAll(listOf("gpt-4o-mini", "deepseek-chat"))
            }
            throw e
        }
        return@withContext models.distinct()
    }

    suspend fun streamChat(
        endpointUrl: String,
        apiKey: String,
        protocolFormat: String,
        model: String,
        history: List<Pair<String, String>>, // list of role to content Pair
        temperature: Double,
        maxTokens: Int,
        topP: Double,
        enabledTools: List<String>,
        onChunk: (StreamChunk) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            if (protocolFormat == "gemini") {
                val resolvedApiKey = apiKey.ifEmpty { BuildConfig.GEMINI_API_KEY }
                val mName = if (model.startsWith("models/")) model else "models/$model"
                val url = "https://generativelanguage.googleapis.com/v1beta/$mName:streamGenerateContent?alt=sse&key=$resolvedApiKey"

                val contentsJson = history.map { (role, text) ->
                    val geminiRole = if (role == "user") "user" else "model"
                    """{"role": "$geminiRole", "parts": [{"text": ${escapeJson(text)}}]}"""
                }.joinToString(",")

                // Configure tools if requested
                val toolsBlock = if (enabledTools.isNotEmpty()) {
                    val decls = enabledTools.mapNotNull { tId ->
                        getGeminiToolDeclaration(tId)
                    }.joinToString(",")
                    ""","tools": [{"functionDeclarations": [$decls]}]"""
                } else ""

                val requestBodyString = """
                    {
                        "contents": [$contentsJson],
                        "generationConfig": {
                            "temperature": $temperature,
                            "maxOutputTokens": $maxTokens,
                            "topP": $topP
                        }$toolsBlock
                    }
                """.trimIndent()

                Log.d("AgentApiClient", "Gemini request body: $requestBodyString")

                val request = Request.Builder()
                    .url(url)
                    .post(requestBodyString.toRequestBody(jsonMediaType))
                    .build()

                executeSseStream(request) { line ->
                    parseGeminiSseLine(line, onChunk)
                }

            } else {
                // OpenAI or Hermes Compatible Tool Calling
                val cleanUrl = endpointUrl.removeSuffix("/")
                val url = "$cleanUrl/chat/completions"

                val messagesJson = history.map { (role, text) ->
                    """{"role": "$role", "content": ${escapeJson(text)}}"""
                }.joinToString(",")

                // Build tools JSON if OpenAI compatible and tools are enabled
                val toolsBlock = if (protocolFormat == "openai" && enabledTools.isNotEmpty()) {
                    val decls = enabledTools.mapNotNull { tId ->
                        getOpenAiToolDeclaration(tId)
                    }.joinToString(",")
                    ""","tools": [$decls], "tool_choice": "auto""""
                } else ""

                val requestBodyString = """
                    {
                        "model": "$model",
                        "messages": [$messagesJson],
                        "stream": true,
                        "temperature": $temperature,
                        "max_tokens": $maxTokens,
                        "top_p": $topP
                        $toolsBlock
                    }
                """.trimIndent()

                Log.d("AgentApiClient", "OpenAI request body: $requestBodyString")

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(requestBodyString.toRequestBody(jsonMediaType))

                if (apiKey.isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                executeSseStream(requestBuilder.build()) { line ->
                    parseOpenAiSseLine(line, onChunk)
                }
            }
        } catch (e: Exception) {
            Log.e("AgentApiClient", "Stream failed", e)
            throw e
        }
    }

    private suspend fun executeSseStream(request: Request, lineProcessor: (String) -> Unit) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Server error ${response.code}: $errBody")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val reader = BufferedReader(body.charStream())
            var line: String?
            while (withContext(Dispatchers.IO) { reader.readLine() }.also { line = it } != null) {
                line?.let { lineProcessor(it) }
            }
        }
    }

    private fun parseOpenAiSseLine(line: String, onChunk: (StreamChunk) -> Unit) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        if (!trimmed.startsWith("data:")) return
        val data = trimmed.substring(5).trim()
        if (data == "[DONE]") {
            onChunk(StreamChunk(isDone = true))
            return
        }
        try {
            // High-performance regex based fallback values
            val contentDelta = extractJsonStringValue(data, "content")
            val reasoningDelta = extractJsonStringValue(data, "reasoning_content") ?: extractJsonStringValue(data, "thinking")
            val toolCallName = extractJsonStringValue(data, "name")
            val toolCallArguments = extractJsonStringValue(data, "arguments")
            val toolCallId = extractJsonStringValue(data, "id")

            if (contentDelta != null || reasoningDelta != null || toolCallName != null || toolCallArguments != null) {
                onChunk(
                    StreamChunk(
                        textDelta = contentDelta ?: "",
                        reasoningDelta = reasoningDelta ?: "",
                        toolCallDeltaName = toolCallName,
                        toolCallDeltaInput = toolCallArguments,
                        toolCallId = toolCallId
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("AgentApiClient", "Error parsing SSE chunk", e)
        }
    }

    private fun parseGeminiSseLine(line: String, onChunk: (StreamChunk) -> Unit) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        val data = if (trimmed.startsWith("data:")) trimmed.substring(5).trim() else trimmed
        if (data == "[DONE]") {
            onChunk(StreamChunk(isDone = true))
            return
        }
        try {
            // Parse Gemini response stream content chunk
            val textValue = extractJsonStringValue(data, "text")
            val functionCallName = extractJsonStringValue(data, "name")
            // arguments are typically a nested object in Gemini, so we can extract raw json string or substring
            val functionCallArgs = extractGeminiArguments(data)

            if (textValue != null || functionCallName != null || functionCallArgs != null) {
                onChunk(
                    StreamChunk(
                        textDelta = textValue ?: "",
                        toolCallDeltaName = functionCallName,
                        toolCallDeltaInput = functionCallArgs,
                        toolCallId = if (functionCallName != null) "call_gemini_${System.currentTimeMillis()}" else null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("AgentApiClient", "Error parsing Gemini SSE chunk", e)
        }
    }

    private fun extractJsonStringValue(json: String, key: String): String? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"(([^\"]|\\\\\")*)\"")
        val matcher = pattern.matcher(json)
        if (matcher.find()) {
            val rawValue = matcher.group(1) ?: ""
            return unescapeJsonString(rawValue)
        }
        return null
    }

    private fun extractGeminiArguments(json: String): String? {
        // Look for "args": { ... }
        val index = json.indexOf("\"args\"")
        if (index == -1) return null
        val sub = json.substring(index)
        val startBrace = sub.indexOf("{")
        if (startBrace == -1) return null
        // Balance curly braces to find the end of the JSON object
        var depth = 0
        var endBrace = -1
        for (i in startBrace until sub.length) {
            val char = sub[i]
            if (char == '{') depth++
            else if (char == '}') {
                depth--
                if (depth == 0) {
                    endBrace = i
                    break
                }
            }
        }
        if (endBrace != -1) {
            return sub.substring(startBrace, endBrace + 1)
        }
        return null
    }

    private fun unescapeJsonString(escaped: String): String {
        val sb = java.lang.StringBuilder()
        var i = 0
        val len = escaped.length
        while (i < len) {
            var c = escaped[i]
            if (c == '\\' && i + 1 < len) {
                val next = escaped[i + 1]
                when (next) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < len) {
                            val code = escaped.substring(i + 2, i + 6)
                            try {
                                sb.append(code.toInt(16).toChar())
                                i += 4
                            } catch (e: Exception) {
                                sb.append("\\u").append(code)
                            }
                        } else {
                            sb.append("\\u")
                        }
                    }
                    else -> sb.append(next)
                }
                i++
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (i in str.indices) {
            val c = str[i]
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 32) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // Standard pre-defined JSON tools definitions
    private fun getOpenAiToolDeclaration(toolId: String): String? {
        return when (toolId) {
            "tool_calculator" -> """
                {
                    "type": "function",
                    "function": {
                        "name": "tool_calculator",
                        "description": "Melakukan perhitungan matematika dasar (tambah, kurang, kali, bagi)",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "expression": {
                                    "type": "string",
                                    "description": "Ekspresi matematika yang akan dihitung, contoh: '2 + 2' atau '12 * 5'"
                                }
                            },
                            "required": ["expression"]
                        }
                    }
                }
            """.trimIndent()
            "tool_weather" -> """
                {
                    "type": "function",
                    "function": {
                        "name": "tool_weather",
                        "description": "Mengambil data cuaca real-time untuk kota tertentu",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "city": {
                                    "type": "string",
                                    "description": "Nama kota, contoh: 'Jakarta', 'Bandung', 'Surabaya'"
                                }
                            },
                            "required": ["city"]
                        }
                    }
                }
            """.trimIndent()
            "tool_search" -> """
                {
                    "type": "function",
                    "function": {
                        "name": "tool_search",
                        "description": "Mencari informasi terbaru di internet melalui Google Search",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "query": {
                                    "type": "string",
                                    "description": "Query pencarian informasi di internet"
                                }
                            },
                            "required": ["query"]
                        }
                    }
                }
            """.trimIndent()
            "mcp_filesystem" -> """
                {
                    "type": "function",
                    "function": {
                        "name": "mcp_filesystem",
                        "description": "Mengakses, membaca, dan menulis file system lokal secara aman",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "path": { "type": "string", "description": "Path file yang akan diakses" },
                                "action": { "type": "string", "enum": ["read", "write", "list"], "description": "Aksi filesystem" },
                                "content": { "type": "string", "description": "Isi file jika melakukan aksi write" }
                            },
                            "required": ["path", "action"]
                        }
                    }
                }
            """.trimIndent()
            "skill_reviewer" -> """
                {
                    "type": "function",
                    "function": {
                        "name": "skill_reviewer",
                        "description": "Mengevaluasi dan mereview implementasi kode Kotlin dan Jetpack Compose",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "code": { "type": "string", "description": "Kode Kotlin/Compose yang akan dianalisis" }
                            },
                            "required": ["code"]
                        }
                    }
                }
            """.trimIndent()
            "skill_translator" -> """
                {
                    "type": "function",
                    "function": {
                        "name": "skill_translator",
                        "description": "Menerjemahkan teks antar bahasa asing secara akurat",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "text": { "type": "string", "description": "Teks sumber" },
                                "target_lang": { "type": "string", "description": "Bahasa target (contoh: English, Indonesian, Japanese)" }
                            },
                            "required": ["text", "target_lang"]
                        }
                    }
                }
            """.trimIndent()
            else -> null
        }
    }

    private fun getGeminiToolDeclaration(toolId: String): String? {
        return when (toolId) {
            "tool_calculator" -> """
                {
                    "name": "tool_calculator",
                    "description": "Melakukan perhitungan matematika dasar (tambah, kurang, kali, bagi)",
                    "parameters": {
                        "type": "OBJECT",
                        "properties": {
                            "expression": {
                                "type": "STRING",
                                "description": "Ekspresi matematika yang akan dihitung, contoh: '2 + 2' atau '12 * 5'"
                            }
                        },
                        "required": ["expression"]
                    }
                }
            """.trimIndent()
            "tool_weather" -> """
                {
                    "name": "tool_weather",
                    "description": "Mengambil data cuaca real-time untuk kota tertentu",
                    "parameters": {
                        "type": "OBJECT",
                        "properties": {
                            "city": {
                                "type": "STRING",
                                "description": "Nama kota, contoh: 'Jakarta', 'Bandung', 'Surabaya'"
                            }
                        },
                        "required": ["city"]
                    }
                }
            """.trimIndent()
            "tool_search" -> """
                {
                    "name": "tool_search",
                    "description": "Mencari informasi terbaru di internet melalui Google Search",
                    "parameters": {
                        "type": "OBJECT",
                        "properties": {
                            "query": {
                                "type": "STRING",
                                "description": "Query pencarian informasi di internet"
                            }
                        },
                        "required": ["query"]
                    }
                }
            """.trimIndent()
            "mcp_filesystem" -> """
                {
                    "name": "mcp_filesystem",
                    "description": "Mengakses, membaca, dan menulis file system lokal secara aman",
                    "parameters": {
                        "type": "OBJECT",
                        "properties": {
                            "path": { "type": "STRING", "description": "Path file yang akan diakses" },
                            "action": { "type": "STRING", "description": "Aksi filesystem: 'read', 'write', 'list'" },
                            "content": { "type": "STRING", "description": "Isi file jika melakukan aksi write" }
                        },
                        "required": ["path", "action"]
                    }
                }
            """.trimIndent()
            "skill_reviewer" -> """
                {
                    "name": "skill_reviewer",
                    "description": "Mengevaluasi dan mereview implementasi kode Kotlin dan Jetpack Compose",
                    "parameters": {
                        "type": "OBJECT",
                        "properties": {
                            "code": { "type": "STRING", "description": "Kode Kotlin/Compose yang akan dianalisis" }
                        },
                        "required": ["code"]
                    }
                }
            """.trimIndent()
            "skill_translator" -> """
                {
                    "name": "skill_translator",
                    "description": "Menerjemahkan teks antar bahasa asing secara akurat",
                    "parameters": {
                        "type": "OBJECT",
                        "properties": {
                            "text": { "type": "STRING", "description": "Teks sumber" },
                            "target_lang": { "type": "STRING", "description": "Bahasa target (contoh: English, Indonesian, Japanese)" }
                        },
                        "required": ["text", "target_lang"]
                    }
                }
            """.trimIndent()
            else -> null
        }
    }
}
