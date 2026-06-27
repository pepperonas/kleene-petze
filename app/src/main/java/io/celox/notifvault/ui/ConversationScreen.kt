package io.celox.notifvault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.ui.theme.Motion

private sealed interface ChatItem {
    data class DayHeader(val key: Long, val label: String) : ChatItem
    /** [showSender] is true only for the first bubble of a sender run (group chats). */
    data class Bubble(val msg: CapturedMessage, val showSender: Boolean) : ChatItem
}

/** Build date separators + sender-run grouping once, off the composition hot path. */
private fun buildChatItems(messages: List<CapturedMessage>): List<ChatItem> {
    val out = ArrayList<ChatItem>(messages.size + 8)
    var lastDay = Long.MIN_VALUE
    var lastSender: String? = null
    for (m in messages) {
        val day = dayKey(m.messageTime)
        if (day != lastDay) {
            out += ChatItem.DayHeader(day, formatDayHeader(m.messageTime))
            lastDay = day
            lastSender = null // re-show the sender after a date break
        }
        val showSender = m.isGroup && m.sender != lastSender
        out += ChatItem.Bubble(m, showSender)
        lastSender = m.sender
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    vm: VaultViewModel,
    conversationKey: String,
    pkg: String,
    onBack: () -> Unit
) {
    // remember keyed by chat so the Flow isn't rebuilt (and the collection reset) on every recomposition.
    val messages by remember(conversationKey, pkg) { vm.messagesFor(conversationKey, pkg) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // The chat's display title is the latest title we captured for it (it can change over time).
    val title = messages.lastOrNull()?.conversation ?: ""
    val chatItems = remember(messages) { buildChatItems(messages) }
    val listState = rememberLazyListState()
    var confirmDelete by remember { mutableStateOf(false) }

    // Open at the newest message (chat convention), but only once the data first arrives —
    // so we don't yank the user back to the bottom while they scroll through history.
    var initialized by remember(conversationKey, pkg) { mutableStateOf(false) }
    LaunchedEffect(chatItems.size) {
        if (!initialized && chatItems.isNotEmpty()) {
            listState.scrollToItem(chatItems.lastIndex)
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "${messages.size} Nachricht${if (messages.size == 1) "" else "en"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.DeleteOutline, "Chat löschen")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(
                items = chatItems,
                key = {
                    when (it) {
                        is ChatItem.DayHeader -> "h${it.key}"
                        is ChatItem.Bubble -> it.msg.id
                    }
                },
                contentType = { it::class }
            ) { item ->
                Box(
                    Modifier.animateItem(
                        fadeInSpec = Motion.effects(),
                        placementSpec = Motion.spatial(),
                        fadeOutSpec = Motion.effects()
                    )
                ) {
                    when (item) {
                        is ChatItem.DayHeader -> DaySeparator(item.label)
                        is ChatItem.Bubble -> MessageBubble(item.msg, item.showSender)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Chat löschen?") },
            text = {
                Text("Alle ${messages.size} gespeicherten Nachrichten aus „$title\" " +
                    "werden unwiderruflich gelöscht.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteConversation(conversationKey, pkg)
                    onBack()
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(m: CapturedMessage, showSender: Boolean) {
    val deleted = m.deletionSuspected
    val bubbleColor = if (deleted)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val onBubble = if (deleted)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth().padding(top = if (showSender) 6.dp else 0.dp)) {
        // Received bubbles are start-aligned with a small top-left "tail".
        Surface(
            color = bubbleColor,
            contentColor = onBubble,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (showSender) {
                    Text(
                        m.sender,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = identityColor(m.sender)
                    )
                }
                Text(m.text, style = MaterialTheme.typography.bodyLarge)

                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (deleted) {
                        Icon(
                            Icons.Outlined.Block,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp).size(14.dp),
                            tint = onBubble.copy(alpha = 0.7f)
                        )
                        Text(
                            "gelöscht",
                            style = MaterialTheme.typography.labelSmall,
                            color = onBubble.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                    }
                    Text(
                        formatClock(m.messageTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = onBubble.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
