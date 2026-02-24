package com.slothspeak.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "system_instruction")
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    @Json(name = "generationConfig")
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String,
    val thought: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "thinkingConfig")
    val thinkingConfig: GeminiThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiThinkingConfig(
    @Json(name = "thinkingLevel")
    val thinkingLevel: String
)

@JsonClass(generateAdapter = true)
data class GeminiTool(
    @Json(name = "google_search")
    val googleSearch: GeminiGoogleSearch? = null
)

@JsonClass(generateAdapter = true)
class GeminiGoogleSearch

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorResponse(
    val error: GeminiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

// Gemini Interactions API (Deep Research)
@JsonClass(generateAdapter = true)
data class GeminiInteractionRequest(
    val input: String,
    val agent: String,
    val background: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GeminiInteraction(
    val id: String? = null,
    val status: String? = null,
    val outputs: List<GeminiInteractionOutput>? = null,
    val error: GeminiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInteractionOutput(
    val text: String? = null
)
