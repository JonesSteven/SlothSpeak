package com.slothspeak.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ResponsesRequest(
    val model: String,
    val instructions: String? = null,
    val input: String,
    val reasoning: ReasoningConfig? = null,
    @Json(name = "previous_response_id")
    val previousResponseId: String? = null,
    val tools: List<WebSearchTool>? = null,
    val store: Boolean = true,
    val background: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ReasoningConfig(
    val effort: String = "high"
)

@JsonClass(generateAdapter = true)
data class WebSearchTool(
    val type: String = "web_search"
)
