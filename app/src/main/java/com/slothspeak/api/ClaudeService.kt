package com.slothspeak.api

import com.slothspeak.api.models.ClaudeRequest
import com.slothspeak.api.models.ClaudeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ClaudeService {

    @POST("messages")
    suspend fun createMessage(
        @Body request: ClaudeRequest
    ): Response<ClaudeResponse>
}
