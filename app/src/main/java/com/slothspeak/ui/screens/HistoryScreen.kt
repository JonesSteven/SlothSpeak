package com.slothspeak.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slothspeak.ui.theme.ErrorRed
import com.slothspeak.ui.theme.AccentGreen
import com.slothspeak.ui.theme.TextSecondary
import com.slothspeak.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onLoadConversation: (conversationId: Long) -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "History",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AccentGreen
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavoritesFilter() }) {
                        Icon(
                            imageVector = if (showFavoritesOnly) Icons.Filled.Eco else Icons.Outlined.Eco,
                            contentDescription = if (showFavoritesOnly) "Show all conversations" else "Show favorites only",
                            tint = if (showFavoritesOnly) AccentGreen else TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (showFavoritesOnly) "No favorite conversations" else "No conversations yet",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (showFavoritesOnly) "Tap the leaf icon to favorite a conversation" else "Your Q&A history will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            // Conversation list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversations) { convPreview ->
                    ConversationListItem(
                        preview = convPreview,
                        dateFormat = dateFormat,
                        onClick = {
                            onLoadConversation(convPreview.conversation.id)
                        },
                        onToggleFavorite = {
                            viewModel.toggleFavorite(convPreview.conversation.id)
                        },
                        onDeleteAudio = {
                            viewModel.deleteAudioOnly(convPreview.conversation.id)
                        },
                        onDelete = {
                            viewModel.deleteConversation(convPreview.conversation.id)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ConversationListItem(
    preview: HistoryViewModel.ConversationWithPreview,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteAudio: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preview.firstQuestion.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormat.format(Date(preview.conversation.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (!preview.isProcessing && preview.hasAudio) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = "Has audio",
                            tint = AccentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (preview.qaPairCount > 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${preview.qaPairCount} exchanges",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (preview.isProcessing) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AccentGreen
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Processing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGreen
                        )
                    }
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (preview.isFavorite) Icons.Filled.Eco else Icons.Outlined.Eco,
                    contentDescription = if (preview.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (preview.isFavorite) AccentGreen else TextSecondary
                )
            }

            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete options",
                    tint = TextSecondary
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (preview.hasAudio) {
                    DropdownMenuItem(
                        text = { Text("Delete audio only") },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onDeleteAudio()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete conversation", color = ErrorRed) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = ErrorRed
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}
