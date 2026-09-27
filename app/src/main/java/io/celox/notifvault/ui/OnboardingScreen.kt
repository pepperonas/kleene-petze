package io.celox.notifvault.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.celox.notifvault.R
import io.celox.notifvault.ui.theme.Ink
import io.celox.notifvault.ui.theme.springEntrance
import io.celox.notifvault.ui.theme.springPressed
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // The mascot on its own night sky (the artwork's background) — in the light theme too,
        // where it reads as a framed picture rather than a cut-out.
        Surface(
            color = Ink,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().springEntrance(0)
        ) {
            Image(
                painterResource(R.drawable.petze_mascot),
                contentDescription = stringResource(R.string.onboarding_mascot_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(vertical = 8.dp)
            )
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmallEmphasized,
            modifier = Modifier.springEntrance(1)
        )
        Text(
            stringResource(R.string.general_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.springEntrance(2)
        )
        Text(
            stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyMedium
        )

        StaggerIn(shown > 0) {
            StepCard(
                icon = Icons.Default.NotificationsActive,
                title = stringResource(R.string.onboarding_access_title),
                body = stringResource(R.string.onboarding_access_body),
                done = hasAccess
            ) { Button(onClick = onGrantAccess) { Text(stringResource(if (hasAccess) R.string.onboarding_access_reopen else R.string.onboarding_access_grant)) } }
        }

        StaggerIn(shown > 1) {
            StepCard(
                icon = Icons.Default.BatteryStd,
                title = stringResource(R.string.onboarding_battery_title),
                body = stringResource(R.string.onboarding_battery_body),
                done = batteryOptimized
            ) { OutlinedButton(onClick = onBattery) { Text(stringResource(R.string.onboarding_battery_action)) } }
        }

        StaggerIn(shown > 2) {
            StepCard(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.onboarding_local_title),
                body = stringResource(R.string.onboarding_local_body),
                done = true
            ) {}
        }

        Spacer(Modifier.height(8.dp))
        val go = remember { MutableInteractionSource() }
        Button(
            onClick = onContinue,
            enabled = hasAccess,
            interactionSource = go,
            modifier = Modifier.fillMaxWidth().height(56.dp).springPressed(go)
        ) { Text(stringResource(R.string.onboarding_continue), style = MaterialTheme.typography.titleMedium) }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large
    ) {
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
