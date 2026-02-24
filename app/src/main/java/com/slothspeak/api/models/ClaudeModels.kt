package com.slothspeak.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClaudeRequest(
    val model: String,
    @Json(name = "max_tokens")
    val maxTokens: Int,
    val system: String? = null,
    val messages: List<ClaudeMessage>,
    val thinking: ClaudeThinking? = null,
    @Json(name = "output_config")
    val outputConfig: ClaudeOutputConfig? = null,
    val tools: List<ClaudeWebSearchTool>? = null
)

@JsonClass(generateAdapter = true)
data class ClaudeWebSearchTool(
    val type: String = "web_search_20250305",
    val name: String = "web_search"
)

@JsonClass(generateAdapter = true)
data class ClaudeMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class ClaudeThinking(
    val type: String
)

@JsonClass(generateAdapter = true)
data class ClaudeOutputConfig(
    val effort: String
)

@JsonClass(generateAdapter = true)
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String? = null,
    val content: List<ClaudeContentBlock>? = null,
    @Json(name = "stop_reason")
    val stopReason: String? = null,
    val usage: ClaudeUsage? = null
)

@JsonClass(generateAdapter = true)
data class ClaudeContentBlock(
    val type: String,
    val text: String? = null,
    val thinking: String? = null
)

@JsonClass(generateAdapter = true)
data class ClaudeUsage(
    @Json(name = "input_tokens")
    val inputTokens: Int? = null,
    @Json(name = "output_tokens")
    val outputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class ClaudeErrorResponse(
    val type: String? = null,
    val error: ClaudeErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class ClaudeErrorDetail(
    val type: String? = null,
    val message: String? = null
)
