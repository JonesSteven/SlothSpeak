package com.slothspeak.api

import com.slothspeak.api.models.ClaudeErrorResponse
import com.slothspeak.api.models.ClaudeMessage
import com.slothspeak.api.models.ClaudeOutputConfig
import com.slothspeak.api.models.ClaudeRequest
import com.slothspeak.api.models.ClaudeThinking
import com.slothspeak.api.models.ClaudeWebSearchTool
import com.slothspeak.data.ApiKeyManager
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class ClaudeLlmResult(
    val answerText: String,
    val responseId: String
)

class ClaudeClient(private val apiKeyManager: ApiKeyManager) {

    private val moshi = Moshi.Builder()
        .build()

    private val errorAdapter = moshi.adapter(ClaudeErrorResponse::class.java)

    private val authInterceptor = Interceptor { chain ->
        val apiKey = apiKeyManager.getClaudeKey()
            ?: throw ApiException("No Claude API key configured. Please set your Claude API key in Settings.")
        val request = chain.request().newBuilder()
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .build()
        chain.proceed(request)
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(1500, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val service: ClaudeService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ClaudeService::class.java)

    suspend fun generateContent(
        questionText: String,
        conversationHistory: List<Pair<String, String>>,
        effort: String,
        webSearchEnabled: Boolean = false
    ): ClaudeLlmResult {
        // Build messages array with conversation history
        val messages = mutableListOf<ClaudeMessage>()

        // Add previous conversation turns
        for ((prevQuestion, prevAnswer) in conversationHistory) {
            messages.add(ClaudeMessage(role = "user", content = prevQuestion))
            messages.add(ClaudeMessage(role = "assistant", content = prevAnswer))
        }

        // Add current question
        messages.add(ClaudeMessage(role = "user", content = questionText))

        val tools = if (webSearchEnabled) listOf(ClaudeWebSearchTool()) else null

        val request = ClaudeRequest(
            model = ApiKeyManager.MODEL_CLAUDE_OPUS,
            maxTokens = MAX_TOKENS,
            system = apiKeyManager.getSystemPrompt(),
            messages = messages,
            thinking = ClaudeThinking(type = "adaptive"),
            outputConfig = ClaudeOutputConfig(effort = effort),
            tools = tools
        )

        val response = service.createMessage(request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw ApiException("Empty Claude response")
            val contentBlocks = body.content
                ?: throw ApiException("No content in Claude response")
            if (contentBlocks.isEmpty()) {
                throw ApiException("No content blocks in Claude response")
            }

            // Filter out thinking blocks and get the text content
            val textBlocks = contentBlocks.filter { it.type == "text" && !it.text.isNullOrBlank() }
            val answerText = textBlocks.lastOrNull()?.text
                ?: throw ApiException("No text content found in Claude response")

            return ClaudeLlmResult(
                answerText = answerText,
                responseId = body.id
            )
        } else {
            throw parseApiError(response.code(), response.errorBody()?.string())
        }
    }

    private fun parseApiError(code: Int, errorBody: String?): ApiException {
        val detail = try {
            errorBody?.let { errorAdapter.fromJson(it)?.error?.message }
        } catch (_: Exception) {
            null
        }

        return when (code) {
            400 -> ApiException(
                detail ?: "Bad request to Claude API. Please check your configuration.",
                code,
                false
            )
            401, 403 -> ApiException(
                "Invalid Claude API key. Please check your key in Settings.",
                code,
                true
            )
            429 -> ApiException(
                detail ?: "Rate limited by Claude API. Please wait and try again.",
                code,
                true
            )
            529 -> ApiException(
                detail ?: "Claude API is overloaded. Please try again later.",
                code,
                true
            )
            in 500..599 -> ApiException(
                detail ?: "Claude server error. Please try again later.",
                code,
                true
            )
            else -> ApiException(
                detail ?: "Claude API request failed (HTTP $code)",
                code,
                true
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1/"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val MAX_TOKENS = 16000
    }
}
