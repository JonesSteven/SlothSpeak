package com.slothspeak.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SpeechRequest(
    val model: String = "gpt-4o-mini-tts",
    val input: String,
    val voice: String = "marin",
    val instructions: String,
    @Json(name = "response_format")
    val responseFormat: String = "mp3"
)
