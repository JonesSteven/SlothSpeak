package com.slothspeak.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.slothspeak.service.SlothSpeakService
import com.slothspeak.ui.theme.ErrorRed
import com.slothspeak.ui.theme.AccentGreen
import kotlinx.coroutines.delay

@Composable
fun StatusIndicator(
    pipelineState: SlothSpeakService.PipelineState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit = {},
    onRestartPlayback: () -> Unit = {},
    onRewind20s: () -> Unit = {},
    onForward30s: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    currentSpeed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {},
    onGetPosition: () -> Long = { 0L },
    onGetDuration: () -> Long = { 0L },
    onStopConversation: () -> Unit = {},
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (pipelineState) {
                is SlothSpeakService.PipelineState.Transcribing -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(pulseAlpha)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Transcribing...", style = MaterialTheme.typography.bodyLarge)
                }

                is SlothSpeakService.PipelineState.Transcribed -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Transcribed", style = MaterialTheme.typography.bodyLarge)
                }

                is SlothSpeakService.PipelineState.Thinking -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier
                                    .size(24.dp)
                                    .alpha(pulseAlpha)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val effortSuffix = if (pipelineState.reasoningEffort.isNotEmpty()) {
                                " (${pipelineState.reasoningEffort})"
                            } else ""
                            Text(
                                "${pipelineState.thinkingLabel}$effortSuffix...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        if (pipelineState.statusMessage.isNotEmpty()) {
                            val displayMessage = if (pipelineState.elapsedSeconds > 0) {
                                "${pipelineState.statusMessage} (${pipelineState.elapsedSeconds}s)"
                            } else {
                                pipelineState.statusMessage
                            }
                            Text(
                                displayMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is SlothSpeakService.PipelineState.ThinkingComplete -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Preparing audio...", style = MaterialTheme.typography.bodyLarge)
                }

                is SlothSpeakService.PipelineState.GeneratingAudio -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Generating audio (${pipelineState.completedChunks}/${pipelineState.totalChunks})...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                is SlothSpeakService.PipelineState.Playing -> {
                    var positionMs by remember { mutableLongStateOf(onGetPosition()) }
                    var durationMs by remember { mutableLongStateOf(onGetDuration()) }

                    LaunchedEffect(pipelineState.isPaused) {
                        while (true) {
                            positionMs = onGetPosition()
                            durationMs = onGetDuration()
                            if (pipelineState.isPaused) break
                            delay(500)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (pipelineState.isPaused) Icons.Default.Pause else Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier
                                    .size(24.dp)
                                    .then(if (!pipelineState.isPaused) Modifier.alpha(pulseAlpha) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val timeStr = "${formatTime(positionMs)} / ${formatTime(durationMs)}"
                            Text(
                                if (pipelineState.isPaused) {
                                    "Paused $timeStr"
                                } else {
                                    "Playing $timeStr"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        PlaybackControlsRow(
                            isPaused = pipelineState.isPaused,
                            onRestart = onRestartPlayback,
                            onRewind = onRewind20s,
                            onPauseResume = if (pipelineState.isPaused) onResume else onPause,
                            onForward = onForward30s,
                            onStop = onStop
                        )
                        SpeedButton(
                            currentSpeed = currentSpeed,
                            onSpeedChange = onSpeedChange
                        )
                    }
                }

                is SlothSpeakService.PipelineState.ListeningForFollowUp -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (pipelineState.isSpeechDetected) AccentGreen else AccentGreen.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .alpha(pulseAlpha)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                when {
                                    pipelineState.isPromptPlaying -> "Do you have a follow-up?"
                                    pipelineState.isSpeechDetected -> "Listening..."
                                    else -> "Waiting for speech..."
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        if (pipelineState.isListening) {
                            Text(
                                if (pipelineState.isSpeechDetected)
                                    "Will auto-stop when you pause"
                                else
                                    "Say something or stay silent to end",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onStopConversation) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop Conversation", color = ErrorRed)
                        }
                    }
                }

                is SlothSpeakService.PipelineState.InteractiveEnding -> {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Conversation complete", style = MaterialTheme.typography.bodyLarge)
                }

                is SlothSpeakService.PipelineState.Error -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            pipelineState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed
                        )
                        if (pipelineState.isRetryable) {
                            TextButton(onClick = onRetry) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", color = AccentGreen)
                            }
                        }
                    }
                }

                else -> { /* Idle, Complete - not shown */ }
            }

            // Mute toggle + Cancel button for active states (except Playing/ListeningForFollowUp which have their own controls)
            if (pipelineState !is SlothSpeakService.PipelineState.Error &&
                pipelineState !is SlothSpeakService.PipelineState.Idle &&
                pipelineState !is SlothSpeakService.PipelineState.Complete &&
                pipelineState !is SlothSpeakService.PipelineState.Playing &&
                pipelineState !is SlothSpeakService.PipelineState.ListeningForFollowUp &&
                pipelineState !is SlothSpeakService.PipelineState.InteractiveEnding
            ) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleMute, modifier = Modifier.size(32.dp)) {
                    Text(
                        text = if (isMuted) "\uD83D\uDD07" else "\uD83D\uDD0A",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isMuted)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else
                            AccentGreen
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = "Cancel",
                        tint = ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackControlsRow(
    isPaused: Boolean,
    onRestart: () -> Unit,
    onRewind: () -> Unit,
    onPauseResume: () -> Unit,
    onForward: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onRestart, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Restart",
                tint = AccentGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onRewind, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.FastRewind,
                contentDescription = "Rewind 20s",
                tint = AccentGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onPauseResume, modifier = Modifier.size(40.dp)) {
            Icon(
                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume" else "Pause",
                tint = AccentGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onForward, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.FastForward,
                contentDescription = "Forward 30s",
                tint = AccentGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "Stop",
                tint = ErrorRed,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
private fun SpeedButton(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        TextButton(
            onClick = {
                val currentIndex = SPEED_OPTIONS.indexOfFirst {
                    kotlin.math.abs(it - currentSpeed) < 0.01f
                }.takeIf { it >= 0 } ?: 2 // default to 1.0x index
                val nextIndex = (currentIndex + 1) % SPEED_OPTIONS.size
                onSpeedChange(SPEED_OPTIONS[nextIndex])
            }
        ) {
            val displaySpeed = if (currentSpeed == currentSpeed.toLong().toFloat()) {
                "${currentSpeed.toLong()}.0x"
            } else {
                "${currentSpeed}x"
            }
            Text(
                text = displaySpeed,
                color = AccentGreen,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
