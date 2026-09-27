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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.notifvault.BuildConfig
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

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> vm.onImportPicked(uri) }

    fun launchExport(format: VaultFormat, pass: String?) {
        vm.beginExport(format, pass)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
        runCatching { exportCreator.launch(ExportNaming.exportFileName(date, format)) }
            .onFailure { vm.cancelExport(); vm.showMessage("Kein Dateiauswahl-Dialog verfügbar.") }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Einstellungen") },
                subtitle = { Text("Kleene Petze ${BuildConfig.VERSION_NAME}") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
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
            SettingsCard("Status", Icons.Outlined.MonitorHeart, index = 0) {
                StatusRow(
                    "Benachrichtigungszugriff",
                    ok = hasAccess,
                    detail = if (hasAccess) "erteilt" else "fehlt",
                    action = if (hasAccess) null else ({ PermissionUtils.openNotificationAccessSettings(context) })
                )
                StatusRow(
                    "Erfassungsdienst",
                    ok = listenerConnected,
                    detail = if (listenerConnected) "verbunden" else "nicht verbunden"
                )
                Text(
                    "Letzte Erfassung: ${formatLastCapture(lastCapture)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasAccess && !listenerConnected) {
                    TextButton(onClick = {
                        vm.showMessage(if (ListenerWatchdog.requestRebind(context)) {
                            "Neuverbindung angefordert. Der Status oben springt auf „verbunden“, " +
                                "sobald das System den Dienst gebunden hat — das dauert einen Moment."
                        } else {
                            "Ohne Benachrichtigungszugriff kann der Dienst nicht verbunden werden."
                        })
                    }) { Text("Erfassung neu verbinden") }
                }
            }
            SettingsCard("Autostart & Selbstheilung", Icons.Outlined.RestartAlt, index = 1) {
                ToggleRow("Nach Neustart automatisch starten", autoStart) { vm.setAutoStart(it) }
                Text(
                    "Verbindet die Erfassung nach einem Neustart, nach einem App-Update und alle " +
                        "15 Minuten neu. Android trennt den Dienst sonst still — und meldet das nicht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (autoStart) {
                    val overdue = WatchdogPolicy.isWatchdogOverdue(
                        lastWatchdog, System.currentTimeMillis()
                    )
                    Text(
                        "Letzte Prüfung: ${formatRelativeSince(lastWatchdog)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // The one symptom no rebind can cure: the app itself is being frozen.
                    if (overdue) {
                        Text(
                            "Die Prüfung läuft seit Stunden nicht mehr — das Energiesparen hält die " +
                                "App an. Nimm sie davon aus, sonst kann auch die Erfassung nicht " +
                                "zurückkommen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!batteryExempt) {
                        TextButton(onClick = {
                            PermissionUtils.requestIgnoreBatteryOptimizations(context)
                        }) { Text("Von Akku-Optimierung ausnehmen") }
                    }
                }
            }
            SettingsCard("Überwachte Apps", Icons.Outlined.Apps, index = 2) {
                ToggleRow("Alle Apps erfassen", captureAll) { vm.setCaptureAll(it) }
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
            SettingsCard("Bilder", Icons.Outlined.Image, index = 3) {
                ToggleRow("Bilder aus Benachrichtigungen sichern", captureImages) {
                    vm.setCaptureImages(it)
                }
                Text(
                    "Kommentare unter Bildern werden immer gesichert — sie sind der Nachrichtentext. " +
                        "Zusätzlich lässt sich die Bildvorschau speichern, die die Benachrichtigung " +
                        "mitbringt. Das ist nicht das Original aus WhatsApp, sondern die kleinere " +
                        "Vorschau; an die Originaldatei kommt keine App heran.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (imageCount == 0) "Keine Bilder gespeichert."
                    else "$imageCount Bild${if (imageCount == 1) "" else "er"} · ${formatBytes(imageBytes)} " +
                        "(verschlüsselt in der Datenbank)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (imageCount > 0) {
                    TextButton(onClick = { confirmDropImages = true }) { Text("Alle Bilder löschen") }
                }
            }
            SettingsCard("Sicherheit", Icons.Outlined.Lock, index = 4) {
                ToggleRow("App mit Biometrie sperren", biometric) { vm.setBiometric(it) }
                Text("Daten liegen verschlüsselt (SQLCipher / AES-256) lokal auf dem Gerät.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
            SettingsCard("Erscheinungsbild", Icons.Outlined.Palette, index = 5) {
                AppearanceSection(
                    mode = themeMode,
                    dynamicColor = dynamicColor,
                    onMode = vm::setThemeMode,
                    onDynamicColor = vm::setDynamicColor
                )
            }
            SettingsCard("Export & Import ($total Nachrichten)", Icons.Outlined.ImportExport, index = 6) {
                Text(
                    "Das ganze Archiv als Datei sichern und wieder einlesen. Verschlüsselung ist " +
                        "optional — verschlüsselt (.kpvault) ist die Datei ohne Passphrase wertlos, " +
                        "JSON und CSV sind lesbar und lassen sich genauso zurückspielen. " +
                        "Gespeicherte Bilder bleiben auf dem Gerät und sind nicht Teil des Exports.",
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
                        Text("Wird verarbeitet…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(
                    onClick = { exportFormatDialog = true },
                    enabled = total > 0 && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Archiv exportieren…") }
                OutlinedButton(
                    onClick = {
                        runCatching { importPicker.launch(arrayOf("*/*")) }
                            .onFailure { vm.showMessage("Kein Dateiauswahl-Dialog verfügbar.") }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Aus Datei importieren…") }
                Text(
                    "Ein Import fügt nur hinzu: bereits vorhandene Nachrichten bleiben unverändert, " +
                        "dieselbe Datei zweimal einzulesen ändert nichts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            SettingsCard("Daten", Icons.Outlined.Storage, index = 7) {
                RetentionRow(retention) { showRetentionDialog = true }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = total > 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Alle Daten löschen") }
            }
            SettingsCard("Updates", Icons.Outlined.SystemUpdate, index = 8) {
                UpdateSection(onOpenFailed = { vm.showMessage("Kein Browser gefunden.") })
            }
            SettingsCard("Hinweise", Icons.Outlined.Info, index = 9) {
                Text(
                    "• Sprachnachrichten, Videos und Originaldateien kommen in keiner Benachrichtigung " +
                    "vor und können daher nicht gesichert werden — Bilder nur als Vorschau.\n" +
                    "• Stummgeschaltete Chats und Nachrichten, die du im offenen Chat empfängst, " +
                    "lösen oft keine Benachrichtigung aus und werden daher nicht erfasst.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SettingsCard("Über die App", Icons.Outlined.Favorite, index = 10) {
                AboutSection(onOpenFailed = { vm.showMessage("Kein Browser gefunden.") })
            }
            Text(
                "© 2026 Martin Pfeffer | celox.io",
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
            title = { Text("Alle Daten löschen?") },
            text = {
                Text("Alle $total gespeicherten Nachrichten werden unwiderruflich gelöscht. " +
                    "Dies kann nicht rückgängig gemacht werden.")
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmClear = false; vm.clearAll() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Alles löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") }
            }
        )
    }

    if (confirmDropImages) {
        AlertDialog(
            onDismissRequest = { confirmDropImages = false },
            title = { Text("Alle Bilder löschen?") },
            text = {
                Text("$imageCount gespeicherte Bild${if (imageCount == 1) "" else "er"} " +
                    "(${formatBytes(imageBytes)}) werden entfernt. Die Nachrichten und " +
                    "Kommentare bleiben erhalten.")
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDropImages = false; vm.deleteAllImages() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Bilder löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDropImages = false }) { Text("Abbrechen") }
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
            title = "Export verschlüsseln",
            hint = "Mindestens ${VaultBackup.MIN_PASSPHRASE_LENGTH} Zeichen. Ohne diese Passphrase " +
                "lässt sich die Datei nie wieder öffnen.",
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
            title = "Datei entschlüsseln",
            hint = "Passphrase der verschlüsselten Datei eingeben.",
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
            title = { Text("Hinweis") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { vm.dismissMessage() }) { Text("OK") }
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
            TextButton(onClick = action) { Text("Öffnen") }
        }
    }
}

private fun formatLastCapture(millis: Long): String = formatRelativeSince(millis)

/** Storage sizes in the units people read them in — MB once it is worth mentioning. */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
private fun RetentionRow(days: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Aufbewahrung", style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) {
            Text(if (days <= 0) "Unbegrenzt" else "$days Tage")
        }
    }
}

@Composable
private fun RetentionDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = RetentionPolicy.OPTIONS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aufbewahrungsdauer") },
        text = {
            Column {
                Text(
                    "Ältere Nachrichten werden automatisch und endgültig gelöscht — " +
                        "auch aufgedeckte (gelöschte/bearbeitete).",
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
                            if (days == 0) "Unbegrenzt (Standard)" else "$days Tage",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/** Format picker — this is where encryption becomes optional rather than mandatory. */
@Composable
private fun ExportFormatDialog(onSelect: (VaultFormat) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Triple(
            VaultFormat.ENCRYPTED, "Verschlüsselt (.kpvault)",
            "AES-256 mit Passphrase. Empfohlen, wenn die Datei das Gerät verlässt — ohne " +
                "Passphrase ist sie für niemanden lesbar, auch nicht für dich."
        ),
        Triple(
            VaultFormat.JSON, "JSON (unverschlüsselt)",
            "Vollständig und wieder importierbar, zusätzlich mit anderen Programmen auswertbar. " +
                "Der Inhalt steht im Klartext in der Datei."
        ),
        Triple(
            VaultFormat.CSV, "CSV (unverschlüsselt)",
            "Für Tabellenprogramme. Ebenfalls vollständig und wieder importierbar, ebenfalls " +
                "im Klartext."
        )
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archiv exportieren") },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Shows what an import file contains before anything is written to the vault. */
@Composable
private fun ImportPreviewDialog(
    preview: VaultTransfer.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dayFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY) }
    val kind = when (preview.format) {
        VaultFormat.ENCRYPTED -> "Verschlüsselte Sicherung"
        VaultFormat.JSON -> "JSON-Export"
        VaultFormat.CSV -> "CSV-Export"
    }
    val range = if (preview.oldest != null && preview.newest != null)
        "${dayFmt.format(Date(preview.oldest))} – ${dayFmt.format(Date(preview.newest))}"
    else "—"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import prüfen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Erkannt: $kind")
                Text("Enthaltene Nachrichten: ${preview.count}")
                Text("Zeitraum: $range")
                preview.exported?.let { Text("Erstellt: $it") }
                Spacer(Modifier.padding(4.dp))
                Text(
                    "Der Import fügt nur hinzu. Bereits vorhandene Nachrichten bleiben " +
                        "unverändert, nichts wird überschrieben oder gelöscht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = preview.count > 0) { Text("Importieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
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
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Passphrase wiederholen") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirm.isNotEmpty() && confirm != pass,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass) }, enabled = valid) { Text("Weiter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
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
