package io.celox.notifvault.ui

import android.net.Uri
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import io.celox.notifvault.ui.about.AboutSection
import io.celox.notifvault.ui.about.AppearanceSection
import io.celox.notifvault.ui.about.SwitchRow
import io.celox.notifvault.ui.about.UpdateSection
import io.celox.notifvault.ui.theme.ThemeMode
import io.celox.notifvault.ui.theme.springEntrance
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.notifvault.BuildConfig
import io.celox.notifvault.R
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.data.RetentionPolicy
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.service.NotificationCaptureService
import io.celox.notifvault.service.ListenerWatchdog
import io.celox.notifvault.service.WatchdogPolicy
import io.celox.notifvault.ui.theme.Motion
import io.celox.notifvault.util.ExportNaming
import io.celox.notifvault.util.PermissionUtils
import io.celox.notifvault.util.VaultBackup
import io.celox.notifvault.util.VaultFormat
import io.celox.notifvault.util.VaultTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: VaultViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureAll by vm.settings.captureAll.collectAsStateWithLifecycle(initialValue = false)
    val monitored by vm.settings.monitoredPackages.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_PACKAGES)
    val biometric by vm.settings.biometricLock.collectAsStateWithLifecycle(initialValue = false)
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val lastCapture by vm.settings.lastCaptureAt.collectAsStateWithLifecycle(initialValue = 0L)
    val retention by vm.settings.retentionDays.collectAsStateWithLifecycle(initialValue = 0)
    val listenerConnected by NotificationCaptureService.listenerConnected.collectAsStateWithLifecycle()
    val autoStart by vm.settings.autoStartOnBoot.collectAsStateWithLifecycle(initialValue = true)
    val lastWatchdog by vm.settings.lastWatchdogAt.collectAsStateWithLifecycle(initialValue = 0L)
    val captureImages by vm.settings.captureImages.collectAsStateWithLifecycle(initialValue = true)
    val imageCount by vm.attachmentCount.collectAsStateWithLifecycle()
    val imageBytes by vm.attachmentBytesTotal.collectAsStateWithLifecycle()
    val themeMode by vm.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dynamicColor by vm.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDropImages by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }

    // Both are granted in system Settings — re-read on ON_RESUME (same pattern as AppNav).
    var hasAccess by remember { mutableStateOf(PermissionUtils.hasNotificationAccess(context)) }
    var batteryExempt by remember {
        mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = PermissionUtils.hasNotificationAccess(context)
                batteryExempt = PermissionUtils.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- Export / Import: state and work live in the ViewModel (see there) ----
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val busy = transfer.busy
    // Plain dialog visibility survives rotation; the pending export itself sits in the VM.
    var exportFormatDialog by rememberSaveable { mutableStateOf(false) }
    var exportPassFor by rememberSaveable { mutableStateOf<VaultFormat?>(null) }

    // One creator for every format: the chosen extension travels in the suggested file name,
    // and "*/*" keeps pickers from appending one of their own.
    val exportCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> vm.onExportTarget(uri) }

    // Read in composition: launchExport and the click lambdas below are not composable.
    val msgNoFilePicker = stringResource(R.string.settings_no_file_picker)
    val msgNoBrowser = stringResource(R.string.settings_no_browser)
    val msgRebindRequested = stringResource(R.string.settings_status_rebind_requested)
    val msgRebindNoAccess = stringResource(R.string.settings_status_rebind_no_access)

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> vm.onImportPicked(uri) }

    fun launchExport(format: VaultFormat, pass: String?) {
        vm.beginExport(format, pass)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
        runCatching { exportCreator.launch(ExportNaming.exportFileName(date, format)) }
            .onFailure { vm.cancelExport(); vm.showMessage(msgNoFilePicker) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                subtitle = { Text(stringResource(R.string.settings_subtitle_version, BuildConfig.VERSION_NAME)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(stringResource(R.string.settings_section_status), Icons.Outlined.MonitorHeart, index = 0) {
                StatusRow(
                    stringResource(R.string.settings_status_access),
                    ok = hasAccess,
                    detail = stringResource(
                        if (hasAccess) R.string.settings_status_access_granted
                        else R.string.settings_status_access_missing
                    ),
                    action = if (hasAccess) null else ({ PermissionUtils.openNotificationAccessSettings(context) })
                )
                StatusRow(
                    stringResource(R.string.settings_status_service),
                    ok = listenerConnected,
                    detail = stringResource(
                        if (listenerConnected) R.string.settings_status_service_connected
                        else R.string.settings_status_service_disconnected
                    )
                )
                Text(
                    stringResource(R.string.settings_status_last_capture, formatLastCapture(lastCapture)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasAccess && !listenerConnected) {
                    TextButton(onClick = {
                        vm.showMessage(if (ListenerWatchdog.requestRebind(context)) {
                            msgRebindRequested
                        } else {
                            msgRebindNoAccess
                        })
                    }) { Text(stringResource(R.string.settings_status_rebind)) }
                }
            }
            SettingsCard(stringResource(R.string.settings_section_autostart), Icons.Outlined.RestartAlt, index = 1) {
                ToggleRow(stringResource(R.string.settings_autostart_toggle), autoStart) { vm.setAutoStart(it) }
                Text(
                    stringResource(R.string.settings_autostart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (autoStart) {
                    val overdue = WatchdogPolicy.isWatchdogOverdue(
                        lastWatchdog, System.currentTimeMillis()
                    )
                    Text(
                        stringResource(R.string.settings_autostart_last_check, formatRelativeSince(lastWatchdog)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // The one symptom no rebind can cure: the app itself is being frozen.
                    if (overdue) {
                        Text(
                            stringResource(R.string.settings_autostart_overdue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!batteryExempt) {
                        TextButton(onClick = {
                            PermissionUtils.requestIgnoreBatteryOptimizations(context)
                        }) { Text(stringResource(R.string.settings_autostart_battery_exempt)) }
                    }
                }
            }
            SettingsCard(stringResource(R.string.settings_section_monitored), Icons.Outlined.Apps, index = 2) {
                ToggleRow(stringResource(R.string.settings_capture_all), captureAll) { vm.setCaptureAll(it) }
                // Spring expand/collapse so the per-app list reveals physically when toggling.
                AnimatedVisibility(
                    visible = !captureAll,
                    enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                    exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsStore.KNOWN_MESSENGERS.forEach { (pkg, label) ->
                            ToggleRow(label, pkg in monitored) { on ->
                                val next = monitored.toMutableSet()
                                if (on) next.add(pkg) else next.remove(pkg)
                                vm.setMonitored(next)
                            }
                        }
                    }
                }
            }
            SettingsCard(stringResource(R.string.settings_section_images), Icons.Outlined.Image, index = 3) {
                ToggleRow(stringResource(R.string.settings_images_toggle), captureImages) {
                    vm.setCaptureImages(it)
                }
                Text(
                    stringResource(R.string.settings_images_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (imageCount == 0) stringResource(R.string.settings_images_none)
                    else pluralStringResource(
                        R.plurals.settings_images_stored, imageCount, imageCount, formatBytes(imageBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (imageCount > 0) {
                    TextButton(onClick = { confirmDropImages = true }) { Text(stringResource(R.string.settings_images_delete_all)) }
                }
            }
            SettingsCard(stringResource(R.string.settings_section_security), Icons.Outlined.Lock, index = 4) {
                ToggleRow(stringResource(R.string.settings_biometric_lock), biometric) { vm.setBiometric(it) }
                Text(stringResource(R.string.settings_security_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
            SettingsCard(stringResource(R.string.settings_section_appearance), Icons.Outlined.Palette, index = 5) {
                AppearanceSection(
                    mode = themeMode,
                    dynamicColor = dynamicColor,
                    onMode = vm::setThemeMode,
                    onDynamicColor = vm::setDynamicColor
                )
            }
            SettingsCard(pluralStringResource(R.plurals.settings_section_export, total, total), Icons.Outlined.ImportExport, index = 6) {
                Text(
                    stringResource(R.string.settings_export_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(
                    visible = busy,
                    enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                    exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.settings_export_busy), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(
                    onClick = { exportFormatDialog = true },
                    enabled = total > 0 && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_export_button)) }
                OutlinedButton(
                    onClick = {
                        runCatching { importPicker.launch(arrayOf("*/*")) }
                            .onFailure { vm.showMessage(msgNoFilePicker) }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_import_button)) }
                Text(
                    stringResource(R.string.settings_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            SettingsCard(stringResource(R.string.settings_section_data), Icons.Outlined.Storage, index = 7) {
                RetentionRow(retention) { showRetentionDialog = true }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = total > 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_clear_all)) }
            }
            SettingsCard(stringResource(R.string.settings_section_updates), Icons.Outlined.SystemUpdate, index = 8) {
                UpdateSection(onOpenFailed = { vm.showMessage(msgNoBrowser) })
            }
            SettingsCard(stringResource(R.string.settings_section_notes), Icons.Outlined.Info, index = 9) {
                Text(
                    stringResource(R.string.settings_notes_text),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SettingsCard(stringResource(R.string.settings_section_about), Icons.Outlined.Favorite, index = 10) {
                AboutSection(onOpenFailed = { vm.showMessage(msgNoBrowser) })
            }
            Text(
                stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = {
                Text(pluralStringResource(R.plurals.settings_clear_confirm_text, total, total))
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmClear = false; vm.clearAll() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_clear_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }

    if (confirmDropImages) {
        AlertDialog(
            onDismissRequest = { confirmDropImages = false },
            title = { Text(stringResource(R.string.settings_images_confirm_title)) },
            text = {
                Text(pluralStringResource(
                    R.plurals.settings_images_confirm_text, imageCount, imageCount, formatBytes(imageBytes)
                ))
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDropImages = false; vm.deleteAllImages() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_images_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDropImages = false }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }

    if (showRetentionDialog) {
        RetentionDialog(
            current = retention,
            onSelect = { days ->
                showRetentionDialog = false
                vm.setRetentionDays(days)
            },
            onDismiss = { showRetentionDialog = false }
        )
    }

    if (exportFormatDialog) {
        ExportFormatDialog(
            onSelect = { format ->
                exportFormatDialog = false
                if (format.encrypted) exportPassFor = format else launchExport(format, null)
            },
            onDismiss = { exportFormatDialog = false }
        )
    }

    exportPassFor?.let { format ->
        PassphraseDialog(
            title = stringResource(R.string.settings_export_pass_title),
            hint = stringResource(R.string.settings_export_pass_hint, VaultBackup.MIN_PASSPHRASE_LENGTH),
            requireConfirm = true,
            onConfirm = { pass ->
                exportPassFor = null
                launchExport(format, pass)
            },
            onDismiss = { exportPassFor = null }
        )
    }

    if (transfer.needsImportPass) {
        PassphraseDialog(
            title = stringResource(R.string.settings_import_pass_title),
            hint = stringResource(R.string.settings_import_pass_hint),
            requireConfirm = false,
            onConfirm = { pass -> vm.submitImportPass(pass) },
            onDismiss = { vm.cancelImport() }
        )
    }

    // What the file holds, shown before a single row is written.
    transfer.preview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onConfirm = { vm.confirmImport() },
            onDismiss = { vm.cancelImport() }
        )
    }

    transfer.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.dismissMessage() },
            title = { Text(stringResource(R.string.settings_message_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { vm.dismissMessage() }) { Text(stringResource(R.string.settings_ok)) }
            }
        )
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, detail: String, action: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            (if (ok) "✓ " else "✗ ") + detail,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
        if (action != null) {
            TextButton(onClick = action) { Text(stringResource(R.string.settings_status_open)) }
        }
    }
}

private fun formatLastCapture(millis: Long): String = formatRelativeSince(millis)


@Composable
private fun RetentionRow(days: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.settings_retention), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) {
            Text(
                if (days <= 0) stringResource(R.string.settings_retention_forever)
                else pluralStringResource(R.plurals.settings_retention_days, days, days)
            )
        }
    }
}

@Composable
private fun RetentionDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = RetentionPolicy.OPTIONS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_retention_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_retention_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { days ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == days, onClick = { onSelect(days) })
                        Text(
                            if (days == 0) stringResource(R.string.settings_retention_forever_default)
                            else pluralStringResource(R.plurals.settings_retention_days, days, days),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}

/** Format picker — this is where encryption becomes optional rather than mandatory. */
@Composable
private fun ExportFormatDialog(onSelect: (VaultFormat) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Triple(
            VaultFormat.ENCRYPTED, stringResource(R.string.settings_format_encrypted),
            stringResource(R.string.settings_format_encrypted_desc)
        ),
        Triple(
            VaultFormat.JSON, stringResource(R.string.settings_format_json),
            stringResource(R.string.settings_format_json_desc)
        ),
        Triple(
            VaultFormat.CSV, stringResource(R.string.settings_format_csv),
            stringResource(R.string.settings_format_csv_desc)
        )
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (format, label, description) ->
                    Row(
                        Modifier.fillMaxWidth().clickableScale { onSelect(format) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(selected = false, onClick = { onSelect(format) })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(label, fontWeight = FontWeight.Medium)
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } }
    )
}

/** Shows what an import file contains before anything is written to the vault. */
@Composable
private fun ImportPreviewDialog(
    preview: VaultTransfer.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val datePattern = stringResource(R.string.settings_import_date_pattern)
    val dayFmt = remember(datePattern) { SimpleDateFormat(datePattern, Locale.getDefault()) }
    val kind = stringResource(
        when (preview.format) {
            VaultFormat.ENCRYPTED -> R.string.settings_import_kind_encrypted
            VaultFormat.JSON -> R.string.settings_import_kind_json
            VaultFormat.CSV -> R.string.settings_import_kind_csv
        }
    )
    val range = if (preview.oldest != null && preview.newest != null)
        "${dayFmt.format(Date(preview.oldest))} – ${dayFmt.format(Date(preview.newest))}"
    else "—"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_import_detected, kind))
                Text(stringResource(R.string.settings_import_count, preview.count))
                Text(stringResource(R.string.settings_import_range, range))
                preview.exported?.let { Text(stringResource(R.string.settings_import_created, it)) }
                Spacer(Modifier.padding(4.dp))
                Text(
                    stringResource(R.string.settings_import_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = preview.count > 0) { Text(stringResource(R.string.settings_import_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } }
    )
}

@Composable
private fun PassphraseDialog(
    title: String,
    hint: String,
    requireConfirm: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pass.length >= VaultBackup.MIN_PASSPHRASE_LENGTH &&
        (!requireConfirm || pass == confirm)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.settings_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.settings_passphrase_repeat)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirm.isNotEmpty() && confirm != pass,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass) }, enabled = valid) { Text(stringResource(R.string.settings_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}

/**
 * One settings group: a tonal card with an icon-led title, rising in on a staggered spring
 * (the expressive replacement for divider-separated flat sections).
 */
@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    index: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().springEntrance(index)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

/** Label on the left, a read-only value on the right. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    SwitchRow(title = label, hint = null, checked = checked, onToggle = { onChange(!checked) })
