package com.slothspeak.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ResponsesResponse(
    val id: String,
    val status: String,
    val output: List<OutputItem> = emptyList(),
    val usage: Usage? = null
)

@JsonClass(generateAdapter = true)
data class OutputItem(
    val type: String,
    val content: List<ContentItem>? = null
)

@JsonClass(generateAdapter = true)
data class ContentItem(
    val type: String,
    val text: String? = null,
    val annotations: List<Annotation>? = null
)

@JsonClass(generateAdapter = true)
data class Annotation(
    val type: String,
    @Json(name = "start_index")
    val startIndex: Int,
    @Json(name = "end_index")
    val endIndex: Int,
    val url: String? = null,
    val title: String? = null
)

@JsonClass(generateAdapter = true)
data class Usage(
    val input_tokens: Int = 0,
    val output_tokens: Int = 0,
    val total_tokens: Int = 0
)
