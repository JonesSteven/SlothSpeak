package com.slothspeak.audio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Centralizes Bluetooth headset microphone routing for recording.
 *
 * Routing strategy (API 31+):
 * 1. Try setCommunicationDevice() without MODE_IN_COMMUNICATION (works for some BLE devices)
 * 2. Try setCommunicationDevice() with MODE_IN_COMMUNICATION (needed for most SCO devices)
 * 3. Fall back to startBluetoothSco() (deprecated but reliable on devices where setCommunicationDevice fails)
 *
 * On API 26-30: Uses startBluetoothSco() + MODE_IN_COMMUNICATION directly.
 *
 * Usage:
 * 1. Call startBluetoothRecordingRoute() before creating a recorder — returns true if BT mic is available
 * 2. Pass findBluetoothInputDevice() to the recorder's preferredDevice parameter
 * 3. Call stopBluetoothRecordingRoute() after recording completes
 * 4. Call release() on lifecycle cleanup
 */
class BluetoothAudioRouter(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    var isRoutingActive = false
        private set

    @Volatile
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    @Volatile
    private var isScoRouted = false

    @Volatile
    private var usedCommunicationDevice = false

    private var scoReceiver: BroadcastReceiver? = null

    /**
     * Returns true if a Bluetooth headset with a microphone (SCO or BLE) is connected.
     */
    fun isBluetoothHeadsetConnected(): Boolean {
        if (!hasBluetoothPermission()) return false
        return findBluetoothInputDevice() != null
    }

    /**
     * Finds a connected Bluetooth input device (microphone) from the available audio devices.
     * Returns null if no Bluetooth input device is available.
     */
    fun findBluetoothInputDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }

    /**
     * Enables Bluetooth microphone routing for recording.
     *
     * @return true if BT mic routing was successfully enabled, false if no BT headset is available
     *         or routing failed (caller should fall back to built-in mic).
     */
    suspend fun startBluetoothRecordingRoute(): Boolean {
        if (isRoutingActive) return true

        if (!hasBluetoothPermission()) {
            Log.d(TAG, "No Bluetooth permission, skipping BT routing")
            return false
        }

        val btDevice = findBluetoothInputDevice()
        if (btDevice == null) {
            Log.d(TAG, "No Bluetooth input device found")
            return false
        }

        Log.d(TAG, "Found Bluetooth input device: ${btDevice.productName} (type=${btDevice.type})")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Try setCommunicationDevice first (modern API)
            if (tryCommunicationDevice(btDevice)) {
                return true
            }
            // setCommunicationDevice failed — fall through to legacy SCO
            Log.d(TAG, "setCommunicationDevice failed, falling back to legacy SCO")
        }

        return startRoutingSco()
    }

    /**
     * Restores normal audio routing after recording completes. Idempotent.
     */
    fun stopBluetoothRecordingRoute() {
        if (!isRoutingActive) return

        Log.d(TAG, "Stopping Bluetooth recording route")

        if (usedCommunicationDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
            audioManager.mode = previousAudioMode
            usedCommunicationDevice = false
            Log.d(TAG, "clearCommunicationDevice called, restored audio mode to $previousAudioMode")
        }

        if (isScoRouted) {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            audioManager.mode = previousAudioMode
            isScoRouted = false
            Log.d(TAG, "Stopped Bluetooth SCO, restored audio mode to $previousAudioMode")
        }

        unregisterScoReceiver()
        isRoutingActive = false
    }

    /**
     * Full lifecycle cleanup. Call from onDestroy() / onCleared().
     */
    fun release() {
        stopBluetoothRecordingRoute()
        unregisterScoReceiver()
    }

    // --- API 31+ setCommunicationDevice attempts ---

    /**
     * Tries setCommunicationDevice, first without MODE_IN_COMMUNICATION (for BLE),
     * then with it (for SCO). Returns true if either attempt succeeds.
     */
    private fun tryCommunicationDevice(btDevice: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        previousAudioMode = audioManager.mode

        // For non-SCO devices, try without changing audio mode first
        if (btDevice.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            val success = audioManager.setCommunicationDevice(btDevice)
            if (success) {
                usedCommunicationDevice = true
                isRoutingActive = true
                Log.d(TAG, "setCommunicationDevice succeeded for ${btDevice.productName}")
                return true
            }
            Log.d(TAG, "setCommunicationDevice failed without MODE_IN_COMMUNICATION, retrying with it")
        }

        // Try with MODE_IN_COMMUNICATION (required for SCO, sometimes needed for BLE)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val success = audioManager.setCommunicationDevice(btDevice)
        if (success) {
            usedCommunicationDevice = true
            isRoutingActive = true
            Log.d(TAG, "setCommunicationDevice succeeded with MODE_IN_COMMUNICATION for ${btDevice.productName}")
            return true
        }

        // Failed — restore mode before falling back
        audioManager.mode = previousAudioMode
        return false
    }

    // --- Legacy SCO routing (API 26-30, also fallback for API 31+) ---

    private suspend fun startRoutingSco(): Boolean {
        previousAudioMode = audioManager.mode

        // Register for SCO connection state changes
        var scoConnected = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_ERROR
                )
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    scoConnected = true
                    Log.d(TAG, "SCO audio connected")
                }
            }
        }
        scoReceiver = receiver

        @Suppress("DEPRECATION")
        context.registerReceiver(
            receiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        )

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.startBluetoothSco()
        isScoRouted = true
        Log.d(TAG, "Started Bluetooth SCO, waiting for connection...")

        // Wait up to 3 seconds for SCO connection
        val startTime = System.currentTimeMillis()
        val timeoutMs = 3000L
        while (!scoConnected && (System.currentTimeMillis() - startTime) < timeoutMs) {
            delay(100)
        }

        if (scoConnected) {
            isRoutingActive = true
            Log.d(TAG, "Bluetooth SCO connected successfully")
            return true
        } else {
            Log.w(TAG, "Bluetooth SCO connection timed out after ${timeoutMs}ms")
            // Clean up on failure
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            audioManager.mode = previousAudioMode
            isScoRouted = false
            unregisterScoReceiver()
            return false
        }
    }

    // --- Helpers ---

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            // BLUETOOTH permission is normal (not runtime) on API <31
            true
        }
    }

    private fun unregisterScoReceiver() {
        scoReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
            scoReceiver = null
        }
    }

    companion object {
        private const val TAG = "BluetoothAudioRouter"
    }
}
