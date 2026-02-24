package com.slothspeak.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.slothspeak.audio.AudioRecorder
import com.slothspeak.ui.components.RecordStopButton
import com.slothspeak.ui.theme.ErrorRed
import com.slothspeak.ui.theme.AccentGreen
import com.slothspeak.ui.theme.TextSecondary

@Composable
fun RecordingOverlay(
    audioRecorder: AudioRecorder,
    onStop: () -> Unit,
    isInteractive: Boolean = false,
    vadSpeechDetected: Boolean = false
) {
    val recordingState by audioRecorder.state.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "recording_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Recording indicator dot - green when speech detected in interactive mode
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .alpha(dotAlpha)
                    .background(
                        if (isInteractive && vadSpeechDetected) AccentGreen else ErrorRed,
                        CircleShape
                    )
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isInteractive) {
                // Interactive mode: show "Listening..." with VAD status
                Text(
                    text = "Listening...",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (vadSpeechDetected)
                        "Speaking... will auto-stop when you pause"
                    else
                        "Waiting for speech...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (vadSpeechDetected) AccentGreen else TextSecondary
                )
            } else {
                // Normal mode: countdown
                val remainingMs = (AudioRecorder.MAX_DURATION_MS - recordingState.durationMs)
                    .coerceAtLeast(0)
                val remainingSec = remainingMs / 1000
                val remainMin = remainingSec / 60
                val remainSec = remainingSec % 60
                Text(
                    text = String.format("%d:%02d", remainMin, remainSec),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time progress bar
                val progress = (recordingState.durationMs.toFloat() / AudioRecorder.MAX_DURATION_MS)
                    .coerceIn(0f, 1f)

                LinearProgressIndicator(
                    progress = progress,
                    color = if (remainingSec < 30) ErrorRed else AccentGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Stop button
            RecordStopButton(onClick = onStop)

            if (recordingState.autoStopped) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recording auto-stopped at 5 minute limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen
                )
            }
        }
    }
}
