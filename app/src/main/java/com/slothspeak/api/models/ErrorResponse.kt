package com.slothspeak.api.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ErrorResponse(
    val error: ErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class ErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
