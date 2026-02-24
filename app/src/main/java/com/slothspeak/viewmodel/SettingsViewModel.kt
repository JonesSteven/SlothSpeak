package com.slothspeak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slothspeak.config.SystemPrompts
import com.slothspeak.data.ApiKeyManager
import com.slothspeak.data.db.SlothSpeakDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyManager = ApiKeyManager(application)
    private val database = SlothSpeakDatabase.getInstance(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    data class SettingsState(
        val hasApiKey: Boolean = false,
        val selectedModel: String = ApiKeyManager.MODEL_PRO,
        val reasoningEffort: String = ApiKeyManager.EFFORT_HIGH,
        val audioStorageSizeMb: Long = 0,
        val conversationCount: Int = 0,
        val showDeleteKeyDialog: Boolean = false,
        val showClearHistoryDialog: Boolean = false,
        val showDeleteAllAudioDialog: Boolean = false,
        val keySaved: Boolean = false,
        val selectedVoice: String = ApiKeyManager.DEFAULT_VOICE,
        val pingBeforeReading: Boolean = false,
        val hasGeminiKey: Boolean = false,
        val geminiKeySaved: Boolean = false,
        val showDeleteGeminiKeyDialog: Boolean = false,
        val thinkingLevel: String = ApiKeyManager.THINKING_HIGH,
        val hasClaudeKey: Boolean = false,
        val claudeKeySaved: Boolean = false,
        val showDeleteClaudeKeyDialog: Boolean = false,
        val claudeEffort: String = ApiKeyManager.CLAUDE_EFFORT_HIGH,
        val hasGrokKey: Boolean = false,
        val grokKeySaved: Boolean = false,
        val showDeleteGrokKeyDialog: Boolean = false,
        val webSearchEnabled: Boolean = false,
        val xSearchEnabled: Boolean = false,
        val systemPrompt: String = SystemPrompts.SYSTEM_PROMPT,
        val deepResearchSystemPrompt: String = SystemPrompts.DEEP_RESEARCH_SYSTEM_PROMPT,
        val ttsInstructions: String = ApiKeyManager.DEFAULT_TTS_INSTRUCTIONS,
        val interactiveVoiceEnabled: Boolean = false,
        val silenceTimeoutMs: Int = ApiKeyManager.DEFAULT_SILENCE_TIMEOUT_MS,
        val followUpPrompt: String = SystemPrompts.FOLLOW_UP_PROMPT
    )

    init {
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch {
            val audioSize = withContext(Dispatchers.IO) { calculateAudioStorageSize() }
            val convCount = withContext(Dispatchers.IO) { database.conversationDao().getConversationCount() }
            _state.value = _state.value.copy(
                hasApiKey = apiKeyManager.hasKey(),
                selectedModel = apiKeyManager.getSelectedModel(),
                reasoningEffort = apiKeyManager.getReasoningEffort(),
                selectedVoice = apiKeyManager.getSelectedVoice(),
                pingBeforeReading = apiKeyManager.getPingBeforeReading(),
                audioStorageSizeMb = audioSize,
                conversationCount = convCount,
                keySaved = false,
                hasGeminiKey = apiKeyManager.hasGeminiKey(),
                geminiKeySaved = false,
                thinkingLevel = apiKeyManager.getThinkingLevel(),
                hasClaudeKey = apiKeyManager.hasClaudeKey(),
                claudeKeySaved = false,
                claudeEffort = apiKeyManager.getClaudeEffort(),
                hasGrokKey = apiKeyManager.hasGrokKey(),
                grokKeySaved = false,
                webSearchEnabled = apiKeyManager.getWebSearchEnabled(),
                xSearchEnabled = apiKeyManager.getXSearchEnabled(),
                systemPrompt = apiKeyManager.getSystemPrompt(),
                deepResearchSystemPrompt = apiKeyManager.getDeepResearchSystemPrompt(),
                ttsInstructions = apiKeyManager.getTtsInstructions(),
                interactiveVoiceEnabled = apiKeyManager.getInteractiveVoiceEnabled(),
                silenceTimeoutMs = apiKeyManager.getSilenceTimeoutMs(),
                followUpPrompt = apiKeyManager.getFollowUpPrompt()
            )
        }
    }

    fun saveApiKey(key: String) {
        if (key.isBlank()) return
        apiKeyManager.saveKey(key)
        _state.value = _state.value.copy(hasApiKey = true, keySaved = true)
    }

    fun showDeleteKeyDialog() {
        _state.value = _state.value.copy(showDeleteKeyDialog = true)
    }

    fun dismissDeleteKeyDialog() {
        _state.value = _state.value.copy(showDeleteKeyDialog = false)
    }

    fun confirmDeleteKey() {
        apiKeyManager.deleteKey()
        _state.value = _state.value.copy(
            hasApiKey = false,
            showDeleteKeyDialog = false,
            keySaved = false
        )
    }

    fun saveGeminiKey(key: String) {
        if (key.isBlank()) return
        apiKeyManager.saveGeminiKey(key)
        _state.value = _state.value.copy(hasGeminiKey = true, geminiKeySaved = true)
    }

    fun showDeleteGeminiKeyDialog() {
        _state.value = _state.value.copy(showDeleteGeminiKeyDialog = true)
    }

    fun dismissDeleteGeminiKeyDialog() {
        _state.value = _state.value.copy(showDeleteGeminiKeyDialog = false)
    }

    fun confirmDeleteGeminiKey() {
        apiKeyManager.deleteGeminiKey()
        _state.value = _state.value.copy(
            hasGeminiKey = false,
            showDeleteGeminiKeyDialog = false,
            geminiKeySaved = false
        )
    }

    fun setThinkingLevel(level: String) {
        apiKeyManager.setThinkingLevel(level)
        _state.value = _state.value.copy(thinkingLevel = level)
    }

    fun saveClaudeKey(key: String) {
        if (key.isBlank()) return
        apiKeyManager.saveClaudeKey(key)
        _state.value = _state.value.copy(hasClaudeKey = true, claudeKeySaved = true)
    }

    fun showDeleteClaudeKeyDialog() {
        _state.value = _state.value.copy(showDeleteClaudeKeyDialog = true)
    }

    fun dismissDeleteClaudeKeyDialog() {
        _state.value = _state.value.copy(showDeleteClaudeKeyDialog = false)
    }

    fun confirmDeleteClaudeKey() {
        apiKeyManager.deleteClaudeKey()
        _state.value = _state.value.copy(
            hasClaudeKey = false,
            showDeleteClaudeKeyDialog = false,
            claudeKeySaved = false
        )
    }

    fun setClaudeEffort(effort: String) {
        apiKeyManager.setClaudeEffort(effort)
        _state.value = _state.value.copy(claudeEffort = effort)
    }

    fun saveGrokKey(key: String) {
        if (key.isBlank()) return
        apiKeyManager.saveGrokKey(key)
        _state.value = _state.value.copy(hasGrokKey = true, grokKeySaved = true)
    }

    fun showDeleteGrokKeyDialog() {
        _state.value = _state.value.copy(showDeleteGrokKeyDialog = true)
    }

    fun dismissDeleteGrokKeyDialog() {
        _state.value = _state.value.copy(showDeleteGrokKeyDialog = false)
    }

    fun confirmDeleteGrokKey() {
        apiKeyManager.deleteGrokKey()
        _state.value = _state.value.copy(
            hasGrokKey = false,
            showDeleteGrokKeyDialog = false,
            grokKeySaved = false
        )
    }

    fun setModel(model: String) {
        apiKeyManager.setSelectedModel(model)
        _state.value = _state.value.copy(selectedModel = model)
    }

    fun setReasoningEffort(effort: String) {
        apiKeyManager.setReasoningEffort(effort)
        _state.value = _state.value.copy(reasoningEffort = effort)
    }

    fun setSelectedVoice(voice: String) {
        apiKeyManager.setSelectedVoice(voice)
        _state.value = _state.value.copy(selectedVoice = voice)
    }

    fun setPingBeforeReading(enabled: Boolean) {
        apiKeyManager.setPingBeforeReading(enabled)
        _state.value = _state.value.copy(pingBeforeReading = enabled)
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        apiKeyManager.setWebSearchEnabled(enabled)
        _state.value = _state.value.copy(webSearchEnabled = enabled)
    }

    fun setXSearchEnabled(enabled: Boolean) {
        apiKeyManager.setXSearchEnabled(enabled)
        _state.value = _state.value.copy(xSearchEnabled = enabled)
    }

    fun setSystemPrompt(prompt: String) {
        apiKeyManager.setSystemPrompt(prompt)
        _state.value = _state.value.copy(systemPrompt = prompt)
    }

    fun setDeepResearchSystemPrompt(prompt: String) {
        apiKeyManager.setDeepResearchSystemPrompt(prompt)
        _state.value = _state.value.copy(deepResearchSystemPrompt = prompt)
    }

    fun resetSystemPrompt() {
        apiKeyManager.resetSystemPrompt()
        _state.value = _state.value.copy(systemPrompt = SystemPrompts.SYSTEM_PROMPT)
    }

    fun resetDeepResearchSystemPrompt() {
        apiKeyManager.resetDeepResearchSystemPrompt()
        _state.value = _state.value.copy(deepResearchSystemPrompt = SystemPrompts.DEEP_RESEARCH_SYSTEM_PROMPT)
    }

    fun setTtsInstructions(instructions: String) {
        apiKeyManager.setTtsInstructions(instructions)
        _state.value = _state.value.copy(ttsInstructions = instructions)
    }

    fun resetTtsInstructions() {
        apiKeyManager.resetTtsInstructions()
        _state.value = _state.value.copy(ttsInstructions = ApiKeyManager.DEFAULT_TTS_INSTRUCTIONS)
    }

    fun setInteractiveVoiceEnabled(enabled: Boolean) {
        apiKeyManager.setInteractiveVoiceEnabled(enabled)
        _state.value = _state.value.copy(interactiveVoiceEnabled = enabled)
    }

    fun setSilenceTimeoutMs(ms: Int) {
        apiKeyManager.setSilenceTimeoutMs(ms)
        _state.value = _state.value.copy(silenceTimeoutMs = ms)
    }

    fun setFollowUpPrompt(prompt: String) {
        apiKeyManager.setFollowUpPrompt(prompt)
        _state.value = _state.value.copy(followUpPrompt = prompt)
    }

    fun resetFollowUpPrompt() {
        apiKeyManager.resetFollowUpPrompt()
        _state.value = _state.value.copy(followUpPrompt = SystemPrompts.FOLLOW_UP_PROMPT)
    }

    fun showDeleteAllAudioDialog() {
        _state.value = _state.value.copy(showDeleteAllAudioDialog = true)
    }

    fun dismissDeleteAllAudioDialog() {
        _state.value = _state.value.copy(showDeleteAllAudioDialog = false)
    }

    fun confirmDeleteAllAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete all audio files referenced in QA pairs
            val audioPaths = database.conversationDao().getAllAudioPaths()
            for (pathsString in audioPaths) {
                pathsString.split(",").forEach { path ->
                    File(path.trim()).delete()
                }
            }
            // Also delete the audio directory contents
            val audioDir = File(getApplication<Application>().filesDir, "audio")
            audioDir.deleteRecursively()

            // Clear audio path references from all QA pairs
            database.conversationDao().clearAllAudioPaths()

            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    showDeleteAllAudioDialog = false,
                    audioStorageSizeMb = 0
                )
            }
        }
    }

    fun showClearHistoryDialog() {
        _state.value = _state.value.copy(showClearHistoryDialog = true)
    }

    fun dismissClearHistoryDialog() {
        _state.value = _state.value.copy(showClearHistoryDialog = false)
    }

    fun confirmClearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete all audio files
            val audioPaths = database.conversationDao().getAllAudioPaths()
            for (pathsString in audioPaths) {
                pathsString.split(",").forEach { path ->
                    File(path.trim()).delete()
                }
            }
            // Also delete the audio directory contents
            val audioDir = File(getApplication<Application>().filesDir, "audio")
            audioDir.deleteRecursively()

            database.conversationDao().clearAll()

            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    showClearHistoryDialog = false,
                    audioStorageSizeMb = 0,
                    conversationCount = 0
                )
            }
        }
    }

    fun deleteConversationAudioOnly(conversationId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioPaths = database.conversationDao().getAudioPathsForConversation(conversationId)
            for (pathsString in audioPaths) {
                pathsString.split(",").forEach { path ->
                    File(path.trim()).delete()
                }
            }
            val qaPairs = database.conversationDao().getQAPairsForConversationOnce(conversationId)
            for (pair in qaPairs) {
                database.conversationDao().clearAudioForQAPair(pair.id)
            }
            withContext(Dispatchers.Main) {
                refreshState()
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioPaths = database.conversationDao().getAudioPathsForConversation(conversationId)
            for (pathsString in audioPaths) {
                pathsString.split(",").forEach { path ->
                    File(path.trim()).delete()
                }
            }
            database.conversationDao().deleteConversation(conversationId)
            withContext(Dispatchers.Main) {
                refreshState()
            }
        }
    }

    private fun calculateAudioStorageSize(): Long {
        val audioDir = File(getApplication<Application>().filesDir, "audio")
        if (!audioDir.exists()) return 0
        var totalBytes = 0L
        audioDir.walkTopDown().forEach { file ->
            if (file.isFile) totalBytes += file.length()
        }
        return totalBytes / (1024 * 1024) // Convert to MB
    }
}
