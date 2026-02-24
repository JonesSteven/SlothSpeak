package com.slothspeak.api

import com.slothspeak.api.models.ResponsesRequest
import com.slothspeak.api.models.ResponsesResponse
import com.slothspeak.api.models.SpeechRequest
import com.slothspeak.api.models.TranscriptionResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface OpenAIService {

    @Multipart
    @POST("audio/transcriptions")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody
    ): Response<TranscriptionResponse>

    @POST("responses")
    suspend fun createResponse(
        @Body request: ResponsesRequest
    ): Response<ResponsesResponse>

    @GET("responses/{responseId}")
    suspend fun getResponse(
        @Path("responseId") responseId: String
    ): Response<ResponsesResponse>

    @POST("audio/speech")
    suspend fun createSpeech(
        @Body request: SpeechRequest
    ): Response<ResponseBody>
}
