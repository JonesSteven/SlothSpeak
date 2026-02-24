package com.slothspeak.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/**
 * Wraps Android AudioManager audio focus into a coroutine-friendly interface.
 *
 * Two modes:
 * - Recording focus: synchronous, exclusive, silences other apps during mic input
 * - Playback focus: suspending, handles delayed focus (e.g. phone call in progress)
 */
class AudioFocusManager(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var currentFocusRequest: AudioFocusRequest? = null

    /** Optional reference to BluetoothAudioRouter, used to distinguish app-set MODE_IN_COMMUNICATION from phone calls */
    var bluetoothRouter: BluetoothAudioRouter? = null

    // Callbacks wired by the service to control playback
    var onFocusGained: (() -> Unit)? = null
    var onFocusLostTransient: (() -> Unit)? = null
    var onFocusLost: (() -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                onFocusGained?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Speech is unintelligible when ducked, so treat duck as transient loss
                Log.d(TAG, "Audio focus lost transiently (focusChange=$focusChange)")
                onFocusLostTransient?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                onFocusLost?.invoke()
            }
        }
    }

    /**
     * Request exclusive focus for recording. Synchronous — returns immediately.
     * Uses AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE to silence all other audio during mic input.
     */
    fun requestRecordingFocus(): Boolean {
        abandonFocusInternal()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        currentFocusRequest = request
        val result = audioManager.requestAudioFocus(request)
        Log.d(TAG, "requestRecordingFocus result=$result")
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Request transient focus for playback. Other apps receive AUDIOFOCUS_LOSS_TRANSIENT
     * and will auto-resume when we abandon focus.
     *
     * Uses AUDIOFOCUS_GAIN_TRANSIENT with willPauseWhenDucked so speech audio gets a
     * clean pause instead of unintelligible ducking.
     *
     * If focus is denied (e.g. phone call in progress), returns false — callers should
     * proceed anyway since the user explicitly triggered playback.
     */
    fun requestPlaybackFocus(): Boolean {
        abandonFocusInternal()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        currentFocusRequest = request
        val result = audioManager.requestAudioFocus(request)
        Log.d(TAG, "requestPlaybackFocus result=$result")

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Release audio focus so other apps can resume playback.
     */
    fun abandonFocus() {
        abandonFocusInternal()
    }

    private fun abandonFocusInternal() {
        currentFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
            Log.d(TAG, "Audio focus abandoned")
        }
        currentFocusRequest = null
    }

    /**
     * Full cleanup: abandons focus, nulls callbacks. Call from onDestroy().
     */
    fun release() {
        abandonFocusInternal()
        onFocusGained = null
        onFocusLostTransient = null
        onFocusLost = null
    }

    /**
     * Returns true if a phone call (telephony or VoIP) is active or the phone is ringing.
     * Used to skip playback during calls without affecting music app pausing behavior.
     */
    fun isPhoneCallActive(): Boolean {
        val mode = audioManager.mode
        // Exclude MODE_IN_COMMUNICATION when the app itself set it for Bluetooth routing
        val isCommunicationMode = mode == AudioManager.MODE_IN_COMMUNICATION
        val isAppBtRouting = bluetoothRouter?.isRoutingActive == true
        return mode == AudioManager.MODE_IN_CALL ||
               (isCommunicationMode && !isAppBtRouting) ||
               mode == AudioManager.MODE_RINGTONE
    }

    companion object {
        private const val TAG = "AudioFocusManager"
    }
}
