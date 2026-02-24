package com.slothspeak.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slothspeak.ui.theme.AccentGreen
import com.slothspeak.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun QAPairCard(
    questionText: String,
    answerText: String?,
    timestamp: Long,
    audioFiles: List<File>?,
    onReplayClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    model: String? = null,
    reasoningEffort: String? = null,
    llmResponseSeconds: Int? = null,
    answerTextRich: String? = null
) {
    val context = LocalContext.current
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val hasMetadata = model != null || llmResponseSeconds != null
    val displayAnswerText = answerTextRich ?: answerText

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Question
            Text(
                text = questionText,
                style = MaterialTheme.typography.bodyLarge,
                color = AccentGreen
            )

            if (displayAnswerText != null) {
                Spacer(modifier = Modifier.height(12.dp))

                // Answer
                Text(
                    text = displayAnswerText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (hasMetadata) {
                // Metadata section
                Column {
                    // Line 1: Date/time with ordinal suffix
                    Text(
                        text = formatTimestampWithOrdinal(timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    // Line 2: Model + reasoning effort
                    if (model != null) {
                        Text(
                            text = formatModelDisplay(model, reasoningEffort),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    // Line 3: Response time
                    if (llmResponseSeconds != null) {
                        Text(
                            text = "$llmResponseSeconds seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom row: copy + replay buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    if (displayAnswerText != null) {
                        IconButton(
                            onClick = { copyQAToClipboard(context, questionText, displayAnswerText) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = TextSecondary
                            )
                        }
                    }
                    if (audioFiles != null && onReplayClick != null) {
                        IconButton(
                            onClick = onReplayClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Replay",
                                tint = AccentGreen
                            )
                        }
                    }
                }
            } else {
                // Fallback for old records without metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormat.format(Date(timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (displayAnswerText != null) {
                        IconButton(
                            onClick = { copyQAToClipboard(context, questionText, displayAnswerText) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = TextSecondary
                            )
                        }
                    }

                    if (audioFiles != null && onReplayClick != null) {
                        IconButton(
                            onClick = onReplayClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Replay",
                                tint = AccentGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestampWithOrdinal(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val suffix = getDayOrdinalSuffix(day)
    val dateFormat = SimpleDateFormat("h:mm a MMM d", Locale.getDefault())
    return "${dateFormat.format(Date(timestamp))}$suffix"
}

private fun formatModelDisplay(model: String, reasoningEffort: String?): String {
    val displayName = when (model) {
        "gpt-5.2-pro" -> "GPT 5.2 Pro"
        "gpt-5.2" -> "GPT 5.2"
        "gemini-3.1-pro-preview" -> "Gemini 3.1 Pro"
        "claude-opus-4-6" -> "Claude Opus 4.6"
        "grok-4-0709" -> "Grok 4"
        "o3-deep-research" -> "Deep Research"
        "deep-research-pro-preview-12-2025" -> "Gemini Deep Research"
        else -> model.replace("gpt-", "GPT ").replace("-", " ")
    }
    return if (reasoningEffort != null) "$displayName $reasoningEffort" else displayName
}

private fun copyQAToClipboard(context: Context, question: String, answer: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = "Q: $question\n\nA: $answer"
    clipboard.setPrimaryClip(ClipData.newPlainText("SlothSpeak Q&A", text))
}

private fun getDayOrdinalSuffix(day: Int): String {
    return when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}
