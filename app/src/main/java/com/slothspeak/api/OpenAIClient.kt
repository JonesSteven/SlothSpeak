package com.slothspeak.api

import com.slothspeak.api.models.ErrorResponse
import com.slothspeak.api.models.ReasoningConfig
import com.slothspeak.api.models.ResponsesRequest
import com.slothspeak.api.models.ResponsesResponse
import com.slothspeak.api.models.WebSearchTool
import com.slothspeak.api.models.SpeechRequest
import com.slothspeak.api.models.TranscriptionResponse
import com.slothspeak.data.ApiKeyManager
import com.slothspeak.util.CitationFormatter
import com.slothspeak.util.CitationResult
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAIClient(private val apiKeyManager: ApiKeyManager) {

    private val moshi = Moshi.Builder()
        .build()

    private val errorAdapter = moshi.adapter(ErrorResponse::class.java)

    private val authInterceptor = Interceptor { chain ->
        val apiKey = apiKeyManager.getKey()
            ?: throw ApiException("No API key configured. Please set your OpenAI API key in Settings.")
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        chain.proceed(request)
    }

    private val sttClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val llmClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(1500, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val ttsClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildService(client: OkHttpClient): OpenAIService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenAIService::class.java)
    }

    private val sttService = buildService(sttClient)
    private val llmService = buildService(llmClient)
    private val ttsService = buildService(ttsClient)

    suspend fun transcribeAudio(audioFile: File): String {
        val contentType = when {
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            else -> "audio/mp4"
        }
        val requestFile = audioFile.asRequestBody(contentType.toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
        val modelBody = "gpt-4o-transcribe".toRequestBody("text/plain".toMediaType())
        val formatBody = "json".toRequestBody("text/plain".toMediaType())

        val response = sttService.transcribeAudio(filePart, modelBody, formatBody)
        if (response.isSuccessful) {
            val body = response.body() ?: throw ApiException("Empty transcription response")
            if (body.text.isBlank()) {
                throw ApiException("Could not understand the recording. Please try again.")
            }
            return body.text
        } else {
            throw parseApiError(response.code(), response.errorBody()?.string())
        }
    }

    suspend fun createResponse(
        questionText: String,
        previousResponseId: String? = null,
        model: String = ApiKeyManager.MODEL_PRO,
        reasoningEffort: String? = null,
        webSearchEnabled: Boolean = false
    ): ResponsesResponse {
        // Only include reasoning config for Pro model (standard model doesn't support extended thinking)
        val reasoningConfig = if (model == ApiKeyManager.MODEL_PRO && reasoningEffort != null) {
            ReasoningConfig(effort = reasoningEffort)
        } else {
            null
        }
        val tools = if (webSearchEnabled) listOf(WebSearchTool()) else null
        val request = ResponsesRequest(
            model = model,
            instructions = apiKeyManager.getSystemPrompt(),
            input = questionText,
            reasoning = reasoningConfig,
            previousResponseId = previousResponseId,
            tools = tools,
            store = true
        )

        val response = llmService.createResponse(request)
        if (response.isSuccessful) {
            return response.body() ?: throw ApiException("Empty LLM response")
        } else {
            throw parseApiError(response.code(), response.errorBody()?.string())
        }
    }

    suspend fun createDeepResearch(questionText: String): ResponsesResponse {
        val request = ResponsesRequest(
            model = ApiKeyManager.MODEL_DEEP_RESEARCH,
            instructions = apiKeyManager.getDeepResearchSystemPrompt(),
            input = questionText,
            tools = listOf(WebSearchTool(type = "web_search_preview")),
            store = true,
            background = true
        )

        val createResponse = llmService.createResponse(request)
        if (!createResponse.isSuccessful) {
            throw parseApiError(createResponse.code(), createResponse.errorBody()?.string())
        }
        val initial = createResponse.body()
            ?: throw ApiException("Empty response when creating deep research task")

        if (initial.status == "completed") return initial
        if (initial.status == "failed" || initial.status == "cancelled") {
            throw ApiException("Deep research task ${initial.status}")
        }

        // Poll until completed
        var consecutiveErrors = 0
        while (true) {
            delay(DEEP_RESEARCH_POLL_INTERVAL_MS)

            val pollResponse = try {
                llmService.getResponse(initial.id)
            } catch (e: Exception) {
                consecutiveErrors++
                if (consecutiveErrors >= MAX_POLL_ERRORS) {
                    throw ApiException("Deep research polling failed after $MAX_POLL_ERRORS consecutive errors: ${e.message}")
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
            val result = pollResponse.body()
                ?: throw ApiException("Empty poll response for deep research")

            when (result.status) {
                "completed" -> return result
                "failed", "cancelled" -> throw ApiException("Deep research task ${result.status}")
                else -> { /* queued, in_progress — keep polling */ }
            }
        }
    }

    suspend fun createSpeech(text: String, outputFile: File, voice: String = "marin", instructions: String) {
        val request = SpeechRequest(input = text, voice = voice, instructions = instructions)
        val response = ttsService.createSpeech(request)
        if (response.isSuccessful) {
            val body = response.body() ?: throw ApiException("Empty TTS response")
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            throw parseApiError(response.code(), response.errorBody()?.string())
        }
    }

    fun extractAnswerText(response: ResponsesResponse): CitationResult {
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
        throw ApiException("No text content found in LLM response")
    }

    private fun parseApiError(code: Int, errorBody: String?): ApiException {
        val detail = try {
            errorBody?.let { errorAdapter.fromJson(it)?.error?.message }
        } catch (_: Exception) {
            null
        }

        return when (code) {
            401 -> ApiException("Invalid API key. Please check your key in Settings.", code, true)
            429 -> ApiException(
                detail ?: "Rate limited by OpenAI. Please wait and try again.",
                code,
                true
            )
            in 500..599 -> ApiException(
                detail ?: "OpenAI server error. Please try again later.",
                code,
                true
            )
            else -> ApiException(
                detail ?: "API request failed (HTTP $code)",
                code,
                true
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://api.openai.com/v1/"
        private const val DEEP_RESEARCH_POLL_INTERVAL_MS = 20_000L
        private const val MAX_POLL_ERRORS = 3
    }
}

class ApiException(
    message: String,
    val httpCode: Int = 0,
    val isRetryable: Boolean = false
) : IOException(message)
