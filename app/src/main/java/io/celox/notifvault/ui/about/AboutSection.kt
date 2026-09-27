package io.celox.notifvault.ui.about

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.celox.notifvault.BuildConfig
import io.celox.notifvault.R
import io.celox.notifvault.ui.theme.ThemeMode
import io.celox.notifvault.ui.theme.springPressed
import io.celox.notifvault.update.AppVersion
import io.celox.notifvault.update.UpdateCheckStore
import io.celox.notifvault.update.UpdateChecker
import io.celox.notifvault.update.UpdateScheduler

/**
 * Light / dark / system as a connected button group — the M3 Expressive replacement for a radio
 * list. Dynamic (wallpaper) color is offered only where the platform has it.
 */
@Composable
fun AppearanceSection(
    mode: ThemeMode,
    dynamicColor: Boolean,
    onMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit
) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, stringResource(R.string.appearance_system), Icons.Outlined.SettingsSuggest),
        Triple(ThemeMode.LIGHT, stringResource(R.string.appearance_light), Icons.Outlined.LightMode),
        Triple(ThemeMode.DARK, stringResource(R.string.appearance_dark), Icons.Outlined.DarkMode),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { i, (value, label, icon) ->
            val interaction = remember { MutableInteractionSource() }
            ToggleButton(
                checked = mode == value,
                onCheckedChange = { onMode(value) },
                interactionSource = interaction,
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).springPressed(interaction, 0.96f)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                Text(label, maxLines = 1)
            }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow(
            title = stringResource(R.string.appearance_dynamic_title),
            hint = stringResource(R.string.appearance_dynamic_hint),
            checked = dynamicColor,
            onToggle = { onDynamicColor(!dynamicColor) }
        )
    }
}

/**
 * The opt-in update check. Off by default; while off, the app makes no network request at all —
 * the switch is the only thing that ever uses the INTERNET permission.
 */
@Composable
fun UpdateSection(onOpenFailed: () -> Unit) {
    val context = LocalContext.current
    val tick by UpdateCheckStore.changes(context).collectAsState(initial = -1)
    val on = remember(tick) { UpdateCheckStore.isEnabled(context) }
    val latest = remember(tick) { UpdateCheckStore.latestSeen(context) }
    val newer = remember(tick) { UpdateChecker.bannerVersion(context) }
    // Asked only when the check is switched on; the in-app banner works without it.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    SwitchRow(
        title = stringResource(R.string.update_check_title),
        hint = stringResource(R.string.update_check_hint),
        checked = on,
        onToggle = {
            val enable = !on
            UpdateScheduler.setEnabled(context, enable)
            if (enable && needsNotificationPermission(context)) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )
    if (on) {
        Text(
            when {
                newer != null -> stringResource(R.string.update_status_available, newer)
                latest != null -> stringResource(R.string.update_status_current, AppVersion.display(latest))
                else -> stringResource(R.string.update_status_none)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (newer != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { UpdateScheduler.checkNow(context) }) { Text(stringResource(R.string.update_check_now)) }
            if (newer != null) {
                TextButton(onClick = { if (!UpdateChecker.openDownload(context)) onOpenFailed() }) {
                    Text(stringResource(R.string.update_download))
                }
            }
        }
    }
}

/**
 * Who made the app, where it lives, under which licence — and a way to say thanks. Every fact
 * comes from [AboutLinks] or BuildConfig; nothing here is typed by hand twice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutSection(onOpenFailed: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        if (!context.openUrl(url)) onOpenFailed()
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // The launcher mark on its own background — the same mark as the website.
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_launcher_background), null, tint = Color.Unspecified, modifier = Modifier.size(56.dp))
            Icon(painterResource(R.drawable.ic_launcher_foreground), null, tint = Color.Unspecified, modifier = Modifier.size(56.dp))
        }
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.about_made_by, AboutLinks.AUTHOR), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(R.string.about_tagline_long),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinkChip(stringResource(R.string.about_link_website), Icons.Outlined.Public) { open(AboutLinks.PRODUCT_URL) }
        LinkChip(AboutLinks.WEBSITE_LABEL, Icons.Outlined.Language) { open(AboutLinks.WEBSITE_URL) }
        LinkChip(stringResource(R.string.about_link_source), Icons.Outlined.Code) { open(AboutLinks.REPO_URL) }
        LinkChip(stringResource(R.string.about_link_changelog), Icons.Outlined.History) { open(AboutLinks.CHANGELOG_URL) }
        LinkChip(AboutLinks.LICENSE_NAME, Icons.Outlined.Description) { open(AboutLinks.LICENSE_URL) }
    }
    Text(
        stringResource(R.string.about_license, AboutLinks.LICENSE_NAME),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(16.dp))
    val donate = remember { MutableInteractionSource() }
    Button(
        onClick = { open(AboutLinks.donateUrl()) },
        interactionSource = donate,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp).springPressed(donate)
    ) {
        Icon(Icons.Outlined.Favorite, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.about_donate))
    }
    Text(
        stringResource(R.string.about_donate_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LinkChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        modifier = Modifier.height(40.dp)
    )
}

/** A whole-row toggle: the row is the touch target, the switch only shows the state. */
@Composable
fun SwitchRow(title: String, hint: String?, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Switch, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
