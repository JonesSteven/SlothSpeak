package com.slothspeak.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slothspeak.data.db.SlothSpeakDatabase
import com.slothspeak.data.db.entities.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SlothSpeakDatabase.getInstance(application)

    private val _allConversations = MutableStateFlow<List<ConversationWithPreview>>(emptyList())

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly

    private val _conversations = MutableStateFlow<List<ConversationWithPreview>>(emptyList())
    val conversations: StateFlow<List<ConversationWithPreview>> = _conversations

    data class ConversationWithPreview(
        val conversation: Conversation,
        val firstQuestion: String,
        val qaPairCount: Int,
        val hasAudio: Boolean,
        val isFavorite: Boolean,
        val isProcessing: Boolean = false
    )

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            combine(
                database.conversationDao().getAllConversations(),
                database.conversationDao().getAllQAPairs()
            ) { convos, allPairs ->
                val pairsByConversation = allPairs.sortedBy { it.createdAt }
                    .groupBy { it.conversationId }
                convos.map { conv ->
                    val pairs = pairsByConversation[conv.id] ?: emptyList()
                    ConversationWithPreview(
                        conversation = conv,
                        firstQuestion = pairs.firstOrNull()?.questionText?.take(100) ?: "",
                        qaPairCount = pairs.size,
                        hasAudio = pairs.any { it.audioFilePaths != null },
                        isFavorite = conv.isFavorite,
                        isProcessing = pairs.any { it.answerText.isEmpty() }
                    )
                }
            }.collectLatest { previews ->
                _allConversations.value = previews
                applyFilter(previews)
            }
        }
    }

    private fun applyFilter(conversations: List<ConversationWithPreview> = _allConversations.value) {
        _conversations.value = if (_showFavoritesOnly.value) {
            conversations.filter { it.isFavorite }
        } else {
            conversations
        }
    }

    fun toggleFavoritesFilter() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
        applyFilter()
    }

    fun toggleFavorite(conversationId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _allConversations.value.find { it.conversation.id == conversationId }
                ?: return@launch
            val newValue = !current.isFavorite
            database.conversationDao().setFavorite(conversationId, newValue)
            // Update local state immediately for responsiveness
            val updatedAll = _allConversations.value.map {
                if (it.conversation.id == conversationId) {
                    it.copy(
                        conversation = it.conversation.copy(isFavorite = newValue),
                        isFavorite = newValue
                    )
                } else it
            }
            _allConversations.value = updatedAll
            applyFilter(updatedAll)
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
        }
    }

    fun deleteAudioOnly(conversationId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioPaths = database.conversationDao().getAudioPathsForConversation(conversationId)
            for (pathsString in audioPaths) {
                pathsString.split(",").forEach { path ->
                    File(path.trim()).delete()
                }
            }
            val pairs = database.conversationDao().getQAPairsForConversationOnce(conversationId)
            for (pair in pairs) {
                database.conversationDao().clearAudioForQAPair(pair.id)
            }
            // Update local state immediately so the audio icon disappears
            val updatedAll = _allConversations.value.map {
                if (it.conversation.id == conversationId) {
                    it.copy(hasAudio = false)
                } else it
            }
            _allConversations.value = updatedAll
            applyFilter(updatedAll)
        }
    }

}
