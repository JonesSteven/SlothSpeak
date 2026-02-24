package com.slothspeak.api

import com.slothspeak.api.models.GrokRequest
import com.slothspeak.api.models.ResponsesResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface GrokService {

    @POST("responses")
    suspend fun createResponse(
        @Body request: GrokRequest
    ): Response<ResponsesResponse>
}
