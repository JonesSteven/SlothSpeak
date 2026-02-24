package com.slothspeak.audio

import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var activeContinuation: CancellableContinuation<Unit>? = null
    private var playbackSpeed: Float = 1.0f

    // Cross-chunk seeking state
    private var chunkDurations: List<Int> = emptyList()
    private var currentChunkIndex: Int = 0
    private var pendingChunkIndex: Int = -1
    private var startPositionMs: Int = 0
    private var currentFiles: List<File> = emptyList()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val currentChunk: Int = 0,
        val totalChunks: Int = 0,
        val isComplete: Boolean = false,
        val playbackSpeed: Float = 1.0f
    )

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2.0f)
        mediaPlayer?.let {
            try {
                it.playbackParams = it.playbackParams.setSpeed(playbackSpeed)
            } catch (_: Exception) {
                // Some devices may not support speed changes while playing
            }
        }
        _state.value = _state.value.copy(playbackSpeed = playbackSpeed)
    }

    suspend fun playFiles(files: List<File>) {
        if (files.isEmpty()) return

        currentFiles = files
        currentChunkIndex = 0
        pendingChunkIndex = -1
        startPositionMs = 0
        chunkDurations = scanChunkDurations(files)

        _state.value = PlaybackState(
            isPlaying = true,
            totalChunks = files.size,
            playbackSpeed = playbackSpeed
        )

        while (currentChunkIndex < files.size) {
            if (!_state.value.isPlaying) break

            _state.value = _state.value.copy(currentChunk = currentChunkIndex + 1)

            withContext(Dispatchers.Main) {
                playFile(files[currentChunkIndex])
            }

            if (pendingChunkIndex >= 0) {
                currentChunkIndex = pendingChunkIndex
                pendingChunkIndex = -1
            } else {
                currentChunkIndex++
            }
        }

        _state.value = _state.value.copy(
            isPlaying = false,
            isComplete = true
        )
    }

    private suspend fun playFile(file: File) = suspendCancellableCoroutine { continuation ->
        activeContinuation = continuation
        val player: MediaPlayer
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
            }
        } catch (e: Exception) {
            activeContinuation = null
            if (continuation.isActive) {
                continuation.resumeWith(
                    Result.failure(java.io.IOException("Failed to load audio file: ${file.name}", e))
                )
            }
            return@suspendCancellableCoroutine
        }
        mediaPlayer = player

        player.setOnCompletionListener {
            activeContinuation = null
            it.release()
            mediaPlayer = null
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }

        player.setOnErrorListener { mp, _, _ ->
            activeContinuation = null
            mp.release()
            mediaPlayer = null
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
            true
        }

        continuation.invokeOnCancellation {
            activeContinuation = null
            try {
                player.stop()
                player.release()
            } catch (_: Exception) {
            }
            mediaPlayer = null
        }

        // Apply playback speed before starting
        try {
            player.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
        } catch (_: Exception) {
            // Fallback: some devices may not support PlaybackParams
        }

        if (_state.value.isPaused) {
            // Don't start if paused - wait for resume
            if (startPositionMs > 0) {
                player.seekTo(startPositionMs)
                startPositionMs = 0
            }
        } else {
            player.start()
            if (startPositionMs > 0) {
                player.seekTo(startPositionMs)
                startPositionMs = 0
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _state.value = _state.value.copy(isPaused = true)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _state.value = _state.value.copy(isPaused = false)
        }
    }

    fun stop() {
        val continuation = activeContinuation
        activeContinuation = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
        pendingChunkIndex = -1
        startPositionMs = 0
        currentChunkIndex = 0
        currentFiles = emptyList()
        chunkDurations = emptyList()
        _state.value = PlaybackState(playbackSpeed = playbackSpeed)
        // Resume the suspended continuation so playFiles() can exit cleanly
        if (continuation?.isActive == true) {
            continuation.resume(Unit)
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun getChunkDurations(): List<Int> = chunkDurations

    fun getCurrentChunkIndex(): Int = currentChunkIndex

    /**
     * Seek to a specific chunk at a specific position within that chunk.
     * If the target is the current chunk, seeks within it directly.
     * If the target is a different chunk, sets up a redirect so the playFiles() loop
     * loads the correct chunk with the desired offset.
     */
    fun seekToChunk(chunkIndex: Int, positionMs: Int) {
        if (chunkIndex == currentChunkIndex) {
            // Same chunk — just seek within it
            mediaPlayer?.seekTo(positionMs)
        } else {
            // Different chunk — set redirect and stop the current player
            // so the continuation resumes and the while-loop picks up the redirect
            pendingChunkIndex = chunkIndex
            startPositionMs = positionMs
            val continuation = activeContinuation
            activeContinuation = null
            try {
                mediaPlayer?.apply {
                    if (isPlaying) stop()
                    release()
                }
            } catch (_: Exception) {
            }
            mediaPlayer = null
            // Resume continuation so playFiles() loop continues with the redirect
            if (continuation?.isActive == true) {
                continuation.resume(Unit)
            }
        }
    }

    private fun scanChunkDurations(files: List<File>): List<Int> {
        val durations = mutableListOf<Int>()
        val retriever = MediaMetadataRetriever()
        for (file in files) {
            try {
                retriever.setDataSource(file.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durations.add(durationStr?.toIntOrNull() ?: 0)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read duration for ${file.name}", e)
                durations.add(0)
            }
        }
        try {
            retriever.release()
        } catch (_: Exception) {
        }
        return durations
    }

    fun reset() {
        stop()
        _state.value = PlaybackState(playbackSpeed = playbackSpeed)
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}
