package com.slothspeak.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.os.StatFs
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slothspeak.audio.AudioFocusManager
import com.slothspeak.audio.AudioRecorder
import com.slothspeak.audio.BluetoothAudioRouter
import com.slothspeak.audio.VadAudioRecorder
import com.slothspeak.data.ApiKeyManager
import com.slothspeak.data.db.SlothSpeakDatabase
import com.slothspeak.service.SlothSpeakService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyManager = ApiKeyManager(application)
    private val database = SlothSpeakDatabase.getInstance(application)
    private val bluetoothRouter = BluetoothAudioRouter(application)
    private val audioFocusManager = AudioFocusManager(application).also {
        it.bluetoothRouter = bluetoothRouter
    }
    val audioRecorder = AudioRecorder(application)
    private var vadRecorder: VadAudioRecorder? = null
    private var vadObserverJob: Job? = null

    private var service: SlothSpeakService? = null
    private var serviceBound = false
    private var serviceDeferred: CompletableDeferred<SlothSpeakService>? = null
    private var stateObserverJob: Job? = null
    private var loadConversationJob: Job? = null

    // Current conversation tracking
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId

    private val _currentResponseId = MutableStateFlow<String?>(null)
    val currentResponseId: StateFlow<String?> = _currentResponseId

    // UI state
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    // Pipeline state mirrored from the service (reactive, survives recomposition)
    private val _pipelineState = MutableStateFlow<SlothSpeakService.PipelineState>(SlothSpeakService.PipelineState.Idle)
    val pipelineState: StateFlow<SlothSpeakService.PipelineState> = _pipelineState

    // Conversation display items for current session
    private val _sessionItems = MutableStateFlow<List<SessionItem>>(emptyList())
    val sessionItems: StateFlow<List<SessionItem>> = _sessionItems

    // Whether the user is currently viewing the active pipeline's output
    private val _isViewingActivePipeline = MutableStateFlow(false)
    val isViewingActivePipeline: StateFlow<Boolean> = _isViewingActivePipeline

    // Whether the service's pipeline is actively running (independent of replay/pipelineState)
    private val _isPipelineRunning = MutableStateFlow(false)
    val isPipelineRunning: StateFlow<Boolean> = _isPipelineRunning

    // Mute mode: processing completes but playback, ping, and follow-up are skipped
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    // Cancel confirmation dialog
    private val _showCancelConfirmation = MutableStateFlow(false)
    val showCancelConfirmation: StateFlow<Boolean> = _showCancelConfirmation

    data class UiState(
        val isRecording: Boolean = false,
        val hasApiKey: Boolean = false,
        val showRecordingOverlay: Boolean = false,
        val lowStorageWarning: Boolean = false,
        val serviceBindError: Boolean = false,
        val isInteractiveRecording: Boolean = false,
        val vadSpeechDetected: Boolean = false
    )

    data class SessionItem(
        val questionText: String,
        val answerText: String?,
        val responseId: String?,
        val audioFiles: List<File>?,
        val isProcessing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis(),
        val model: String? = null,
        val reasoningEffort: String? = null,
        val llmResponseSeconds: Int? = null,
        val answerTextRich: String? = null
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as SlothSpeakService.LocalBinder
            service = localBinder.getService()
            serviceBound = true
            serviceDeferred?.complete(service!!)
            observeServiceState()

            // Sync isPipelineRunning from service (only upward to true, never downward)
            val svc = service
            if (svc != null && svc.isPipelineRunning) {
                _isPipelineRunning.value = true
            }

            // Auto-reconnect on activity recreation: if pipeline is active and
            // we have no items, rebuild the UI from the current pipeline state
            if (svc != null && svc.isPipelineRunning && _sessionItems.value.isEmpty()) {
                _isViewingActivePipeline.value = true
                val currentState = svc.pipelineState.value
                _pipelineState.value = currentState
                val convId = svc.activeConversationId.value
                _currentConversationId.value = convId
                viewModelScope.launch {
                    rebuildSessionFromPipelineState(currentState, convId)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            serviceBound = false
            _isPipelineRunning.value = false
        }
    }

    init {
        _uiState.value = _uiState.value.copy(hasApiKey = apiKeyManager.hasKey())
        checkStorage()
        reconnectToActiveService()
    }

    fun refreshApiKeyStatus() {
        _uiState.value = _uiState.value.copy(hasApiKey = apiKeyManager.hasKey())
    }

    fun goHome() {
        loadConversationJob?.cancel()
        val svc = service

        // Only stop replay audio if a replay is actually superseding
        if (svc != null && svc.isReplaySuperseding()) {
            svc.stopReplayOnly()
        }

        // Clear conversation state — go to blank slate
        _currentConversationId.value = null
        _currentResponseId.value = null
        _sessionItems.value = emptyList()
        _isViewingActivePipeline.value = false

        if (_isPipelineRunning.value) {
            // Pipeline is actively processing/playing — keep service bound for indicator chip.
            // The observer is still running and will show the indicator.
            // Read current pipeline state so the chip can display status.
            if (svc != null) {
                _pipelineState.value = svc.pipelineState.value
            }
        } else {
            // No active pipeline — clean up fully
            _pipelineState.value = SlothSpeakService.PipelineState.Idle
            svc?.stopPlayback()
            unbindServiceSafely()
        }
    }

    fun returnToActivePipeline() {
        val svc = service ?: return
        if (!_isPipelineRunning.value) return

        // If a replay was superseding the pipeline's UI state, stop it and restore
        if (svc.isReplaySuperseding()) {
            svc.stopReplayOnly()
            svc.restorePipelineState()
        }

        _isViewingActivePipeline.value = true

        // Read current state directly — collector hasn't fired yet
        val currentState = svc.pipelineState.value
        _pipelineState.value = currentState
        val convId = svc.activeConversationId.value
        _currentConversationId.value = convId
        _currentResponseId.value = null

        // Load prior sessions from DB asynchronously, then append current item
        viewModelScope.launch {
            rebuildSessionFromPipelineState(currentState, convId)
        }
    }

    /**
     * Rebuilds session items by loading all prior Q&A pairs from the database,
     * then appending the current in-progress item from the pipeline state.
     * This preserves multi-session conversation history during auto-navigate.
     */
    private suspend fun rebuildSessionFromPipelineState(
        state: SlothSpeakService.PipelineState,
        conversationId: Long?
    ) {
        val question: String?
        val answer: String?
        val richText: String?
        when (state) {
            is SlothSpeakService.PipelineState.Transcribing -> {
                question = "Transcribing..."
                answer = null
                richText = null
            }
            is SlothSpeakService.PipelineState.Transcribed -> {
                question = state.questionText
                answer = null
                richText = null
            }
            is SlothSpeakService.PipelineState.Thinking -> {
                question = state.questionText
                answer = null
                richText = null
            }
            is SlothSpeakService.PipelineState.ThinkingComplete -> {
                question = state.questionText
                answer = state.answerText
                richText = state.answerTextRich
            }
            is SlothSpeakService.PipelineState.GeneratingAudio -> {
                question = state.questionText
                answer = state.answerText
                richText = state.answerTextRich
            }
            is SlothSpeakService.PipelineState.Playing -> {
                question = state.questionText
                answer = state.answerText
                richText = state.answerTextRich
            }
            is SlothSpeakService.PipelineState.Error -> {
                question = state.questionText ?: "Error"
                answer = state.answerText
                richText = state.answerTextRich
            }
            is SlothSpeakService.PipelineState.ListeningForFollowUp,
            is SlothSpeakService.PipelineState.InteractiveEnding -> {
                question = null
                answer = null
                richText = null
            }
            else -> {
                question = null
                answer = null
                richText = null
            }
        }

        // Load all prior completed Q&A pairs from the database
        val priorItems = if (conversationId != null) {
            try {
                val qaPairs = withContext(Dispatchers.IO) {
                    database.conversationDao().getQAPairsForConversationOnce(conversationId)
                }
                qaPairs.map { pair ->
                    val audioFiles = pair.audioFilePaths?.split(",")
                        ?.map { File(it.trim()) }
                        ?.filter { it.exists() }
                        ?.ifEmpty { null }
                    SessionItem(
                        questionText = pair.questionText,
                        answerText = pair.answerText,
                        responseId = pair.responseId,
                        audioFiles = audioFiles,
                        isProcessing = false,
                        timestamp = pair.createdAt,
                        model = pair.model,
                        reasoningEffort = pair.reasoningEffort,
                        llmResponseSeconds = pair.llmResponseSeconds,
                        answerTextRich = pair.answerTextRich
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load prior sessions for rebuild", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        if (question != null) {
            val currentItem = SessionItem(
                questionText = question,
                answerText = answer,
                responseId = null,
                audioFiles = null,
                isProcessing = true,
                answerTextRich = richText
            )
            // The current Q&A pair may already be in the DB (early save creates a
            // placeholder after transcription, final save updates it before playback).
            // If the last DB item matches the current question, replace it with the
            // processing version to avoid duplication.
            val items = priorItems.toMutableList()
            if (items.isNotEmpty() && items.last().questionText == question) {
                items[items.lastIndex] = currentItem
            } else {
                items.add(currentItem)
            }
            _sessionItems.value = items
        } else {
            // No current item (e.g. ListeningForFollowUp) — just show DB items
            _sessionItems.value = priorItems
        }
    }

    fun startNewQuestion() {
        loadConversationJob?.cancel()
        _currentConversationId.value = null
        _currentResponseId.value = null
        _sessionItems.value = emptyList()
        _isViewingActivePipeline.value = false  // Will be set true when pipeline starts
        _isMuted.value = false
        startRecording()
    }

    fun isFollowUpSupported(): Boolean {
        val model = apiKeyManager.getSelectedModel()
        return model != ApiKeyManager.MODEL_DEEP_RESEARCH &&
                model != ApiKeyManager.MODEL_GEMINI_DEEP_RESEARCH
    }

    fun startFollowUp() {
        startRecording()
    }

    private fun startRecording() {
        if (!apiKeyManager.hasKey()) return

        // Request exclusive audio focus to silence other apps during recording
        audioFocusManager.requestRecordingFocus()

        val isInteractive = apiKeyManager.getInteractiveVoiceEnabled()

        // Show recording overlay immediately
        _uiState.value = _uiState.value.copy(
            isRecording = true,
            showRecordingOverlay = true,
            isInteractiveRecording = isInteractive,
            vadSpeechDetected = false
        )

        // Launch coroutine to handle BT routing (suspend) then start recording
        viewModelScope.launch {
            // Enable Bluetooth mic routing if a BT headset is connected
            val btRouted = bluetoothRouter.startBluetoothRecordingRoute()
            val btDevice = if (btRouted) bluetoothRouter.findBluetoothInputDevice() else null
            // On API <31, use VOICE_COMMUNICATION audio source when BT SCO is active
            val useBtSource = btRouted && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S

            if (isInteractive) {
                // Use VAD-based recording for auto-stop
                val recorder = VadAudioRecorder(
                    context = getApplication(),
                    silenceAfterSpeechMs = apiKeyManager.getSilenceTimeoutMs(),
                    noSpeechTimeoutMs = 8000
                )
                vadRecorder = recorder

                val audioFile = recorder.startRecording(
                    viewModelScope,
                    preferredDevice = btDevice,
                    useBluetoothAudioSource = useBtSource
                )
                if (audioFile == null) {
                    // Failed to start VAD recorder, fall back to normal recording
                    vadRecorder = null
                    _uiState.value = _uiState.value.copy(isInteractiveRecording = false)
                    audioRecorder.startRecording(
                        viewModelScope,
                        preferredDevice = btDevice,
                        useBluetoothAudioSource = useBtSource
                    )
                    return@launch
                }

                // Observe VAD state for auto-stop
                vadObserverJob = launch {
                    // Monitor speech detection for UI updates
                    val uiMonitorJob = launch {
                        recorder.state.collect { vadState ->
                            _uiState.value = _uiState.value.copy(
                                vadSpeechDetected = vadState.isSpeechDetected
                            )
                        }
                    }

                    // Wait for a terminal VAD state
                    val finalState = recorder.state.first { vadState ->
                        vadState.speechEndedByVad || vadState.noSpeechDetected
                    }
                    uiMonitorJob.cancel()

                    when {
                        finalState.speechEndedByVad -> {
                            Log.d(TAG, "VAD auto-stop: speech ended")
                            playPingSound()
                            delay(2000) // Let ping finish before releasing focus
                            stopRecordingInternal(audioFile, discardSilent = false)
                        }
                        finalState.noSpeechDetected -> {
                            Log.d(TAG, "VAD auto-stop: no speech detected")
                            stopRecordingInternal(audioFile, discardSilent = true)
                        }
                    }
                }
            } else {
                audioRecorder.startRecording(
                    viewModelScope,
                    preferredDevice = btDevice,
                    useBluetoothAudioSource = useBtSource
                )
            }
        }
    }

    private fun bindAndAwaitService(): CompletableDeferred<SlothSpeakService> {
        val context = getApplication<Application>()
        val intent = Intent(context, SlothSpeakService::class.java)
        context.startForegroundService(intent)

        // If already bound and service is available, return immediately.
        // This prevents a timeout when onServiceConnected won't fire again
        // (e.g., after replay finishes but the binding was never released).
        service?.let { svc ->
            if (serviceBound) {
                return CompletableDeferred(svc)
            }
        }

        val deferred = CompletableDeferred<SlothSpeakService>()
        serviceDeferred = deferred

        val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            deferred.completeExceptionally(
                IllegalStateException("Failed to bind to SlothSpeakService")
            )
        }

        return deferred
    }

    fun stopRecording() {
        val recorder = vadRecorder
        if (recorder != null) {
            // VAD mode: stop VAD recorder and use its output file
            vadObserverJob?.cancel()
            vadObserverJob = null
            val audioFile = recorder.stopRecording()
            recorder.release()
            vadRecorder = null

            if (audioFile != null && audioFile.exists() && audioFile.length() > 44) {
                // Has data beyond WAV header
                stopRecordingInternal(audioFile, discardSilent = false)
            } else {
                // No meaningful audio, just clean up
                bluetoothRouter.stopBluetoothRecordingRoute()
                audioFocusManager.abandonFocus()
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    showRecordingOverlay = false,
                    isInteractiveRecording = false,
                    vadSpeechDetected = false
                )
            }
        } else {
            val audioFile = audioRecorder.stopRecording() ?: return
            stopRecordingInternal(audioFile, discardSilent = false)
        }
    }

    private fun playPingSound() {
        if (!apiKeyManager.getPingBeforeReading()) return
        if (!apiKeyManager.getInteractiveVoiceEnabled()) return
        try {
            val player = MediaPlayer.create(
                getApplication(),
                com.slothspeak.R.raw.notify
            )
            player?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play ping sound", e)
        }
    }

    private fun stopRecordingInternal(audioFile: File, discardSilent: Boolean) {
        vadObserverJob?.cancel()
        vadObserverJob = null
        vadRecorder?.release()
        vadRecorder = null

        // Release Bluetooth routing before releasing focus to avoid earpiece routing
        bluetoothRouter.stopBluetoothRecordingRoute()
        // Release recording focus so other apps can resume during transcription/thinking
        audioFocusManager.abandonFocus()
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            showRecordingOverlay = false,
            serviceBindError = false,
            isInteractiveRecording = false,
            vadSpeechDetected = false
        )

        if (discardSilent) {
            // No speech was detected, discard silently
            audioFile.delete()
            return
        }

        _isPipelineRunning.value = true
        val deferred = bindAndAwaitService()

        viewModelScope.launch {
            try {
                val svc = withTimeout(10_000) { deferred.await() }
                _isViewingActivePipeline.value = true
                svc.startPipeline(
                    audioFile,
                    _currentConversationId.value,
                    _currentResponseId.value
                )
            } catch (e: TimeoutCancellationException) {
                _isPipelineRunning.value = false
                Log.e(TAG, "Timed out waiting for service bind", e)
                _uiState.value = _uiState.value.copy(serviceBindError = true)
            } catch (e: Exception) {
                _isPipelineRunning.value = false
                Log.e(TAG, "Failed to bind service", e)
                _uiState.value = _uiState.value.copy(serviceBindError = true)
            }
        }
    }

    private fun observeServiceState() {
        stateObserverJob?.cancel()
        stateObserverJob = viewModelScope.launch {
            service?.pipelineState?.collect { state ->
                // Always mirror pipeline state (needed for indicator chip)
                _pipelineState.value = state

                // Handle lifecycle-critical states regardless of viewing mode.
                // Complete/Idle MUST always call unbindServiceSafely() to prevent
                // service leaks when the user navigates away during processing.
                when (state) {
                    is SlothSpeakService.PipelineState.Complete -> {
                        _isPipelineRunning.value = false
                        _isMuted.value = false
                        if (_isViewingActivePipeline.value) {
                            _currentConversationId.value = state.conversationId
                            _currentResponseId.value = state.responseId
                            updateLastItem(
                                state.questionText,
                                state.answerText,
                                state.responseId,
                                state.audioFiles,
                                isProcessing = false,
                                model = state.model,
                                reasoningEffort = state.reasoningEffort,
                                llmResponseSeconds = state.llmResponseSeconds,
                                answerTextRich = state.answerTextRich
                            )
                        }
                        _isViewingActivePipeline.value = false
                        unbindServiceSafely()
                        return@collect
                    }

                    is SlothSpeakService.PipelineState.Idle -> {
                        if (_isViewingActivePipeline.value) {
                            val items = _sessionItems.value
                            if (items.any { it.isProcessing }) {
                                _sessionItems.value = items.map { it.copy(isProcessing = false) }
                            }
                        }
                        // Only unbind if the real pipeline isn't running.
                        // Replay finishing emits Idle but the pipeline may still be processing.
                        if (!_isPipelineRunning.value) {
                            unbindServiceSafely()
                        }
                        return@collect
                    }

                    is SlothSpeakService.PipelineState.InteractiveEnding -> {
                        _isPipelineRunning.value = false
                        if (_isViewingActivePipeline.value) {
                            _currentConversationId.value = state.conversationId
                            _isViewingActivePipeline.value = false
                            val items = _sessionItems.value.toMutableList()
                            // Remove the stale "Transcribing..." placeholder from the dismissed follow-up
                            if (items.lastOrNull()?.isProcessing == true && items.lastOrNull()?.answerText == null) {
                                items.removeAt(items.lastIndex)
                            }
                            _sessionItems.value = items.map { it.copy(isProcessing = false) }
                        }
                        return@collect
                    }

                    is SlothSpeakService.PipelineState.Playing -> {
                        // Sync from service — replay doesn't touch isPipelineRunning,
                        // so it stays true during replay but false for pipeline's own playback
                        val svcRunning = service?.isPipelineRunning ?: false
                        _isPipelineRunning.value = svcRunning
                        val isReplay = service?.isReplaySuperseding() ?: false

                        if (!isReplay) {
                            // Pipeline's own playback starting — auto-navigate to active conversation
                            if (!_isViewingActivePipeline.value) {
                                loadConversationJob?.cancel()
                                _isViewingActivePipeline.value = true
                                val convId = service?.activeConversationId?.value
                                _currentConversationId.value = convId
                                _currentResponseId.value = state.responseId.ifEmpty { null }
                                rebuildSessionFromPipelineState(state, convId)
                            }
                            updateLastItem(
                                state.questionText,
                                state.answerText,
                                state.responseId,
                                state.audioFiles,
                                isProcessing = true,
                                answerTextRich = state.answerTextRich
                            )
                        }
                        // For replay Playing, skip UI mutations (user is viewing history)
                        return@collect
                    }

                    else -> { /* handled below */ }
                }

                // Sync isPipelineRunning from service for follow-up pipelines
                val svcRunning = service?.isPipelineRunning ?: false
                if (svcRunning && !_isPipelineRunning.value) {
                    _isPipelineRunning.value = true
                }

                // For non-terminal states, skip UI mutations if not viewing
                if (!_isViewingActivePipeline.value) return@collect

                when (state) {
                    is SlothSpeakService.PipelineState.Transcribing -> {
                        addOrUpdateProcessingItem("Transcribing...", null)
                    }

                    is SlothSpeakService.PipelineState.Transcribed -> {
                        addOrUpdateProcessingItem(state.questionText, null)
                    }

                    is SlothSpeakService.PipelineState.Thinking -> {
                        addOrUpdateProcessingItem(state.questionText, null)
                    }

                    is SlothSpeakService.PipelineState.ThinkingComplete -> {
                        addOrUpdateProcessingItem(state.questionText, state.answerText, answerTextRich = state.answerTextRich)
                    }

                    is SlothSpeakService.PipelineState.GeneratingAudio -> {
                        addOrUpdateProcessingItem(state.questionText, state.answerText, answerTextRich = state.answerTextRich)
                    }

                    is SlothSpeakService.PipelineState.ListeningForFollowUp -> {
                        _currentConversationId.value = state.conversationId
                        _currentResponseId.value = state.previousResponseId
                        val items = _sessionItems.value
                        if (items.any { it.isProcessing }) {
                            _sessionItems.value = items.map { it.copy(isProcessing = false) }
                        }
                    }

                    is SlothSpeakService.PipelineState.Error -> {
                        addOrUpdateProcessingItem(
                            state.questionText ?: "Error",
                            state.answerText,
                            isProcessing = false,
                            answerTextRich = state.answerTextRich
                        )
                    }

                    // Complete, Idle, InteractiveEnding already handled above
                    else -> {}
                }
            }
        }
    }

    private fun addOrUpdateProcessingItem(question: String, answer: String?, isProcessing: Boolean = true, answerTextRich: String? = null) {
        val items = _sessionItems.value.toMutableList()
        val lastItem = items.lastOrNull()
        if (lastItem?.isProcessing == true) {
            items[items.lastIndex] = lastItem.copy(
                questionText = question,
                answerText = answer ?: lastItem.answerText,
                isProcessing = isProcessing,
                answerTextRich = answerTextRich ?: lastItem.answerTextRich
            )
        } else {
            items.add(
                SessionItem(
                    questionText = question,
                    answerText = answer,
                    responseId = null,
                    audioFiles = null,
                    isProcessing = isProcessing,
                    answerTextRich = answerTextRich
                )
            )
        }
        _sessionItems.value = items
    }

    private fun updateLastItem(
        question: String,
        answer: String,
        responseId: String,
        audioFiles: List<File>,
        isProcessing: Boolean,
        model: String? = null,
        reasoningEffort: String? = null,
        llmResponseSeconds: Int? = null,
        answerTextRich: String? = null
    ) {
        val items = _sessionItems.value.toMutableList()
        // Find the target item to update:
        // 1. An item currently being processed (normal pipeline flow)
        // 2. An item matching this question text (history replay, where the
        //    replayed item may not be the last one)
        // 3. Fall back to the last item
        val targetIndex = items.indexOfLast { it.isProcessing }
            .takeIf { it >= 0 }
            ?: items.indexOfLast { it.questionText == question }
                .takeIf { it >= 0 }
            ?: items.lastIndex

        val newItem = SessionItem(
            questionText = question,
            answerText = answer,
            responseId = responseId,
            audioFiles = audioFiles,
            isProcessing = isProcessing,
            model = model,
            reasoningEffort = reasoningEffort,
            llmResponseSeconds = llmResponseSeconds,
            answerTextRich = answerTextRich
        )

        if (targetIndex >= 0) {
            // Preserve existing metadata (model, timing) when updating for replay
            // (replay Playing state doesn't carry model/reasoningEffort/llmResponseSeconds)
            val existing = items[targetIndex]
            items[targetIndex] = newItem.copy(
                model = model ?: existing.model,
                reasoningEffort = reasoningEffort ?: existing.reasoningEffort,
                llmResponseSeconds = llmResponseSeconds ?: existing.llmResponseSeconds,
                answerTextRich = answerTextRich ?: existing.answerTextRich,
                timestamp = existing.timestamp
            )
        } else {
            items.add(newItem)
        }
        _sessionItems.value = items
    }

    fun retryFromError() {
        service?.retryFromError()
    }

    fun cancelPipeline() {
        vadObserverJob?.cancel()
        vadObserverJob = null
        vadRecorder?.release()
        vadRecorder = null
        bluetoothRouter.stopBluetoothRecordingRoute()
        service?.cancelPipeline()
        audioFocusManager.abandonFocus()
        _isPipelineRunning.value = false
        _isViewingActivePipeline.value = false
        _isMuted.value = false
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            showRecordingOverlay = false,
            isInteractiveRecording = false,
            vadSpeechDetected = false
        )
        unbindServiceSafely()
    }

    fun requestCancelPipeline() {
        _showCancelConfirmation.value = true
    }

    fun confirmCancelPipeline() {
        _showCancelConfirmation.value = false
        cancelPipeline()
    }

    fun dismissCancelConfirmation() {
        _showCancelConfirmation.value = false
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        service?.isMuted = newMuted
    }

    fun cancelInteractiveListening() {
        service?.cancelFollowUpListening()
        val conversationId = _currentConversationId.value
        if (conversationId != null) {
            // Let the service handle transitioning to Idle
            service?.cancelPipeline()
        }
        _isPipelineRunning.value = false
        unbindServiceSafely()
    }

    fun replayAudio(files: List<File>, questionText: String = "", answerText: String = "") {
        _uiState.value = _uiState.value.copy(serviceBindError = false)
        val deferred = bindAndAwaitService()

        viewModelScope.launch {
            try {
                val svc = withTimeout(10_000) { deferred.await() }
                svc.replayAudio(files, questionText, answerText)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timed out waiting for service bind for replay", e)
                _uiState.value = _uiState.value.copy(serviceBindError = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind service for replay", e)
                _uiState.value = _uiState.value.copy(serviceBindError = true)
            }
        }
    }

    /**
     * Load a conversation from the database and display it on the main screen.
     * No audio playback is started — the user can tap play on individual Q&A pairs.
     */
    fun loadConversation(conversationId: Long) {
        // If loading the same conversation that the pipeline is actively processing,
        // return to the live pipeline view instead of loading stale DB data
        val svc = service
        if (svc != null && svc.activeConversationId.value == conversationId && svc.isPipelineRunning) {
            returnToActivePipeline()
            return
        }
        _isViewingActivePipeline.value = false

        loadConversationJob?.cancel()
        loadConversationJob = viewModelScope.launch {
            try {
                val qaPairs = withContext(Dispatchers.IO) {
                    database.conversationDao().getQAPairsForConversationOnce(conversationId)
                }

                val items = qaPairs.map { pair ->
                    val audioFiles = pair.audioFilePaths?.split(",")
                        ?.map { File(it.trim()) }
                        ?.filter { it.exists() }
                        ?.ifEmpty { null }
                    SessionItem(
                        questionText = pair.questionText,
                        answerText = pair.answerText,
                        responseId = pair.responseId,
                        audioFiles = audioFiles,
                        isProcessing = false,
                        timestamp = pair.createdAt,
                        model = pair.model,
                        reasoningEffort = pair.reasoningEffort,
                        llmResponseSeconds = pair.llmResponseSeconds,
                        answerTextRich = pair.answerTextRich
                    )
                }

                _sessionItems.value = items
                _currentConversationId.value = conversationId
                _currentResponseId.value = qaPairs.lastOrNull()?.responseId
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversation", e)
            }
        }
    }

    fun pausePlayback() = service?.pausePlayback()
    fun resumePlayback() = service?.resumePlayback()

    fun stopPlayback() {
        val svc = service ?: return

        if (svc.isReplaySuperseding()) {
            // Replay is active — stop it
            svc.stopReplayOnly()
            if (_isPipelineRunning.value) {
                // Pipeline still running in background — restore its state
                svc.restorePipelineState()
            } else {
                // No pipeline — go idle and unbind
                _pipelineState.value = SlothSpeakService.PipelineState.Idle
                unbindServiceSafely()
            }
            return
        }

        // Capture IDs from Playing state so follow-up is available after stop
        val currentState = _pipelineState.value
        if (currentState is SlothSpeakService.PipelineState.Playing) {
            if (currentState.responseId.isNotEmpty()) {
                _currentResponseId.value = currentState.responseId
            }
            if (currentState.conversationId >= 0) {
                _currentConversationId.value = currentState.conversationId
            }
        }
        val items = _sessionItems.value
        if (items.any { it.isProcessing }) {
            _sessionItems.value = items.map { it.copy(isProcessing = false) }
        }
        _isPipelineRunning.value = false
        svc.stopPlayback()
        unbindServiceSafely()
    }

    fun restartPlayback() = service?.restartPlayback()

    fun seekRelative(deltaMs: Int) = service?.seekRelative(deltaMs)

    fun getPlaybackPosition(): Long = service?.getPlaybackPosition() ?: 0L
    fun getPlaybackDuration(): Long = service?.getPlaybackDuration() ?: 0L

    fun setPlaybackSpeed(speed: Float) {
        service?.setPlaybackSpeed(speed)
        // Also persist directly in case service isn't bound yet
        apiKeyManager.setPlaybackSpeed(speed)
    }

    fun getPlaybackSpeed(): Float = apiKeyManager.getPlaybackSpeed()

    fun clearServiceBindError() {
        _uiState.value = _uiState.value.copy(serviceBindError = false)
    }

    /**
     * On init, try to bind to an already-running service (e.g., after Activity recreation).
     * Uses flags=0 (no BIND_AUTO_CREATE) so it only connects if the service is already running.
     * If the service is mid-pipeline, onServiceConnected will fire, observeServiceState()
     * will collect the current PipelineState, and the UI will be restored.
     */
    private fun reconnectToActiveService() {
        if (serviceBound) return
        val context = getApplication<Application>()
        val intent = Intent(context, SlothSpeakService::class.java)
        try {
            context.bindService(intent, serviceConnection, 0)
        } catch (_: Exception) {
        }
    }

    private fun checkStorage() {
        try {
            val stat = StatFs(getApplication<Application>().filesDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val availableMb = availableBytes / (1024 * 1024)
            _uiState.value = _uiState.value.copy(lowStorageWarning = availableMb < 100)
        } catch (_: Exception) {
        }
    }

    private fun unbindServiceSafely() {
        stateObserverJob?.cancel()
        stateObserverJob = null
        serviceDeferred?.cancel()
        serviceDeferred = null
        if (serviceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            serviceBound = false
        }
        service = null
    }

    override fun onCleared() {
        super.onCleared()
        vadObserverJob?.cancel()
        vadRecorder?.release()
        bluetoothRouter.release()
        audioRecorder.release()
        audioFocusManager.release()
        unbindServiceSafely()
    }

    companion object {
        private const val TAG = "ConversationViewModel"
    }
}
