package com.slothspeak.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GrokRequest(
    val model: String,
    val input: List<GrokInputMessage>,
    @Json(name = "previous_response_id")
    val previousResponseId: String? = null,
    val tools: List<GrokSearchTool>? = null,
    val store: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GrokInputMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class GrokSearchTool(val type: String)
