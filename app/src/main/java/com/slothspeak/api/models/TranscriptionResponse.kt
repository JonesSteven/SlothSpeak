package com.slothspeak.api.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TranscriptionResponse(
    val text: String
)
