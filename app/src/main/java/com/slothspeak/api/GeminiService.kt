package com.slothspeak.api

import com.slothspeak.api.models.GeminiInteraction
import com.slothspeak.api.models.GeminiInteractionRequest
import com.slothspeak.api.models.GeminiRequest
import com.slothspeak.api.models.GeminiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiService {

    @POST("models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    @POST("interactions")
    suspend fun createInteraction(
        @Body request: GeminiInteractionRequest
    ): Response<GeminiInteraction>

    @GET("interactions/{interactionId}")
    suspend fun getInteraction(
        @Path("interactionId") interactionId: String
    ): Response<GeminiInteraction>
}
