package com.example.network

import android.util.Log
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
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

// WebSearch data models
@JsonClass(generateAdapter = true)
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

@JsonClass(generateAdapter = true)
data class WebSearchResponse(
    val results: List<WebSearchResult>,
    val query: String
)

@JsonClass(generateAdapter = true)
data class StreamChunk(
    @Json(name = "isDone")
    val isDone: Boolean = false,

    @Json(name = "textDelta")
    val textDelta: String = "",

    @Json(name = "reasoningDelta")
    val reasoningDelta: String = "",

    @Json(name = "toolCallDeltaName")
    val toolCallDeltaName: String? = null,

    @Json(name = "toolCallDeltaInput")
    val toolCallDeltaInput: String? = null,

    @Json(name = "toolCallId")
    val toolCallId: String? = null
)

class AgentApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // WebSearch methods
    suspend fun searchSearXNG(endpointUrl: String, apiKey: String?, query: String): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val url = "$endpointUrl/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json"
        val requestBuilder = Request.Builder().url(url)
        if (!apiKey.isNullOrEmpty()) requestBuilder.addHeader("Authorization", apiKey)
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: return@use emptyList()
            parseSearXNGResults(body)
        }
    }

    suspend fun searchExa(endpointUrl: String, apiKey: String, query: String): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val url = "$endpointUrl/search"
        val body = """{"query": "${escapeJson(query)}", "num_results": 10}""".trimIndent()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("x-api-key", apiKey)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val respBody = response.body?.string() ?: return@use emptyList()
            parseExaResults(respBody)
        }
    }

    suspend fun searchFirecrawl(endpointUrl: String, apiKey: String, query: String): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val url = "$endpointUrl/search"
        val body = """{"query": "${escapeJson(query)}", "limit": 10}""".trimIndent()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val respBody = response.body?.string() ?: return@use emptyList()
            parseFirecrawlResults(respBody)
        }
    }

    // Connection test
    suspend fun testConnection(endpointUrl: String, apiKey: String, protocolFormat: String): List<String> = withContext(Dispatchers.IO) {
        val models = mutableListOf<String>()
        try {
            if (protocolFormat == "gemini") {
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.ifEmpty { "" }}"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")
                    val bodyString = response.body?.string() ?: ""
                    val matcher = Pattern.compile("\"name\"\\s*:\\s*\"models/([^\"]+)\"").matcher(bodyString)
                    while (matcher.find()) {
                        matcher.group(1)?.let { if (it.contains("gemini")) models.add("models/$it") }
                    }
                    if (models.isEmpty()) models.addAll(listOf("gemini-1.5-flash", "gemini-1.5-pro"))
                }
            } else {
                val cleanUrl = endpointUrl.removeSuffix("/")
                val url = "$cleanUrl/models"
                val requestBuilder = Request.Builder().url(url).get()
                if (apiKey.isNotEmpty()) requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")
                    val bodyString = response.body?.string() ?: ""
                    val matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(bodyString)
                    while (matcher.find()) { matcher.group(1)?.let { models.add(it) } }
                    if (models.isEmpty()) models.addAll(listOf("gpt-4o-mini", "gpt-4o", "llama3.2"))
                }
            }
        } catch (e: Exception) {
            Log.e("AgentApiClient", "testConnection failed", e)
            if (protocolFormat == "gemini") models.addAll(listOf("gemini-1.5-flash", "gemini-1.5-pro"))
            else models.addAll(listOf("gpt-4o-mini", "llama3.2"))
            throw e
        }
        return@withContext models.distinct()
    }

    // Streaming chat
    suspend fun streamChat(
        endpointUrl: String, apiKey: String, protocolFormat: String, model: String,
        history: List<Pair<String, String>>,
        temperature: Double, maxTokens: Int, topP: Double,
        enabledTools: List<String>,
        onChunk: (StreamChunk) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            if (protocolFormat == "gemini") {
                val resolvedApiKey = apiKey.ifEmpty { "" }
                val mName = if (model.startsWith("models/")) model else "models/$model"
                val url = "https://generativelanguage.googleapis.com/v1beta/$mName:streamGenerateContent?alt=sse&key=$resolvedApiKey"
                val contentsJson = history.map { (role, text) ->
                    val geminiRole = if (role == "user") "user" else "model"
                    """{"role": "$geminiRole", "parts": [{"text": ${escapeJson(text)}}]}"""
                }.joinToString(",")
                val toolsBlock = if (enabledTools.isNotEmpty()) {
                    val decls = enabledTools.mapNotNull { tId -> getGeminiToolDeclaration(tId) }.joinToString(",")
                    ""","tools": [{"functionDeclarations": [$decls]}]"""
                } else ""
                val requestBodyString = """{"contents": [$contentsJson], "generationConfig": {"temperature": $temperature, "maxOutputTokens": $maxTokens, "topP": $topP}$toolsBlock}""".trimIndent()
                val request = Request.Builder().url(url).post(requestBodyString.toRequestBody(jsonMediaType)).build()
                executeSseStream(request) { line -> parseGeminiSseLine(line, onChunk) }
            } else {
                val cleanUrl = endpointUrl.removeSuffix("/")
                val url = "$cleanUrl/chat/completions"
                val messagesJson = history.map { (role, text) ->
                    """{"role": "$role", "content": ${escapeJson(text)}}"""
                }.joinToString(",")
                val toolsBlock = if (enabledTools.isNotEmpty()) {
                    val decls = enabledTools.mapNotNull { tId -> getOpenAiToolDeclaration(tId) }.joinToString(",")
                    ""","tools": [$decls], "tool_choice": "auto""""
                } else ""
                val requestBodyString = """{"model": "$model", "messages": [$messagesJson], "stream": true, "temperature": $temperature, "max_tokens": $maxTokens, "top_p": $topP $toolsBlock}""".trimIndent()
                val requestBuilder = Request.Builder().url(url).post(requestBodyString.toRequestBody(jsonMediaType))
                if (apiKey.isNotEmpty()) requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                executeSseStream(requestBuilder.build()) { line -> parseOpenAiSseLine(line, onChunk) }
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

    // HTML stripping helpers
    private fun stripHtml(html: String): String {
        var result = html
        result = result.replace(Regex("<[^>]+>"), " ")
        result = result.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        result = result.replace("  +", " ").trim()
        return result
    }

    private fun parseSearXNGResults(body: String): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        try {
            val json = moshi.adapter<Map<String, Any>>(Map::class.java, emptySet()).fromJson(body) ?: return results
            val jsonResults = json["results"] as? List<Map<String, Any>> ?: return results
            for (r in jsonResults) {
                results.add(WebSearchResult(r["title"] as? String ?: "",
                    r["url"] as? String ?: "",
                    stripHtml(r["content"] as? String ?: r["snippet"] as? String ?: "")
                ))
            }
        } catch (e: Exception) { Log.e("AgentApiClient", "SearXNG parse error", e) }
        return results
    }

    private fun parseExaResults(body: String): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        try {
            // Exa returns: {"requestId":"...","data":[{"title":"...","url":"...","publishedDate":"...","text":"..."}]}
            val json = moshi.adapter<Map<String, Any>>(Map::class.java, emptySet()).fromJson(body) ?: return results
            val data = json["data"] as? List<Map<String, Any>> ?: return results
            for (item in data) {
                results.add(WebSearchResult(
                    item["title"] as? String ?: "",
                    item["url"] as? String ?: "",
                    stripHtml(item["text"] as? String ?: item["snippet"] as? String ?: "")
                ))
            }
        } catch (e: Exception) {
            // Try flat array format
            try {
                val json = moshi.adapter<List<Map<String, Any>>>(object : com.squareup.moshi.reflect.TypeToken<List<Map<String, Any>>>() {}.type).fromJson(body)
                json?.forEach { item ->
                    results.add(WebSearchResult(
                        item["title"] as? String ?: item["name"] as? String ?: "",
                        item["url"] as? String ?: item["link"] as? String ?: "",
                        stripHtml(item["text"] as? String ?: item["desc"] as? String ?: item["snippet"] as? String ?: "")
                    ))
                }
            } catch (e2: Exception) { Log.e("AgentApiClient", "Exa parse error", e2) }
        }
        return results
    }

    private fun parseFirecrawlResults(body: String): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        try {
            val json = moshi.adapter<Map<String, Any>>(Map::class.java, emptySet()).fromJson(body) ?: return results
            val data = json["data"] as? List<Map<String, Any>> ?: json["results"] as? List<Map<String, Any>> ?: return results
            for (item in data) {
                results.add(WebSearchResult(
                    item["title"] as? String ?: item["name"] as? String ?: "",
                    item["url"] as? String ?: "",
                    stripHtml(item["description"] as? String ?: item["text"] as? String ?: "")
                ))
            }
        } catch (e: Exception) { Log.e("AgentApiClient", "Firecrawl parse error", e) }
        return results
    }

    // SSE Parsing
    private fun parseOpenAiSseLine(line: String, onChunk: (StreamChunk) -> Unit) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        if (!trimmed.startsWith("data:")) return
        val data = trimmed.substring(5).trim()
        if (data == "[DONE]") { onChunk(StreamChunk(isDone = true)); return }
        try {
            val contentDelta = extractJsonStringValue(data, "content")
            val reasoningDelta = extractJsonStringValue(data, "reasoning_content") ?: extractJsonStringValue(data, "thinking")
            val toolCallName = extractJsonStringValue(data, "name")
            val toolCallArguments = extractJsonStringValue(data, "arguments")
            val toolCallId = extractJsonStringValue(data, "id")
            if (contentDelta != null || reasoningDelta != null || toolCallName != null || toolCallArguments != null) {
                onChunk(StreamChunk(textDelta = contentDelta ?: "", reasoningDelta = reasoningDelta ?: "",
                    toolCallDeltaName = toolCallName, toolCallDeltaInput = toolCallArguments, toolCallId = toolCallId))
            }
        } catch (e: Exception) { Log.e("AgentApiClient", "Error parsing SSE chunk", e) }
    }

    private fun parseGeminiSseLine(line: String, onChunk: (StreamChunk) -> Unit) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        val data = if (trimmed.startsWith("data:")) trimmed.substring(5).trim() else trimmed
        if (data == "[DONE]") { onChunk(StreamChunk(isDone = true)); return }
        try {
            val textValue = extractJsonStringValue(data, "text")
            val functionCallName = extractJsonStringValue(data, "name")
            val functionCallArgs = extractGeminiArguments(data)
            if (textValue != null || functionCallName != null || functionCallArgs != null) {
                onChunk(StreamChunk(
                    textDelta = textValue ?: "", toolCallDeltaName = functionCallName,
                    toolCallDeltaInput = functionCallArgs,
                    toolCallId = if (functionCallName != null) "call_gemini_${System.currentTimeMillis()}" else null))
            }
        } catch (e: Exception) { Log.e("AgentApiClient", "Error parsing Gemini SSE chunk", e) }
    }

    private fun extractJsonStringValue(json: String, key: String): String? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"(([^\"]|\\\\\")*)\"")
        val matcher = pattern.matcher(json)
        if (matcher.find()) { val rawValue = matcher.group(1) ?: ""; return unescapeJsonString(rawValue) }
        // Also try number/bool
        val numPattern = Pattern.compile("\"$key\"\\s*:\\s*([0-9.]+)")
        val numMatcher = numPattern.matcher(json)
        if (numMatcher.find()) return numMatcher.group(1)
        val boolPattern = Pattern.compile("\"$key\"\\s*:\\s*(true|false)")
        val boolMatcher = boolPattern.matcher(json)
        if (boolMatcher.find()) return boolMatcher.group(1)
        return null
    }

    private fun extractGeminiArguments(json: String): String? {
        val index = json.indexOf("\"args\"")
        if (index == -1) return null
        val sub = json.substring(index)
        val startBrace = sub.indexOf("{")
        if (startBrace == -1) return null
        var depth = 0; var endBrace = -1
        for (i in startBrace until sub.length) {
            when (val c = sub[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { endBrace = i; break } }
            }
        }
        return if (endBrace != -1) sub.substring(startBrace, endBrace + 1) else null
    }

    private fun unescapeJsonString(escaped: String): String {
        val sb = StringBuilder()
        var i = 0; val len = escaped.length
        while (i < len) {
            var c = escaped[i]
            if (c == '\\' && i + 1 < len) {
                when (val next = escaped[i + 1]) {
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                    'b' -> sb.append('\b'); 'f' -> sb.append('')
                    '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                    'u' -> { if (i + 5 < len) { try { sb.append(escaped.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 } catch (e: Exception) { sb.append("\\u") } } else sb.append("\\u") }
                    else -> sb.append(next)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        val sb = StringBuilder().append('"')
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\"); '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
                else -> if (c.code < 32) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    // ==================== TOOL DECLARATIONS (40+ tools) ====================

    private fun getOpenAiToolDeclaration(toolId: String): String? {
        return when (toolId) {
            "tool_calculator" -> """
                {"type":"function","function":{"name":"tool_calculator","description":"Evaluate mathematical expressions","parameters":{"type":"object","properties":{"expression":{"type":"string","description":"Math expression"}},"required":["expression"]}}}
            """.trimIndent()
            "tool_weather" -> """
                {"type":"function","function":{"name":"tool_weather","description":"Get real-time weather data for a city","parameters":{"type":"object","properties":{"city":{"type":"string","description":"City name"}},"required":["city"]}}}
            """.trimIndent()
            "tool_code_executor" -> """
                {"type":"function","function":{"name":"tool_code_executor","description":"Execute code snippets and return output","parameters":{"type":"object","properties":{"code":{"type":"string","description":"Code to execute"},"language":{"type":"string","description":"Programming language"}},"required":["code"]}}}
            """.trimIndent()
            "tool_translator" -> """
                {"type":"function","function":{"name":"tool_translator","description":"Translate text between languages","parameters":{"type":"object","properties":{"text":{"type":"string","description":"Text to translate"},"target_language":{"type":"string","description":"Target language"}},"required":["text","target_language"]}}}
            """.trimIndent()
            "tool_summarizer" -> """
                {"type":"function","function":{"name":"tool_summarizer","description":"Summarize long text into key points","parameters":{"type":"object","properties":{"text":{"type":"string","description":"Text to summarize"}},"required":["text"]}}}
            """.trimIndent()
            "tool_email_drafter" -> """
                {"type":"function","function":{"name":"tool_email_drafter","description":"Draft professional emails","parameters":{"type":"object","properties":{"subject":{"type":"string"},"recipient":{"type":"string"},"content":{"type":"string"}},"required":["subject","recipient"]}}}
            """.trimIndent()
            "tool_sql_query" -> """
                {"type":"function","function":{"name":"tool_sql_query","description":"Execute SQL query","parameters":{"type":"object","properties":{"query":{"type":"string","description":"SQL query"}},"required":["query"]}}}
            """.trimIndent()
            "tool_api_tester" -> """
                {"type":"function","function":{"name":"tool_api_tester","description":"Test HTTP API endpoints","parameters":{"type":"object","properties":{"url":{"type":"string"},"method":{"type":"string"},"headers":{"type":"object"}},"required":["url","method"]}}}
            """.trimIndent()
            "tool_sentiment_analyzer" -> """
                {"type":"function","function":{"name":"tool_sentiment_analyzer","description":"Analyze text sentiment","parameters":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}}}
            """.trimIndent()
            "tool_password_generator" -> """
                {"type":"function","function":{"name":"tool_password_generator","description":"Generate secure passwords","parameters":{"type":"object","properties":{"length":{"type":"integer"},"include_special":{"type":"boolean"}},"required":["length"]}}}
            """.trimIndent()
            "tool_url_shortener" -> """
                {"type":"function","function":{"name":"tool_url_shortener","description":"Shorten long URLs","parameters":{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}}}
            """.trimIndent()
            "tool_qr_generator" -> """
                {"type":"function","function":{"name":"tool_qr_generator","description":"Generate QR code","parameters":{"type":"object","properties":{"data":{"type":"string"}},"required":["data"]}}}
            """.trimIndent()
            "tool_unit_converter" -> """
                {"type":"function","function":{"name":"tool_unit_converter","description":"Convert between units","parameters":{"type":"object","properties":{"value":{"type":"number"},"from_unit":{"type":"string"},"to_unit":{"type":"string"}},"required":["value","from_unit","to_unit"]}}}
            """.trimIndent()
            "tool_json_formatter" -> """
                {"type":"function","function":{"name":"tool_json_formatter","description":"Format, validate, prettify JSON","parameters":{"type":"object","properties":{"json":{"type":"string"},"action":{"type":"string"}},"required":["json"]}}}
            """.trimIndent()
            "tool_xml_converter" -> """
                {"type":"function","function":{"name":"tool_xml_converter","description":"Convert XML to JSON or JSON to XML","parameters":{"type":"object","properties":{"data":{"type":"string"},"from":{"type":"string"},"to":{"type":"string"}},"required":["data"]}}}
            """.trimIndent()
            "tool_csv_analyzer" -> """
                {"type":"function","function":{"name":"tool_csv_analyzer","description":"Analyze CSV data","parameters":{"type":"object","properties":{"csv":{"type":"string"},"action":{"type":"string"}},"required":["csv"]}}}
            """.trimIndent()
            "tool_markdown_parser" -> """
                {"type":"function","function":{"name":"tool_markdown_parser","description":"Parse Markdown to HTML","parameters":{"type":"object","properties":{"markdown":{"type":"string"},"output":{"type":"string"}},"required":["markdown"]}}}
            """.trimIndent()
            "tool_regex_tester" -> """
                {"type":"function","function":{"name":"tool_regex_tester","description":"Test regex patterns","parameters":{"type":"object","properties":{"pattern":{"type":"string"},"text":{"type":"string"}},"required":["pattern","text"]}}}
            """.trimIndent()
            "tool_uuid_generator" -> """
                {"type":"function","function":{"name":"tool_uuid_generator","description":"Generate UUIDs","parameters":{"type":"object","properties":{"version":{"type":"string","enum":["v1","v4","v7"]}},"required":[]}}}
            """.trimIndent()
            "tool_hash_generator" -> """
                {"type":"function","function":{"name":"tool_hash_generator","description":"Generate hash (MD5, SHA1, SHA256, SHA512)","parameters":{"type":"object","properties":{"text":{"type":"string"},"algorithm":{"type":"string"}},"required":["text"]}}}
            """.trimIndent()
            "tool_base64_tool" -> """
                {"type":"function","function":{"name":"tool_base64_tool","description":"Encode/decode Base64","parameters":{"type":"object","properties":{"data":{"type":"string"},"action":{"type":"string"}},"required":["data","action"]}}}
            """.trimIndent()
            "tool_timestamp_converter" -> """
                {"type":"function","function":{"name":"tool_timestamp_converter","description":"Convert timestamps","parameters":{"type":"object","properties":{"value":{"type":"string"},"from":{"type":"string"},"to":{"type":"string"}},"required":["value"]}}}
            """.trimIndent()
            "tool_ip_lookup" -> """
                {"type":"function","function":{"name":"tool_ip_lookup","description":"IP geolocation lookup","parameters":{"type":"object","properties":{"ip":{"type":"string"}},"required":["ip"]}}}
            """.trimIndent()
            "tool_user_agent_parser" -> """
                {"type":"function","function":{"name":"tool_user_agent_parser","description":"Parse User-Agent string","parameters":{"type":"object","properties":{"user_agent":{"type":"string"}},"required":["user_agent"]}}}
            """.trimIndent()
            "tool_cidr_calculator" -> """
                {"type":"function","function":{"name":"tool_cidr_calculator","description":"Calculate CIDR ranges","parameters":{"type":"object","properties":{"cidr":{"type":"string"},"action":{"type":"string"}},"required":["cidr"]}}}
            """.trimIndent()
            "tool_http_header_analyzer" -> """
                {"type":"function","function":{"name":"tool_http_header_analyzer","description":"Analyze HTTP headers","parameters":{"type":"object","properties":{"headers":{"type":"object"},"url":{"type":"string"}},"required":["headers"]}}}
            """.trimIndent()
            "tool_dns_lookup" -> """
                {"type":"function","function":{"name":"tool_dns_lookup","description":"DNS lookup","parameters":{"type":"object","properties":{"domain":{"type":"string"},"record_type":{"type":"string"}},"required":["domain"]}}}
            """.trimIndent()
            "tool_cron_generator" -> """
                {"type":"function","function":{"name":"tool_cron_generator","description":"Generate cron expressions","parameters":{"type":"object","properties":{"description":{"type":"string"}},"required":["description"]}}}
            """.trimIndent()
            "tool_jwt_decoder" -> """
                {"type":"function","function":{"name":"tool_jwt_decoder","description":"Decode JWT tokens","parameters":{"type":"object","properties":{"token":{"type":"string"}},"required":["token"]}}}
            """.trimIndent()
            "tool_csv_to_json" -> """
                {"type":"function","function":{"name":"tool_csv_to_json","description":"CSV to JSON conversion","parameters":{"type":"object","properties":{"csv":{"type":"string"}},"required":["csv"]}}}
            """.trimIndent()
            "tool_html_to_markdown" -> """
                {"type":"function","function":{"name":"tool_html_to_markdown","description":"HTML to Markdown conversion","parameters":{"type":"object","properties":{"html":{"type":"string"}},"required":["html"]}}}
            """.trimIndent()
            "tool_color_picker" -> """
                {"type":"function","function":{"name":"tool_color_picker","description":"Convert colors between formats","parameters":{"type":"object","properties":{"color":{"type":"string"},"from":{"type":"string"},"to":{"type":"string"}},"required":["color"]}}}
            """.trimIndent()
            "tool_web_search" -> """
                {"type":"function","function":{"name":"tool_web_search","description":"Search the web for real-time information","parameters":{"type":"object","properties":{"query":{"type":"string","description":"Search query"}},"required":["query"]}}}
            """.trimIndent()
            else -> null
        }
    }

    private fun getGeminiToolDeclaration(toolId: String): String? {
        return when (toolId) {
            "tool_calculator" -> """
                {"name":"tool_calculator","description":"Evaluate mathematical expressions","parameters":{"type":"OBJECT","properties":{"expression":{"type":"STRING","description":"Math expression"}},"required":["expression"]}}
            """.trimIndent()
            "tool_weather" -> """
                {"name":"tool_weather","description":"Get weather data","parameters":{"type":"OBJECT","properties":{"city":{"type":"STRING","description":"City name"}},"required":["city"]}}
            """.trimIndent()
            "tool_code_executor" -> """
                {"name":"tool_code_executor","description":"Execute code snippets","parameters":{"type":"OBJECT","properties":{"code":{"type":"STRING"},"language":{"type":"STRING"}},"required":["code"]}}
            """.trimIndent()
            "tool_translator" -> """
                {"name":"tool_translator","description":"Translate text","parameters":{"type":"OBJECT","properties":{"text":{"type":"STRING"},"target_lang":{"type":"STRING"}},"required":["text","target_lang"]}}
            """.trimIndent()
            "tool_summarizer" -> """
                {"name":"tool_summarizer","description":"Summarize text","parameters":{"type":"OBJECT","properties":{"text":{"type":"STRING"}},"required":["text"]}}
            """.trimIndent()
            "tool_email_drafter" -> """
                {"name":"tool_email_drafter","description":"Draft emails","parameters":{"type":"OBJECT","properties":{"subject":{"type":"STRING"},"content":{"type":"STRING"}},"required":["subject","content"]}}
            """.trimIndent()
            "tool_sql_query" -> """
                {"name":"tool_sql_query","description":"Execute SQL queries","parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING"}},"required":["query"]}}
            """.trimIndent()
            "tool_api_tester" -> """
                {"name":"tool_api_tester","description":"Test HTTP APIs","parameters":{"type":"OBJECT","properties":{"url":{"type":"STRING"},"method":{"type":"STRING"}},"required":["url","method"]}}
            """.trimIndent()
            "tool_sentiment_analyzer" -> """
                {"name":"tool_sentiment_analyzer","description":"Analyze sentiment","parameters":{"type":"OBJECT","properties":{"text":{"type":"STRING"}},"required":["text"]}}
            """.trimIndent()
            "tool_password_generator" -> """
                {"name":"tool_password_generator","description":"Generate passwords","parameters":{"type":"OBJECT","properties":{"length":{"type":"NUMBER"}},"required":["length"]}}
            """.trimIndent()
            "tool_url_shortener" -> """
                {"name":"tool_url_shortener","description":"Shorten URLs","parameters":{"type":"OBJECT","properties":{"url":{"type":"STRING"}},"required":["url"]}}
            """.trimIndent()
            "tool_qr_generator" -> """
                {"name":"tool_qr_generator","description":"Generate QR codes","parameters":{"type":"OBJECT","properties":{"data":{"type":"STRING"}},"required":["data"]}}
            """.trimIndent()
            "tool_unit_converter" -> """
                {"name":"tool_unit_converter","description":"Convert units","parameters":{"type":"OBJECT","properties":{"value":{"type":"NUMBER"},"from_unit":{"type":"STRING"},"to_unit":{"type":"STRING"}},"required":["value","from_unit","to_unit"]}}
            """.trimIndent()
            "tool_json_formatter" -> """
                {"name":"tool_json_formatter","description":"Format JSON","parameters":{"type":"OBJECT","properties":{"json":{"type":"STRING"}},"required":["json"]}}
            """.trimIndent()
            "tool_xml_converter" -> """
                {"name":"tool_xml_converter","description":"Convert XML/JSON","parameters":{"type":"OBJECT","properties":{"data":{"type":"STRING"},"from":{"type":"STRING"},"to":{"type":"STRING"}},"required":["data"]}}
            """.trimIndent()
            "tool_csv_analyzer" -> """
                {"name":"tool_csv_analyzer","description":"Analyze CSV","parameters":{"type":"OBJECT","properties":{"csv":{"type":"STRING"}},"required":["csv"]}}
            """.trimIndent()
            "tool_markdown_parser" -> """
                {"name":"tool_markdown_parser","description":"Parse Markdown","parameters":{"type":"OBJECT","properties":{"markdown":{"type":"STRING"}},"required":["markdown"]}}
            """.trimIndent()
            "tool_regex_tester" -> """
                {"name":"tool_regex_tester","description":"Test regex","parameters":{"type":"OBJECT","properties":{"pattern":{"type":"STRING"},"text":{"type":"STRING"}},"required":["pattern","text"]}}
            """.trimIndent()
            "tool_uuid_generator" -> """
                {"name":"tool_uuid_generator","description":"Generate UUIDs","parameters":{"type":"OBJECT","properties":{},"required":[]}}
            """.trimIndent()
            "tool_hash_generator" -> """
                {"name":"tool_hash_generator","description":"Generate hashes","parameters":{"type":"OBJECT","properties":{"text":{"type":"STRING"},"algorithm":{"type":"STRING"}},"required":["text","algorithm"]}}
            """.trimIndent()
            "tool_base64_tool" -> """
                {"name":"tool_base64_tool","description":"Encode/decode Base64","parameters":{"type":"OBJECT","properties":{"data":{"type":"STRING"},"action":{"type":"STRING"}},"required":["data","action"]}}
            """.trimIndent()
            "tool_timestamp_converter" -> """
                {"name":"tool_timestamp_converter","description":"Convert timestamps","parameters":{"type":"OBJECT","properties":{"value":{"type":"STRING"}},"required":["value"]}}
            """.trimIndent()
            "tool_ip_lookup" -> """
                {"name":"tool_ip_lookup","description":"IP lookup","parameters":{"type":"OBJECT","properties":{"ip":{"type":"STRING"}},"required":["ip"]}}
            """.trimIndent()
            "tool_user_agent_parser" -> """
                {"name":"tool_user_agent_parser","description":"Parse User-Agent","parameters":{"type":"OBJECT","properties":{"user_agent":{"type":"STRING"}},"required":["user_agent"]}}
            """.trimIndent()
            "tool_cidr_calculator" -> """
                {"name":"tool_cidr_calculator","description":"CIDR calculator","parameters":{"type":"OBJECT","properties":{"cidr":{"type":"STRING"}},"required":["cidr"]}}
            """.trimIndent()
            "tool_http_header_analyzer" -> """
                {"name":"tool_http_header_analyzer","description":"Analyze HTTP headers","parameters":{"type":"OBJECT","properties":{"headers":{"type":"OBJECT"}},"required":["headers"]}}
            """.trimIndent()
            "tool_dns_lookup" -> """
                {"name":"tool_dns_lookup","description":"DNS lookup","parameters":{"type":"OBJECT","properties":{"domain":{"type":"STRING"}},"required":["domain"]}}
            """.trimIndent()
            "tool_cron_generator" -> """
                {"name":"tool_cron_generator","description":"Generate cron expressions","parameters":{"type":"OBJECT","properties":{"description":{"type":"STRING"}},"required":["description"]}}
            """.trimIndent()
            "tool_jwt_decoder" -> """
                {"name":"tool_jwt_decoder","description":"Decode JWT tokens","parameters":{"type":"OBJECT","properties":{"token":{"type":"STRING"}},"required":["token"]}}
            """.trimIndent()
            "tool_csv_to_json" -> """
                {"name":"tool_csv_to_json","description":"CSV to JSON","parameters":{"type":"OBJECT","properties":{"csv":{"type":"STRING"}},"required":["csv"]}}
            """.trimIndent()
            "tool_html_to_markdown" -> """
                {"name":"tool_html_to_markdown","description":"HTML to Markdown","parameters":{"type":"OBJECT","properties":{"html":{"type":"STRING"}},"required":["html"]}}
            """.trimIndent()
            "tool_color_picker" -> """
                {"name":"tool_color_picker","description":"Convert colors","parameters":{"type":"OBJECT","properties":{"color":{"type":"STRING"},"from":{"type":"STRING"},"to":{"type":"STRING"}},"required":["color"]}}
            """.trimIndent()
            "tool_web_search" -> """
                {"name":"tool_web_search","description":"Search the web for real-time information","parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING"},"provider":{"type":"STRING"}},"required":["query"]}}
            """.trimIndent()
            else -> null
        }
    }
}
