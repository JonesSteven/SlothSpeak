package com.slothspeak.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.slothspeak.service.SlothSpeakService
import com.slothspeak.ui.components.QAPairCard
import com.slothspeak.ui.components.StatusIndicator
import com.slothspeak.ui.theme.AccentGreen
import com.slothspeak.ui.theme.ErrorRed
import com.slothspeak.ui.theme.TextSecondary
import com.slothspeak.viewmodel.ConversationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ConversationViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val sessionItems by viewModel.sessionItems.collectAsState()
    val currentResponseId by viewModel.currentResponseId.collectAsState()
    val currentPipelineState by viewModel.pipelineState.collectAsState()
    val isViewingActivePipeline by viewModel.isViewingActivePipeline.collectAsState()
    val isPipelineRunning by viewModel.isPipelineRunning.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val showCancelConfirmation by viewModel.showCancelConfirmation.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Refresh API key status whenever this screen becomes visible (e.g. returning from settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshApiKeyStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var playbackSpeed by remember { mutableFloatStateOf(viewModel.getPlaybackSpeed()) }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasBluetoothPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // Track whether we've already asked for BT permission (don't re-ask if denied)
    var hasAskedBluetoothPermission by remember { mutableStateOf(hasBluetoothPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission =
                permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && permissions.containsKey(Manifest.permission.BLUETOOTH_CONNECT)) {
            hasBluetoothPermission =
                permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
            hasAskedBluetoothPermission = true
        }
        if (hasAudioPermission) {
            viewModel.startNewQuestion()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Microphone permission is required to record questions"
                )
            }
        }
    }

    // Show snackbar on service bind error
    LaunchedEffect(uiState.serviceBindError) {
        if (uiState.serviceBindError) {
            snackbarHostState.showSnackbar(
                "Failed to start service. Please try again."
            )
            viewModel.clearServiceBindError()
        }
    }

    // Auto-scroll when new items are added
    LaunchedEffect(sessionItems.size) {
        if (sessionItems.isNotEmpty()) {
            listState.animateScrollToItem(sessionItems.lastIndex)
        }
    }

    // Cancel confirmation dialog
    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCancelConfirmation() },
            title = { Text("Cancel processing?") },
            text = { Text("This will stop the current job. Any progress will be lost.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCancelPipeline() }) {
                    Text("Cancel Job", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCancelConfirmation() }) {
                    Text("Keep Running")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.goHome() }
                    ) {
                        Image(
                            painter = painterResource(id = com.slothspeak.R.drawable.ic_sloth),
                            contentDescription = "Sloth icon",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SlothSpeak",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                },
                actions = {
                    // Show indicator chip when pipeline is processing (hides once audio plays)
                    if (isPipelineRunning) {
                        ActivePipelineChip(
                            onClick = { viewModel.returnToActivePipeline() }
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "History",
                            tint = AccentGreen
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AccentGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!uiState.hasApiKey) {
                // No API key - prompt to set one
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Welcome to SlothSpeak",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Set your OpenAI API key in Settings to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateToSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("Open Settings")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.lowStorageWarning) {
                        Text(
                            text = "Low storage warning: less than 100 MB available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Conversation items
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (sessionItems.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Ask a question",
                                        style = MaterialTheme.typography.headlineMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap \"New Question\" to record your voice. " +
                                                "SlothSpeak will transcribe, think deeply, and read the answer aloud.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(32.dp))
                                    Image(
                                        painter = painterResource(id = com.slothspeak.R.drawable.ic_sloth),
                                        contentDescription = "SlothSpeak",
                                        modifier = Modifier.size(120.dp)
                                    )
                                }
                            }
                        }

                        items(sessionItems) { item ->
                            QAPairCard(
                                questionText = item.questionText,
                                answerText = item.answerText,
                                timestamp = item.timestamp,
                                audioFiles = item.audioFiles,
                                onReplayClick = item.audioFiles?.let { files ->
                                    { viewModel.replayAudio(files, item.questionText, item.answerText ?: "") }
                                },
                                model = item.model,
                                reasoningEffort = item.reasoningEffort,
                                llmResponseSeconds = item.llmResponseSeconds,
                                answerTextRich = item.answerTextRich
                            )
                        }

                        // Spacer at bottom for status indicator
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Pipeline status — show for active pipeline processing or any playback (incl. replay)
                    val showStatusIndicator = currentPipelineState !is SlothSpeakService.PipelineState.Idle &&
                        currentPipelineState !is SlothSpeakService.PipelineState.Complete &&
                        (isViewingActivePipeline || currentPipelineState is SlothSpeakService.PipelineState.Playing)
                    if (showStatusIndicator) {
                        StatusIndicator(
                            pipelineState = currentPipelineState,
                            onRetry = { viewModel.retryFromError() },
                            onCancel = { viewModel.requestCancelPipeline() },
                            onStop = { viewModel.stopPlayback() },
                            onRestartPlayback = { viewModel.restartPlayback() },
                            onRewind20s = { viewModel.seekRelative(-20_000) },
                            onForward30s = { viewModel.seekRelative(30_000) },
                            onPause = { viewModel.pausePlayback() },
                            onResume = { viewModel.resumePlayback() },
                            currentSpeed = playbackSpeed,
                            onGetPosition = { viewModel.getPlaybackPosition() },
                            onGetDuration = { viewModel.getPlaybackDuration() },
                            onSpeedChange = { speed ->
                                playbackSpeed = speed
                                viewModel.setPlaybackSpeed(speed)
                            },
                            onStopConversation = { viewModel.cancelInteractiveListening() },
                            isMuted = isMuted,
                            onToggleMute = { viewModel.toggleMute() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Bottom buttons - hidden during processing
                    val isProcessing = currentPipelineState !is SlothSpeakService.PipelineState.Idle &&
                            currentPipelineState !is SlothSpeakService.PipelineState.Complete &&
                            currentPipelineState !is SlothSpeakService.PipelineState.Error &&
                            currentPipelineState !is SlothSpeakService.PipelineState.InteractiveEnding

                    if (!isProcessing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = {
                                    val needsBtPermission = !hasBluetoothPermission && !hasAskedBluetoothPermission
                                    if (!hasAudioPermission || !hasNotificationPermission || needsBtPermission) {
                                        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                                        }
                                        permissionLauncher.launch(perms.toTypedArray())
                                    } else {
                                        viewModel.startNewQuestion()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("New Question")
                            }

                            if (currentResponseId != null &&
                                viewModel.isFollowUpSupported() &&
                                (currentPipelineState is SlothSpeakService.PipelineState.Complete ||
                                        currentPipelineState is SlothSpeakService.PipelineState.Idle)
                            ) {
                                Spacer(modifier = Modifier.width(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        val needsBtPerm = !hasBluetoothPermission && !hasAskedBluetoothPermission
                                        if (!hasAudioPermission || needsBtPerm) {
                                            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                                            }
                                            permissionLauncher.launch(perms.toTypedArray())
                                        } else {
                                            viewModel.startFollowUp()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = AccentGreen
                                    )
                                ) {
                                    Text("Follow Up")
                                }
                            }
                        }
                    }
                }
            }

            // Recording overlay
            if (uiState.showRecordingOverlay) {
                RecordingOverlay(
                    audioRecorder = viewModel.audioRecorder,
                    onStop = { viewModel.stopRecording() },
                    isInteractive = uiState.isInteractiveRecording,
                    vadSpeechDetected = uiState.vadSpeechDetected
                )
            }
        }
    }
}

@Composable
private fun ActivePipelineChip(
    onClick: () -> Unit
) {
    val statusText = "Thinking..."

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .alpha(pulseAlpha)
            .background(
                color = AccentGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Sync,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = AccentGreen
        )
    }
}
