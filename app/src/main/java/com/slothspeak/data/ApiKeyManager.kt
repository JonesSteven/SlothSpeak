package com.slothspeak.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.slothspeak.config.SystemPrompts

class ApiKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getKey(): String? {
        return prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun deleteKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    fun hasKey(): Boolean {
        return getKey() != null
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_MODEL, MODEL_PRO) ?: MODEL_PRO
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
    }

    fun getReasoningEffort(): String {
        return prefs.getString(KEY_REASONING_EFFORT, EFFORT_HIGH) ?: EFFORT_HIGH
    }

    fun setReasoningEffort(effort: String) {
        prefs.edit().putString(KEY_REASONING_EFFORT, effort).apply()
    }

    fun getSelectedVoice(): String {
        return prefs.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
    }

    fun setSelectedVoice(voice: String) {
        prefs.edit().putString(KEY_VOICE, voice).apply()
    }

    fun getPingBeforeReading(): Boolean {
        return prefs.getBoolean(KEY_PING_BEFORE_READING, false)
    }

    fun setPingBeforeReading(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PING_BEFORE_READING, enabled).apply()
    }

    fun getWebSearchEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, false)
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEB_SEARCH_ENABLED, enabled).apply()
    }

    fun getXSearchEnabled(): Boolean {
        return prefs.getBoolean(KEY_XSEARCH_ENABLED, false)
    }

    fun setXSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_XSEARCH_ENABLED, enabled).apply()
    }

    fun getPlaybackSpeed(): Float {
        return prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
    }

    // Gemini API key methods
    fun saveGeminiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun getGeminiKey(): String? {
        return prefs.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun deleteGeminiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun hasGeminiKey(): Boolean {
        return getGeminiKey() != null
    }

    fun getThinkingLevel(): String {
        return prefs.getString(KEY_THINKING_LEVEL, THINKING_HIGH) ?: THINKING_HIGH
    }

    fun setThinkingLevel(level: String) {
        prefs.edit().putString(KEY_THINKING_LEVEL, level).apply()
    }

    // Claude API key methods
    fun saveClaudeKey(key: String) {
        prefs.edit().putString(KEY_CLAUDE_API_KEY, key.trim()).apply()
    }

    fun getClaudeKey(): String? {
        return prefs.getString(KEY_CLAUDE_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun deleteClaudeKey() {
        prefs.edit().remove(KEY_CLAUDE_API_KEY).apply()
    }

    fun hasClaudeKey(): Boolean {
        return getClaudeKey() != null
    }

    fun getClaudeEffort(): String {
        return prefs.getString(KEY_CLAUDE_EFFORT, CLAUDE_EFFORT_HIGH) ?: CLAUDE_EFFORT_HIGH
    }

    fun setClaudeEffort(effort: String) {
        prefs.edit().putString(KEY_CLAUDE_EFFORT, effort).apply()
    }

    // Grok (xAI) API key methods
    fun saveGrokKey(key: String) {
        prefs.edit().putString(KEY_GROK_API_KEY, key.trim()).apply()
    }

    fun getGrokKey(): String? {
        return prefs.getString(KEY_GROK_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun deleteGrokKey() {
        prefs.edit().remove(KEY_GROK_API_KEY).apply()
    }

    fun hasGrokKey(): Boolean {
        return getGrokKey() != null
    }

    // System prompt methods
    fun getSystemPrompt(): String {
        return prefs.getString(KEY_SYSTEM_PROMPT, null) ?: SystemPrompts.SYSTEM_PROMPT
    }

    fun setSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply()
    }

    fun resetSystemPrompt() {
        prefs.edit().remove(KEY_SYSTEM_PROMPT).apply()
    }

    fun getDeepResearchSystemPrompt(): String {
        return prefs.getString(KEY_DEEP_RESEARCH_SYSTEM_PROMPT, null) ?: SystemPrompts.DEEP_RESEARCH_SYSTEM_PROMPT
    }

    fun setDeepResearchSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_DEEP_RESEARCH_SYSTEM_PROMPT, prompt).apply()
    }

    fun resetDeepResearchSystemPrompt() {
        prefs.edit().remove(KEY_DEEP_RESEARCH_SYSTEM_PROMPT).apply()
    }

    // Interactive voice mode methods
    fun getInteractiveVoiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_INTERACTIVE_VOICE, false)
    }

    fun setInteractiveVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTERACTIVE_VOICE, enabled).apply()
    }

    fun getSilenceTimeoutMs(): Int {
        return prefs.getInt(KEY_SILENCE_TIMEOUT_MS, DEFAULT_SILENCE_TIMEOUT_MS)
    }

    fun setSilenceTimeoutMs(ms: Int) {
        prefs.edit().putInt(KEY_SILENCE_TIMEOUT_MS, ms.coerceIn(1000, 10000)).apply()
    }

    // TTS instructions methods
    fun getTtsInstructions(): String {
        return prefs.getString(KEY_TTS_INSTRUCTIONS, null) ?: DEFAULT_TTS_INSTRUCTIONS
    }

    fun setTtsInstructions(instructions: String) {
        prefs.edit().putString(KEY_TTS_INSTRUCTIONS, instructions).apply()
    }

    fun resetTtsInstructions() {
        prefs.edit().remove(KEY_TTS_INSTRUCTIONS).apply()
    }

    // Follow-up prompt methods
    fun getFollowUpPrompt(): String {
        return prefs.getString(KEY_FOLLOW_UP_PROMPT, null) ?: SystemPrompts.FOLLOW_UP_PROMPT
    }

    fun setFollowUpPrompt(prompt: String) {
        prefs.edit().putString(KEY_FOLLOW_UP_PROMPT, prompt).apply()
    }

    fun resetFollowUpPrompt() {
        prefs.edit().remove(KEY_FOLLOW_UP_PROMPT).apply()
    }

    companion object {
        private const val PREFS_FILE = "slothspeak_secure_prefs"
        private const val KEY_API_KEY = "openai_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_CLAUDE_EFFORT = "claude_effort"
        private const val KEY_GROK_API_KEY = "grok_api_key"
        private const val KEY_MODEL = "selected_model"
        private const val KEY_REASONING_EFFORT = "reasoning_effort"
        private const val KEY_THINKING_LEVEL = "gemini_thinking_level"
        private const val KEY_VOICE = "selected_voice"
        private const val KEY_PING_BEFORE_READING = "ping_before_reading"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_WEB_SEARCH_ENABLED = "web_search_enabled"
        private const val KEY_XSEARCH_ENABLED = "xsearch_enabled"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_DEEP_RESEARCH_SYSTEM_PROMPT = "deep_research_system_prompt"
        private const val KEY_TTS_INSTRUCTIONS = "tts_instructions"
        private const val KEY_FOLLOW_UP_PROMPT = "follow_up_prompt"
        private const val KEY_INTERACTIVE_VOICE = "interactive_voice_enabled"
        private const val KEY_SILENCE_TIMEOUT_MS = "silence_timeout_ms"
        const val DEFAULT_SILENCE_TIMEOUT_MS = 2000
        const val DEFAULT_VOICE = "marin"
        val VOICES = listOf(
            "alloy", "ash", "ballad", "coral", "echo", "fable",
            "nova", "onyx", "sage", "shimmer", "verse", "marin", "cedar"
        )
        const val MODEL_PRO = "gpt-5.2-pro"
        const val MODEL_STANDARD = "gpt-5.2"
        const val MODEL_GEMINI_PRO = "gemini-3.1-pro-preview"
        const val MODEL_CLAUDE_OPUS = "claude-opus-4-6"
        const val MODEL_GROK = "grok-4-0709"
        const val MODEL_DEEP_RESEARCH = "o3-deep-research"
        const val MODEL_GEMINI_DEEP_RESEARCH = "deep-research-pro-preview-12-2025"
        const val CLAUDE_EFFORT_LOW = "low"
        const val CLAUDE_EFFORT_MEDIUM = "medium"
        const val CLAUDE_EFFORT_HIGH = "high"
        const val CLAUDE_EFFORT_MAX = "max"
        const val EFFORT_MEDIUM = "medium"
        const val EFFORT_HIGH = "high"
        const val EFFORT_XHIGH = "xhigh"
        const val THINKING_LOW = "low"
        const val THINKING_MEDIUM = "medium"
        const val THINKING_HIGH = "high"
        const val DEFAULT_TTS_INSTRUCTIONS =
            "Read naturally and clearly, conversational tone, moderate pace, " +
            "subtle pauses at punctuation. Avoid dramatic emphasis."
    }
}
