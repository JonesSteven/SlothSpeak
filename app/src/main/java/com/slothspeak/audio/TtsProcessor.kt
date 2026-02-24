package com.slothspeak.audio

import android.util.Log
import com.slothspeak.api.OpenAIClient
import com.slothspeak.util.TextChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

class TtsProcessor(
    private val client: OpenAIClient,
    private val audioDir: File,
    private val voice: String = "marin",
    private val ttsInstructions: String
) {

    private val _progress = MutableStateFlow(TtsProgress())
    val progress: StateFlow<TtsProgress> = _progress

    data class TtsProgress(
        val totalChunks: Int = 0,
        val completedChunks: Int = 0,
        val isComplete: Boolean = false,
        val error: String? = null,
        val statusMessage: String = ""
    )

    suspend fun processText(text: String): List<File> = coroutineScope {
        val chunks = TextChunker.chunkText(text)
        val totalChunks = chunks.size
        val totalChars = text.length

        Log.i(TAG, "TTS: $totalChunks chunks from $totalChars chars")
        _progress.value = TtsProgress(
            totalChunks = totalChunks,
            statusMessage = "Splitting text: $totalChunks chunks ($totalChars chars)"
        )

        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }

        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        var completedCount = 0
        val lock = Any()
        val startTime = System.currentTimeMillis()

        val deferredFiles = chunks.mapIndexed { index, chunk ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val chunkNum = index + 1
                    val chunkChars = chunk.length
                    Log.i(TAG, "TTS chunk $chunkNum/$totalChunks: sending $chunkChars chars")

                    synchronized(lock) {
                        _progress.value = _progress.value.copy(
                            statusMessage = "Sending chunk $chunkNum/$totalChunks ($chunkChars chars)..."
                        )
                    }

                    val baseName = "chunk_${String.format("%03d", index)}"
                    val chunkLabel = "chunk $chunkNum/$totalChunks"
                    val chunkStart = System.currentTimeMillis()

                    val resultFiles = processSingleChunk(
                        text = chunk,
                        baseName = baseName,
                        chunkLabel = chunkLabel,
                        depth = 0
                    )

                    val elapsed = System.currentTimeMillis() - chunkStart

                    synchronized(lock) {
                        completedCount++
                        val totalElapsed = (System.currentTimeMillis() - startTime) / 1000
                        val statusDetail = if (resultFiles.size > 1) {
                            "Chunk $chunkNum done (split into ${resultFiles.size} parts, ${elapsed / 1000}s). "
                        } else {
                            val fileSize = resultFiles.first().length() / 1024
                            "Chunk $chunkNum done (${elapsed / 1000}s, ${fileSize}KB). "
                        }
                        _progress.value = _progress.value.copy(
                            completedChunks = completedCount,
                            statusMessage = "$statusDetail$completedCount/$totalChunks complete [${totalElapsed}s total]"
                        )
                    }

                    resultFiles
                }
            }
        }

        val files = deferredFiles.awaitAll().flatten()
        val totalElapsed = (System.currentTimeMillis() - startTime) / 1000
        Log.i(TAG, "TTS complete: $totalChunks chunks in ${totalElapsed}s")
        _progress.value = _progress.value.copy(
            isComplete = true,
            statusMessage = "All $totalChunks chunks generated in ${totalElapsed}s"
        )
        files
    }

    /**
     * Process a single piece of text into TTS audio file(s).
     * Handles retry with backoff for transient errors, and recursive splitting
     * for token limit errors. Returns one file normally, or multiple files if
     * the text had to be split.
     */
    private suspend fun processSingleChunk(
        text: String,
        baseName: String,
        chunkLabel: String,
        depth: Int
    ): List<File> {
        val outputFile = File(audioDir, "$baseName.mp3")
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                if (attempt > 1) {
                    val backoffMs = RETRY_BASE_DELAY_MS * (1L shl (attempt - 2))
                    Log.w(TAG, "TTS $chunkLabel: retry $attempt after ${backoffMs}ms")
                    delay(backoffMs)
                }

                client.createSpeech(text, outputFile, voice, ttsInstructions)

                val fileSize = outputFile.length() / 1024
                Log.i(TAG, "TTS $chunkLabel done: ${fileSize}KB (depth=$depth)")

                return listOf(outputFile)
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "TTS $chunkLabel attempt $attempt failed: ${e.message}")

                if (isTokenLimitError(e)) {
                    if (depth >= MAX_SPLIT_DEPTH) {
                        Log.e(TAG, "TTS $chunkLabel: token limit at max split depth $depth, giving up")
                        throw e
                    }

                    val halves = TextChunker.splitInHalf(text)
                    Log.i(TAG, "TTS $chunkLabel: token limit hit, splitting into ${halves.size} sub-chunks " +
                            "(${halves.map { it.length }} chars, depth=${depth + 1})")

                    return halves.flatMapIndexed { subIndex, subChunk ->
                        processSingleChunk(
                            text = subChunk,
                            baseName = "${baseName}_$subIndex",
                            chunkLabel = "${chunkLabel}_$subIndex",
                            depth = depth + 1
                        )
                    }
                }

                if (attempt == MAX_RETRIES) {
                    Log.e(TAG, "TTS $chunkLabel: all $MAX_RETRIES attempts failed")
                }
            }
        }

        throw lastException!!
    }

    fun reset() {
        _progress.value = TtsProgress()
    }

    private fun isTokenLimitError(e: Exception): Boolean {
        val message = e.message ?: return false
        return message.contains("maximum input limit", ignoreCase = true)
    }

    companion object {
        private const val TAG = "TtsProcessor"
        private const val MAX_CONCURRENT_REQUESTS = 3
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 2000L
        private const val MAX_SPLIT_DEPTH = 3
    }
}
