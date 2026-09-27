package io.celox.notifvault.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.celox.notifvault.ui.theme.springEntrance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.celox.notifvault.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.ui.theme.Motion

/**
 * Global "Aufgedeckt" view: every message the vault caught the sender deleting, plus every
 * earlier version of an edited message — across all chats, newest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlaggedScreen(
    vm: VaultViewModel,
    onOpenConversation: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val flagged by vm.flagged.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.flagged_title)) },
                subtitle = {
                    Text(pluralStringResource(R.plurals.flagged_count, flagged.size, flagged.size))
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.flagged_back))
                    }
                }
            )
        }
    ) { pad ->
        if (flagged.isEmpty()) {
            EmptyFlagged(Modifier.padding(pad))
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                itemsIndexed(flagged, key = { _, it -> it.id }) { i, m ->
                    Column(
                        Modifier.animateItem(
                            fadeInSpec = Motion.effects(),
                            placementSpec = Motion.spatial(),
                            fadeOutSpec = Motion.effects()
                        ).springEntrance(i)
                    ) {
                        FlaggedRow(m) { onOpenConversation(m.conversationKey, m.packageName) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FlaggedRow(m: CapturedMessage, onClick: () -> Unit) {
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
                prefix + m.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.deletionSuspected) {
                    FlagBadge(stringResource(R.string.flagged_badge_deleted), error = true)
                    Spacer(Modifier.width(6.dp))
                }
                if (m.editSuperseded) {
                    FlagBadge(stringResource(R.string.flagged_badge_edited), error = false)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    m.appLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun FlagBadge(label: String, error: Boolean) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyFlagged(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.VisibilityOff, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.flagged_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.flagged_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
