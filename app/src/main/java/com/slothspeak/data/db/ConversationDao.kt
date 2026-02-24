package com.slothspeak.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.slothspeak.data.db.entities.Conversation
import com.slothspeak.data.db.entities.QAPair
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert
    suspend fun insertConversation(conversation: Conversation): Long

    @Insert
    suspend fun insertQAPair(qaPair: QAPair): Long

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM qa_pairs WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getQAPairsForConversation(conversationId: Long): Flow<List<QAPair>>

    @Query("SELECT * FROM qa_pairs WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getQAPairsForConversationOnce(conversationId: Long): List<QAPair>

    @Query("SELECT * FROM qa_pairs ORDER BY createdAt DESC")
    fun getAllQAPairs(): Flow<List<QAPair>>

    @Query("UPDATE qa_pairs SET audioFilePaths = NULL WHERE id = :qaPairId")
    suspend fun clearAudioForQAPair(qaPairId: Long)

    @Query("DELETE FROM qa_pairs WHERE id = :qaPairId")
    suspend fun deleteQAPair(qaPairId: Long)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    @Query("DELETE FROM qa_pairs")
    suspend fun deleteAllQAPairs()

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Transaction
    suspend fun clearAll() {
        deleteAllQAPairs()
        deleteAllConversations()
    }

    @Query("UPDATE qa_pairs SET audioFilePaths = NULL WHERE audioFilePaths IS NOT NULL")
    suspend fun clearAllAudioPaths()

    @Query("SELECT audioFilePaths FROM qa_pairs WHERE audioFilePaths IS NOT NULL")
    suspend fun getAllAudioPaths(): List<String>

    @Query("SELECT audioFilePaths FROM qa_pairs WHERE conversationId = :conversationId AND audioFilePaths IS NOT NULL")
    suspend fun getAudioPathsForConversation(conversationId: Long): List<String>

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getConversationCount(): Int

    @Query("UPDATE conversations SET isFavorite = :isFavorite WHERE id = :conversationId")
    suspend fun setFavorite(conversationId: Long, isFavorite: Boolean)

    @Query("""UPDATE qa_pairs SET answerText = :answerText, audioFilePaths = :audioFilePaths,
        responseId = :responseId, model = :model, reasoningEffort = :reasoningEffort,
        llmResponseSeconds = :llmResponseSeconds, answerTextRich = :answerTextRich
        WHERE id = :qaPairId""")
    suspend fun updateQAPairResult(
        qaPairId: Long, answerText: String, audioFilePaths: String?,
        responseId: String, model: String, reasoningEffort: String?,
        llmResponseSeconds: Int?, answerTextRich: String?
    )

    @Query("SELECT COUNT(*) FROM qa_pairs WHERE conversationId = :conversationId")
    suspend fun getQAPairCountForConversation(conversationId: Long): Int
}
