package com.slothspeak.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio recorder that uses Android's AudioRecord API with Silero VAD
 * for automatic end-of-speech detection. Writes WAV files compatible
 * with OpenAI's transcription API.
 */
class VadAudioRecorder(
    private val context: Context,
    private val silenceAfterSpeechMs: Int = 1800,
    private val noSpeechTimeoutMs: Int = 4000,
    private val maxRecordingMs: Long = MAX_RECORDING_MS
) {
    private var audioRecord: AudioRecord? = null
    private var vadSilero: VadSilero? = null
    private var recordingJob: Job? = null
    private var outputFile: File? = null

    private val _state = MutableStateFlow(VadRecordingState())
    val state: StateFlow<VadRecordingState> = _state

    data class VadRecordingState(
        val isRecording: Boolean = false,
        val isSpeechDetected: Boolean = false,
        val speechEndedByVad: Boolean = false,
        val noSpeechDetected: Boolean = false,
        val durationMs: Long = 0
    )

    fun startRecording(
        scope: CoroutineScope,
        preferredDevice: AudioDeviceInfo? = null,
        useBluetoothAudioSource: Boolean = false
    ): File? {
        if (_state.value.isRecording) return outputFile

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return null
        }

        val file = File(context.cacheDir, "vad_recording_${System.currentTimeMillis()}.wav")
        outputFile = file

        // Initialize VAD
        // speechDurationMs: how long speech must persist before we consider it "real" speech
        // silenceDurationMs: how long silence must persist after speech to consider speech ended
        try {
            vadSilero = Vad.builder()
                .setContext(context)
                .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                .setFrameSize(FrameSize.FRAME_SIZE_512)
                .setMode(Mode.NORMAL)
                .setSpeechDurationMs(250)
                .setSilenceDurationMs(silenceAfterSpeechMs)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Silero VAD", e)
            return null
        }

        // Initialize AudioRecord
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, FRAME_SIZE_SAMPLES * 2 * 4) // At least 4 frames

        // Use VOICE_COMMUNICATION on legacy API when BT SCO is active, otherwise MIC
        val audioSource = if (useBluetoothAudioSource) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.MIC
        }

        try {
            audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            // Set preferred device for Bluetooth routing (available API 23+)
            if (preferredDevice != null) {
                audioRecord?.setPreferredDevice(preferredDevice)
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                release()
                return null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException creating AudioRecord", e)
            release()
            return null
        }

        _state.value = VadRecordingState(isRecording = true)

        audioRecord?.startRecording()

        recordingJob = scope.launch(Dispatchers.IO) {
            recordAndAnalyze(file)
        }

        return file
    }

    private suspend fun recordAndAnalyze(file: File) {
        val record = audioRecord ?: return
        val vad = vadSilero ?: return

        val frameBuffer = ShortArray(FRAME_SIZE_SAMPLES)
        val pcmData = mutableListOf<ByteArray>()
        val startTime = System.currentTimeMillis()
        var hasSpeechBeenDetected = false

        try {
            while (currentCoroutineContext()[Job]?.isActive == true) {
                val elapsed = System.currentTimeMillis() - startTime

                // Safety cap: max recording duration
                if (elapsed >= maxRecordingMs) {
                    Log.d(TAG, "Max recording duration reached")
                    _state.value = _state.value.copy(
                        speechEndedByVad = true,
                        durationMs = elapsed
                    )
                    break
                }

                // Read a frame from the microphone
                val readResult = record.read(frameBuffer, 0, FRAME_SIZE_SAMPLES)
                if (readResult != FRAME_SIZE_SAMPLES) {
                    if (readResult < 0) {
                        Log.e(TAG, "AudioRecord.read() error: $readResult")
                        break
                    }
                    continue
                }

                // Convert frame to bytes and accumulate for WAV writing
                val byteBuffer = ByteBuffer.allocate(readResult * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                for (sample in 0 until readResult) {
                    byteBuffer.putShort(frameBuffer[sample])
                }
                pcmData.add(byteBuffer.array())

                // Run VAD on this frame
                val isSpeech = vad.isSpeech(frameBuffer)

                val updatedElapsed = System.currentTimeMillis() - startTime
                if (isSpeech && !hasSpeechBeenDetected) {
                    hasSpeechBeenDetected = true
                    _state.value = _state.value.copy(
                        isSpeechDetected = true,
                        durationMs = updatedElapsed
                    )
                    Log.d(TAG, "Speech detected at ${updatedElapsed}ms")
                } else if (!isSpeech && hasSpeechBeenDetected) {
                    // VAD's internal silenceDurationMs tracking means this only
                    // triggers after sustained silence (silenceAfterSpeechMs)
                    Log.d(TAG, "Speech ended by VAD at ${updatedElapsed}ms")
                    _state.value = _state.value.copy(
                        speechEndedByVad = true,
                        durationMs = updatedElapsed
                    )
                    break
                } else {
                    _state.value = _state.value.copy(durationMs = updatedElapsed)
                }

                // No speech timeout: if we haven't detected any speech within the timeout
                if (!hasSpeechBeenDetected && updatedElapsed >= noSpeechTimeoutMs) {
                    Log.d(TAG, "No speech detected within ${noSpeechTimeoutMs}ms")
                    _state.value = _state.value.copy(
                        noSpeechDetected = true,
                        durationMs = updatedElapsed
                    )
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during recording", e)
        }

        // Write WAV file
        val shouldWriteFile = hasSpeechBeenDetected && pcmData.isNotEmpty()
        if (shouldWriteFile) {
            writeWavFile(file, pcmData)
        }

        // Stop recording hardware
        try {
            record.stop()
        } catch (_: Exception) {
        }

        _state.value = _state.value.copy(isRecording = false)
    }

    private fun writeWavFile(file: File, pcmData: List<ByteArray>) {
        try {
            val totalDataSize = pcmData.sumOf { it.size }
            RandomAccessFile(file, "rw").use { raf ->
                // WAV header (44 bytes)
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

                // RIFF chunk
                header.put("RIFF".toByteArray())
                header.putInt(36 + totalDataSize) // File size - 8
                header.put("WAVE".toByteArray())

                // fmt sub-chunk
                header.put("fmt ".toByteArray())
                header.putInt(16) // Sub-chunk size (PCM)
                header.putShort(1) // Audio format (PCM = 1)
                header.putShort(1) // Num channels (mono)
                header.putInt(16000) // Sample rate
                header.putInt(16000 * 1 * 2) // Byte rate
                header.putShort(2) // Block align (channels * bytes per sample)
                header.putShort(16) // Bits per sample

                // data sub-chunk
                header.put("data".toByteArray())
                header.putInt(totalDataSize)

                raf.write(header.array())

                // Write PCM data
                for (chunk in pcmData) {
                    raf.write(chunk)
                }
            }
            Log.d(TAG, "WAV file written: ${file.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV file", e)
        }
    }

    fun stopRecording(): File? {
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        _state.value = _state.value.copy(isRecording = false)
        return outputFile
    }

    fun release() {
        stopRecording()
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        try {
            vadSilero?.close()
        } catch (_: Exception) {
        }
        vadSilero = null
    }

    companion object {
        private const val TAG = "VadAudioRecorder"
        const val MAX_RECORDING_MS = 5L * 60 * 1000 // 5 minutes
        private const val FRAME_SIZE_SAMPLES = 512 // 512 samples at 16kHz = 32ms per frame
    }
}
