package com.slothspeak.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var monitorJob: Job? = null
    private var startTimeMs: Long = 0

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    data class RecordingState(
        val isRecording: Boolean = false,
        val durationMs: Long = 0,
        val autoStopped: Boolean = false
    )

    fun startRecording(
        scope: CoroutineScope,
        preferredDevice: AudioDeviceInfo? = null,
        useBluetoothAudioSource: Boolean = false
    ): File {
        val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val audioSource = if (useBluetoothAudioSource) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.MIC
        }

        recorder = createMediaRecorder().apply {
            setAudioSource(audioSource)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setAudioEncodingBitRate(32000)
            setOutputFile(file.absolutePath)
            setMaxFileSize(MAX_FILE_SIZE_BYTES)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    _state.value = _state.value.copy(autoStopped = true)
                    stopRecording()
                }
            }
            prepare()
            // Set preferred device after prepare() — available on API 28+
            if (preferredDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setPreferredDevice(preferredDevice)
            }
            start()
        }

        startTimeMs = System.currentTimeMillis()
        _state.value = RecordingState(isRecording = true)

        // Monitor elapsed time and auto-stop at max duration
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive && _state.value.isRecording) {
                delay(500)
                val elapsed = System.currentTimeMillis() - startTimeMs
                _state.value = _state.value.copy(durationMs = elapsed)

                if (elapsed >= MAX_DURATION_MS) {
                    _state.value = _state.value.copy(autoStopped = true)
                    stopRecording()
                    break
                }
            }
        }

        return file
    }

    fun stopRecording(): File? {
        monitorJob?.cancel()
        monitorJob = null

        val rec = recorder
        recorder = null
        if (rec != null) {
            try {
                rec.stop()
            } catch (_: Exception) {
                // May throw if recording was very short
            }
            try {
                rec.release()
            } catch (_: Exception) {
            }
        }

        _state.value = _state.value.copy(isRecording = false)
        return outputFile
    }

    fun release() {
        stopRecording()
        outputFile?.delete()
        outputFile = null
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    companion object {
        const val MAX_FILE_SIZE_BYTES = 24L * 1024 * 1024 // 24 MB (safety net)
        const val MAX_DURATION_MS = 5L * 60 * 1000 // 5 minutes
    }
}
