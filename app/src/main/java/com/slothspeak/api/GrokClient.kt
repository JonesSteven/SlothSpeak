package com.slothspeak.api

import com.slothspeak.api.models.ErrorResponse
import com.slothspeak.api.models.GrokInputMessage
import com.slothspeak.api.models.GrokRequest
import com.slothspeak.api.models.GrokSearchTool
import com.slothspeak.api.models.ResponsesResponse
import com.slothspeak.data.ApiKeyManager
import com.slothspeak.util.CitationFormatter
import com.slothspeak.util.CitationResult
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class GrokLlmResult(
    val answerText: String,
    val responseId: String,
    val answerTextRich: String? = null
)

class GrokClient(private val apiKeyManager: ApiKeyManager) {

    private val moshi = Moshi.Builder()
        .build()

    private val errorAdapter = moshi.adapter(ErrorResponse::class.java)

    private val authInterceptor = Interceptor { chain ->
        val apiKey = apiKeyManager.getGrokKey()
            ?: throw ApiException("No xAI API key configured. Please set your xAI API key in Settings.")
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        chain.proceed(request)
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(1500, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val service: GrokService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GrokService::class.java)

    suspend fun createResponse(
        questionText: String,
        previousResponseId: String? = null,
        webSearchEnabled: Boolean = false,
        xSearchEnabled: Boolean = false
    ): GrokLlmResult {
        val tools = mutableListOf<GrokSearchTool>()
        if (webSearchEnabled) tools.add(GrokSearchTool("web_search"))
        if (xSearchEnabled) tools.add(GrokSearchTool("x_search"))
        val toolsList = tools.ifEmpty { null }

        // xAI Responses API: system prompt goes in input array on first request,
        // follow-ups only include the new user message (context is server-side)
        val input = mutableListOf<GrokInputMessage>()
        if (previousResponseId == null) {
            input.add(GrokInputMessage(role = "system", content = apiKeyManager.getSystemPrompt()))
        }
        input.add(GrokInputMessage(role = "user", content = questionText))

        val request = GrokRequest(
            model = ApiKeyManager.MODEL_GROK,
            input = input,
            previousResponseId = previousResponseId,
            tools = toolsList,
            store = true
        )

        val response = service.createResponse(request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw ApiException("Empty Grok response")
            val citationResult = extractAnswerText(body)
            return GrokLlmResult(
                answerText = citationResult.cleanText,
                responseId = body.id,
                answerTextRich = citationResult.richText
            )
        } else {
            throw parseApiError(response.code(), response.errorBody()?.string())
        }
    }

    private fun extractAnswerText(response: ResponsesResponse): CitationResult {
        for (output in response.output) {
            if (output.type == "message") {
                val content = output.content ?: continue
                for (item in content) {
                    if (item.type == "output_text" && item.text != null) {
                        return CitationFormatter.processCitations(item.text, item.annotations)
                    }
                }
            }
        }
        throw ApiException("No text content found in Grok response")
    }

    private fun parseApiError(code: Int, errorBody: String?): ApiException {
        val detail = try {
            errorBody?.let { errorAdapter.fromJson(it)?.error?.message }
        } catch (_: Exception) {
            null
        }

        return when (code) {
            401 -> ApiException(
                "Invalid xAI API key. Please check your key in Settings.",
                code,
                true
            )
            429 -> ApiException(
                detail ?: "Rate limited by xAI. Please wait and try again.",
                code,
                true
            )
            in 500..599 -> ApiException(
                detail ?: "xAI server error. Please try again later.",
                code,
                true
            )
            else -> ApiException(
                detail ?: "xAI API request failed (HTTP $code)",
                code,
                true
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://api.x.ai/v1/"
    }
}
