package com.slothspeak.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "qa_pairs",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class QAPair(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val questionText: String,
    val answerText: String,
    val audioFilePaths: String? = null,
    val responseId: String,
    val model: String,
    val reasoningEffort: String? = null,
    val llmResponseSeconds: Int? = null,
    val answerTextRich: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
