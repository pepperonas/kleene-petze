package io.celox.notifvault.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.data.ConversationSummary
import io.celox.notifvault.service.NotificationCaptureService
import io.celox.notifvault.ui.theme.Motion
import io.celox.notifvault.util.findMatches

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: VaultViewModel,
    hasAccess: Boolean,
    onOpenConversation: (String, String) -> Unit,
    onOpenFlagged: () -> Unit,
    onOpenSettings: () -> Unit,
    onGrantAccess: () -> Unit
) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val listenerConnected by NotificationCaptureService.listenerConnected.collectAsStateWithLifecycle()
    // Saveable so rotation keeps the field and the ViewModel query in sync.
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { vm.setQuery(query) }
    // Per-app filter ("" = all); only offered once more than one app has conversations.
    var appFilter by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kleene Petze") },
                actions = {
                    IconButton(onClick = onOpenFlagged) {
                        Icon(Icons.Default.History, "Aufgedeckt")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Einstellungen")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!hasAccess) {
                CaptureBanner(
                    text = "Benachrichtigungszugriff fehlt — es wird nichts mehr gesichert.",
                    error = true,
                    actionLabel = "Erlauben",
                    onAction = onGrantAccess
                )
            } else if (!listenerConnected) {
                CaptureBanner(
                    text = "Erfassung derzeit inaktiv — das System hat den Dienst getrennt. " +
                        "Verbindet sich meist von selbst neu.",
                    error = false
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Nachrichten durchsuchen…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )

            // pkg → label, insertion-ordered by most recent chat (conversations is lastTime DESC).
            val apps = remember(conversations) {
                val m = LinkedHashMap<String, String>()
                for (c in conversations) m.putIfAbsent(c.packageName, c.appLabel)
                m
            }
            if (apps.size >= 2 && query.isBlank()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = appFilter.isEmpty(),
                        onClick = { appFilter = "" },
                        label = { Text("Alle") }
                    )
                    for ((pkg, label) in apps) {
                        FilterChip(
                            selected = appFilter == pkg,
                            onClick = { appFilter = if (appFilter == pkg) "" else pkg },
                            label = { Text(label) }
                        )
                    }
                }
            }
            val shown =
                if (appFilter.isEmpty()) conversations
                else conversations.filter { it.packageName == appFilter }

            when {
                query.isNotBlank() -> SearchResults(query, results, onOpenConversation)
                conversations.isEmpty() -> EmptyState(total)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    // Space separator keeps the composed key unambiguous (package names
                    // contain no spaces; bare concatenation could collide).
                    items(shown, key = { "${it.conversationKey} ${it.packageName}" }) { c ->
                        // Spring placement so a chat springing to the top on a new message
                        // (and rows above sliding down) reads as physical, not a hard cut.
                        Column(
                            Modifier.animateItem(
                                fadeInSpec = Motion.effects(),
                                placementSpec = Motion.spatial(),
                                fadeOutSpec = Motion.effects()
                            )
                        ) {
                            ConversationRow(c) { onOpenConversation(c.conversationKey, c.packageName) }
                            HorizontalDivider(
                                Modifier.padding(start = 76.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Capture-health banner: loud (error) when access is revoked, quiet when only unbound. */
@Composable
private fun CaptureBanner(
    text: String,
    error: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Warning, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    results: List<CapturedMessage>,
    onOpen: (String, String) -> Unit
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Keine Treffer für „$query\"", style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "${results.size} Treffer${if (results.size == 500) "+" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        items(results, key = { it.id }) { m ->
            Column(
                Modifier.animateItem(
                    fadeInSpec = Motion.effects(),
                    placementSpec = Motion.spatial(),
                    fadeOutSpec = Motion.effects()
                )
            ) {
                SearchResultRow(m, query) { onOpen(m.conversationKey, m.packageName) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun SearchResultRow(m: CapturedMessage, query: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableScale(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(m.conversation, m.isGroup, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.conversation,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatListTime(m.messageTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val prefix = if (m.isGroup) "${m.sender}: " else ""
            Text(
                highlight(prefix + m.text, query, MaterialTheme.colorScheme.primary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConversationRow(c: ConversationSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableScale(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(c.conversation, c.isGroup, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.conversation,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatListTime(c.lastTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.lastText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                if (c.deletedCount > 0) {
                    DeletedBadge(c.deletedCount)
                    Spacer(Modifier.width(6.dp))
                }
                if (c.editedCount > 0) {
                    EditedBadge(c.editedCount)
                    Spacer(Modifier.width(6.dp))
                }
                CountBadge(c.messageCount)
            }
            Text(
                c.appLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DeletedBadge(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            "🗑 $count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EditedBadge(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            "✏️ $count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            if (count > 999) "999+" else "$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyState(total: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Inbox, null, tint = MaterialTheme.colorScheme.primary)
            Text("Noch keine Nachrichten gesichert", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sobald eine WhatsApp-Benachrichtigung eingeht, taucht sie hier auf.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (total > 0) Text("Gespeichert: $total", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Bold + accent-color the matched substring(s) of [query] within [text] (case-insensitive). */
private fun highlight(text: String, query: String, color: Color): AnnotatedString {
    val ranges = findMatches(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var i = 0
        for (r in ranges) {
            append(text.substring(i, r.first))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(text.substring(r.first, r.last + 1))
            }
            i = r.last + 1
        }
        append(text.substring(i))
    }
}
