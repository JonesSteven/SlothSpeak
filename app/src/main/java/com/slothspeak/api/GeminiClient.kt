package com.slothspeak.api

import com.slothspeak.api.models.GeminiContent
import com.slothspeak.api.models.GeminiErrorResponse
import com.slothspeak.api.models.GeminiGenerationConfig
import com.slothspeak.api.models.GeminiGoogleSearch
import com.slothspeak.api.models.GeminiInteraction
import com.slothspeak.api.models.GeminiInteractionRequest
import com.slothspeak.api.models.GeminiPart
import com.slothspeak.api.models.GeminiRequest
import com.slothspeak.api.models.GeminiThinkingConfig
import com.slothspeak.api.models.GeminiTool
import com.slothspeak.data.ApiKeyManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GeminiLlmResult(
    val answerText: String,
    val responseId: String
)

class GeminiClient(private val apiKeyManager: ApiKeyManager) {

    private val moshi = Moshi.Builder()
        .build()

    private val errorAdapter = moshi.adapter(GeminiErrorResponse::class.java)

    private val authInterceptor = Interceptor { chain ->
        val apiKey = apiKeyManager.getGeminiKey()
            ?: throw ApiException("No Gemini API key configured. Please set your Gemini API key in Settings.")
        val request = chain.request().newBuilder()
            .addHeader("x-goog-api-key", apiKey)
            .build()
        chain.proceed(request)
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(1500, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val service: GeminiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiService::class.java)

    suspend fun generateContent(
        questionText: String,
        conversationHistory: List<Pair<String, String>>,
        thinkingLevel: String,
        webSearchEnabled: Boolean = false
    ): GeminiLlmResult {
        // Build contents array with conversation history
        val contents = mutableListOf<GeminiContent>()

        // Add previous conversation turns
        for ((prevQuestion, prevAnswer) in conversationHistory) {
            contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prevQuestion))))
            contents.add(GeminiContent(role = "model", parts = listOf(GeminiPart(text = prevAnswer))))
        }

        // Add current question
        contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = questionText))))

        val tools = if (webSearchEnabled) listOf(GeminiTool(googleSearch = GeminiGoogleSearch())) else null

        val request = GeminiRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = apiKeyManager.getSystemPrompt()))
            ),
            contents = contents,
            generationConfig = GeminiGenerationConfig(
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = thinkingLevel)
            ),
            tools = tools
        )

        val response = service.generateContent(ApiKeyManager.MODEL_GEMINI_PRO, request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw ApiException("Empty Gemini response")
            val candidates = body.candidates
                ?: throw ApiException("No candidates in Gemini response")
            if (candidates.isEmpty()) {
                throw ApiException("No candidates in Gemini response")
            }
            val parts = candidates[0].content?.parts
                ?: throw ApiException("No content in Gemini response")

            // Filter out thinking parts and get the answer text
            val answerParts = parts.filter { it.thought != true && it.text.isNotBlank() }
            val answerText = answerParts.lastOrNull()?.text
                ?: throw ApiException("No text content found in Gemini response")

            return GeminiLlmResult(
                answerText = answerText,
                responseId = UUID.randomUUID().toString()
            )
        } else {
            throw parseApiError(response.code(), response.errorBody()?.string())
        }
    }

    suspend fun createDeepResearch(questionText: String): GeminiLlmResult {
        val request = GeminiInteractionRequest(
            input = "${apiKeyManager.getDeepResearchSystemPrompt()}\n\n$questionText",
            agent = ApiKeyManager.MODEL_GEMINI_DEEP_RESEARCH,
            background = true
        )

        val createResponse = service.createInteraction(request)
        if (!createResponse.isSuccessful) {
            throw parseApiError(createResponse.code(), createResponse.errorBody()?.string())
        }

        val interaction = createResponse.body()
            ?: throw ApiException("Empty response when creating Gemini Deep Research interaction")
        val interactionId = interaction.id
            ?: throw ApiException("No interaction ID returned from Gemini Deep Research")

        // Poll until completed or failed
        var consecutiveErrors = 0
        while (true) {
            delay(DEEP_RESEARCH_POLL_INTERVAL_MS)

            val pollResponse = try {
                service.getInteraction(interactionId)
            } catch (e: Exception) {
                consecutiveErrors++
                if (consecutiveErrors >= MAX_POLL_ERRORS) {
                    throw ApiException(
                        "Lost connection to Gemini Deep Research after $MAX_POLL_ERRORS consecutive poll failures: ${e.message}",
                        isRetryable = true
                    )
                }
                continue
            }

            if (!pollResponse.isSuccessful) {
                consecutiveErrors++
                if (consecutiveErrors >= MAX_POLL_ERRORS) {
                    throw parseApiError(pollResponse.code(), pollResponse.errorBody()?.string())
                }
                continue
            }

            consecutiveErrors = 0
            val current = pollResponse.body()
                ?: throw ApiException("Empty poll response from Gemini Deep Research")

            when (current.status) {
                "completed" -> return extractDeepResearchResult(current, interactionId)
                "failed" -> {
                    val errorMsg = current.error?.message ?: "Gemini Deep Research task failed"
                    throw ApiException(errorMsg, isRetryable = true)
                }
                // "running", "pending", etc. — keep polling
            }
        }
    }

    private fun extractDeepResearchResult(interaction: GeminiInteraction, interactionId: String): GeminiLlmResult {
        val outputs = interaction.outputs
        if (outputs.isNullOrEmpty()) {
            throw ApiException("Gemini Deep Research completed but returned no output")
        }
        val answerText = outputs.last().text
        if (answerText.isNullOrBlank()) {
            throw ApiException("Gemini Deep Research completed but output text is empty")
        }
        return GeminiLlmResult(
            answerText = answerText,
            responseId = interactionId
        )
    }

    private fun parseApiError(code: Int, errorBody: String?): ApiException {
        val detail = try {
            errorBody?.let { errorAdapter.fromJson(it)?.error?.message }
        } catch (_: Exception) {
            null
        }

        return when (code) {
            400 -> ApiException(
                detail ?: "Bad request to Gemini API. Please check your configuration.",
                code,
                false
            )
            401, 403 -> ApiException(
                "Invalid Gemini API key. Please check your key in Settings.",
                code,
                true
            )
            429 -> ApiException(
                detail ?: "Rate limited by Gemini API. Please wait and try again.",
                code,
                true
            )
            in 500..599 -> ApiException(
                detail ?: "Gemini server error. Please try again later.",
                code,
                true
            )
            else -> ApiException(
                detail ?: "Gemini API request failed (HTTP $code)",
                code,
                true
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"
        private const val DEEP_RESEARCH_POLL_INTERVAL_MS = 20_000L
        private const val MAX_POLL_ERRORS = 5
    }
}
