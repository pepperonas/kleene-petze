package io.celox.notifvault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.celox.notifvault.ui.theme.Motion
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    hasAccess: Boolean,
    batteryOptimized: Boolean,
    onGrantAccess: () -> Unit,
    onBattery: () -> Unit,
    onContinue: () -> Unit
) {
    // Cascade the step cards in on first show — a small staggered spring entrance.
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(3) { delay(80); shown++ } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Kleene Petze", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Speichert eingehende Nachrichten-Benachrichtigungen dauerhaft und verschlüsselt – auch wenn der Absender sie später löscht.",
            style = MaterialTheme.typography.bodyMedium
        )

        StaggerIn(shown > 0) {
            StepCard(
                icon = Icons.Default.NotificationsActive,
                title = "Benachrichtigungszugriff",
                body = "Erlaube den Zugriff, damit Kleene Petze eingehende Nachrichten mitlesen und sichern kann.",
                done = hasAccess
            ) { Button(onClick = onGrantAccess) { Text(if (hasAccess) "Erneut öffnen" else "Zugriff erteilen") } }
        }

        StaggerIn(shown > 1) {
            StepCard(
                icon = Icons.Default.BatteryStd,
                title = "Akku-Optimierung ausnehmen",
                body = "Samsung beendet Hintergrunddienste sehr aggressiv. Ohne Ausnahme verpasst du Nachrichten.",
                done = batteryOptimized
            ) { OutlinedButton(onClick = onBattery) { Text("Ausnahme einrichten") } }
        }

        StaggerIn(shown > 2) {
            StepCard(
                icon = Icons.Default.Shield,
                title = "Lokal & verschlüsselt",
                body = "Alle Daten bleiben auf dem Gerät (SQLCipher / AES-256). Keine Cloud, kein Tracking.",
                done = true
            ) {}
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = hasAccess,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Loslegen") }
    }
}

@Composable
private fun StaggerIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.spatial()) { it / 3 } + fadeIn(Motion.effects())
    ) { content() }
}

@Composable
private fun StepCard(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    action: @Composable () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (done) Icons.Default.CheckCircle else icon, null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                Text("  $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodySmall)
            action()
        }
    }
}
