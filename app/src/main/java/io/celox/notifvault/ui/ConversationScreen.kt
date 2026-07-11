package io.celox.notifvault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.ui.theme.Motion
import io.celox.notifvault.util.shareExport
import kotlinx.coroutines.launch

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
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, "Mehr")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Chat als CSV teilen") },
                                onClick = {
                                    menuOpen = false
                                    val snapshot = messages
                                    scope.launch {
                                        shareExport(context, snapshot, csv = true, fileBaseName = exportName(title))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chat als JSON teilen") },
                                onClick = {
                                    menuOpen = false
                                    val snapshot = messages
                                    scope.launch {
                                        shareExport(context, snapshot, csv = false, fileBaseName = exportName(title))
                                    }
                                }
                            )
                        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(m: CapturedMessage, showSender: Boolean) {
    val deleted = m.deletionSuspected
    val edited = m.editSuperseded
    // Deleted wins visually over edited (an edited-then-deleted message shows as deleted).
    val bubbleColor = when {
        deleted -> MaterialTheme.colorScheme.errorContainer
        edited -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onBubble = when {
        deleted -> MaterialTheme.colorScheme.onErrorContainer
        edited -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = if (showSender) 6.dp else 0.dp)) {
        Box {
            // Received bubbles are start-aligned with a small top-left "tail".
            Surface(
                color = bubbleColor,
                contentColor = onBubble,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    )
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
                        if (deleted || edited) {
                            Icon(
                                if (deleted) Icons.Outlined.Block else Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp).size(14.dp),
                                tint = onBubble.copy(alpha = 0.7f)
                            )
                            Text(
                                if (deleted) "gelöscht" else "bearbeitet (frühere Version)",
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
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Kopieren") },
                    onClick = { menuOpen = false; copySensitive(context, m.text) }
                )
                DropdownMenuItem(
                    text = { Text("Teilen") },
                    onClick = {
                        menuOpen = false
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, m.text)
                        }
                        context.startActivity(Intent.createChooser(share, "Nachricht teilen"))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Details") },
                    onClick = { menuOpen = false; showDetails = true }
                )
            }
        }
    }

    if (showDetails) {
        MessageDetailsDialog(m) { showDetails = false }
    }
}

/** Copy to the clipboard, flagged sensitive (API 33+) so the OS suppresses the preview. */
private fun copySensitive(context: android.content.Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Nachricht", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescriptionCompat.EXTRA_IS_SENSITIVE, true)
        }
    }
    cm.setPrimaryClip(clip)
}

// ClipDescription.EXTRA_IS_SENSITIVE is API 33; the string constant is stable, so reference
// it in a way that compiles against minSdk 26.
private object ClipDescriptionCompat {
    const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}

@Composable
private fun MessageDetailsDialog(m: CapturedMessage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nachrichtendetails") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("Absender", m.sender)
                DetailRow("App", m.appLabel)
                DetailRow("Gesendet", formatTimestamp(m.messageTime))
                // capturedAt is the forensic anchor: when *we* saved it, independent of the
                // sender-controlled message timestamp.
                DetailRow("Erfasst", formatTimestamp(m.capturedAt))
                if (m.deletionSuspected) DetailRow("Status", "Vom Absender gelöscht 🗑")
                else if (m.editSuperseded) DetailRow("Status", "Vom Absender bearbeitet ✏️ (frühere Version)")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Safe file base name for a per-chat export ("Anna & Ben 👨‍👩‍👧" → kleene-petze_Anna_Ben). */
private fun exportName(title: String): String {
    val safe = title.replace(Regex("[^A-Za-z0-9äöüÄÖÜß]+"), "_").trim('_').take(40)
    return if (safe.isEmpty()) "kleene-petze_chat" else "kleene-petze_$safe"
}
