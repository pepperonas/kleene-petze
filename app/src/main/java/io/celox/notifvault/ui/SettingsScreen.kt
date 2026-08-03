package io.celox.notifvault.ui

import android.net.Uri
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
    var confirmClear by remember { mutableStateOf(false) }
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

    // ---- Export / Import state ----
    var exportFormatDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf<VaultFormat?>(null) }
    var exportPassDialog by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importFormat by remember { mutableStateOf<VaultFormat?>(null) }
    var importPassDialog by remember { mutableStateOf(false) }
    var importPreview by remember {
        mutableStateOf<Pair<VaultTransfer.Preview, List<CapturedMessage>?>?>(null)
    }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun openImport(uri: Uri) = context.contentResolver.openInputStream(uri)
        ?: error("Datei konnte nicht gelesen werden")

    // One creator for every format: the chosen extension travels in the suggested file name,
    // and "*/*" keeps pickers from appending one of their own.
    val exportCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val format = exportFormat
        val pass = exportPass
        exportPass = ""
        if (uri == null || format == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            resultMessage = runCatching {
                val out = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)
                        ?: error("Datei konnte nicht geschrieben werden")
                }
                out.use { vm.exportTo(it, format, pass.takeIf { p -> p.isNotEmpty() }?.toCharArray()) }
            }.fold(
                onSuccess = { n ->
                    if (format.encrypted)
                        "$n Nachrichten verschlüsselt exportiert. Passphrase gut aufbewahren — " +
                            "ohne sie ist die Datei wertlos."
                    else
                        "$n Nachrichten als ${format.extension.uppercase()} exportiert. " +
                            "Die Datei ist unverschlüsselt und im Klartext lesbar."
                },
                onFailure = { "Export fehlgeschlagen: ${it.message}" }
            )
            busy = false
        }
    }

    // Reads the file, works out what it is and summarises it — nothing is written yet.
    fun prepareImport(uri: Uri, format: VaultFormat, pass: CharArray?) {
        busy = true
        scope.launch {
            runCatching { vm.previewImport(format, { openImport(uri) }, pass) }
                .fold(
                    onSuccess = { importPreview = it },
                    onFailure = { resultMessage = importError(it) }
                )
            busy = false
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importUri = uri
        busy = true
        scope.launch {
            val detected = runCatching {
                withContext(Dispatchers.IO) {
                    openImport(uri).use { stream ->
                        VaultFormat.detect(ByteArray(VaultFormat.SNIFF_BYTES).let { buf ->
                            val n = stream.read(buf)
                            if (n <= 0) ByteArray(0) else buf.copyOf(n)
                        })
                    }
                }
            }
            busy = false
            detected.fold(
                onSuccess = { format ->
                    importFormat = format
                    // The passphrase is only needed for the encrypted container.
                    if (format.encrypted) importPassDialog = true
                    else prepareImport(uri, format, null)
                },
                onFailure = { resultMessage = importError(it) }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Section("Status")
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
                    resultMessage = if (ListenerWatchdog.requestRebind(context)) {
                        "Neuverbindung angefordert. Der Status oben springt auf „verbunden“, " +
                            "sobald das System den Dienst gebunden hat — das dauert einen Moment."
                    } else {
                        "Ohne Benachrichtigungszugriff kann der Dienst nicht verbunden werden."
                    }
                }) { Text("Erfassung neu verbinden") }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Autostart & Selbstheilung")
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

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Überwachte Apps")
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

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Sicherheit")
            ToggleRow("App mit Biometrie sperren", biometric) { vm.setBiometric(it) }
            Text("Daten liegen verschlüsselt (SQLCipher / AES-256) lokal auf dem Gerät.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Export & Import ($total Nachrichten)")
            Text(
                "Das ganze Archiv als Datei sichern und wieder einlesen. Verschlüsselung ist " +
                    "optional — verschlüsselt (.kpvault) ist die Datei ohne Passphrase wertlos, " +
                    "JSON und CSV sind lesbar und lassen sich genauso zurückspielen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { exportFormatDialog = true },
                enabled = total > 0 && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Archiv exportieren…") }
            OutlinedButton(
                onClick = { importPicker.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Aus Datei importieren…") }
            Text(
                "Ein Import fügt nur hinzu: bereits vorhandene Nachrichten bleiben unverändert, " +
                    "dieselbe Datei zweimal einzulesen ändert nichts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Daten")
            RetentionRow(retention) { showRetentionDialog = true }
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = total > 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Alle Daten löschen") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Hinweise")
            Text(
                "• Medien (Fotos, Sprachnachrichten) können technisch nicht gesichert werden.\n" +
                "• Stummgeschaltete Chats und Nachrichten, die du im offenen Chat empfängst, " +
                "lösen oft keine Benachrichtigung aus und werden daher nicht erfasst.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Über die App")
            // Version comes from BuildConfig, i.e. from the APK that is actually running —
            // the quickest way to check whether an update really landed on the device.
            InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow("Paket", BuildConfig.APPLICATION_ID)

            Spacer(Modifier.padding(16.dp))
            Text(
                "© 2026 Martin Pfeffer | celox.io",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth(),
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
                exportFormat = format
                if (format.encrypted) {
                    exportPassDialog = true
                } else {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
                    exportCreator.launch(ExportNaming.exportFileName(date, format))
                }
            },
            onDismiss = { exportFormatDialog = false }
        )
    }

    if (exportPassDialog) {
        PassphraseDialog(
            title = "Export verschlüsseln",
            hint = "Mindestens ${VaultBackup.MIN_PASSPHRASE_LENGTH} Zeichen. Ohne diese Passphrase " +
                "lässt sich die Datei nie wieder öffnen.",
            requireConfirm = true,
            onConfirm = { pass ->
                exportPassDialog = false
                exportPass = pass
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
                exportCreator.launch(ExportNaming.exportFileName(date, VaultFormat.ENCRYPTED))
            },
            onDismiss = { exportPassDialog = false; exportFormat = null }
        )
    }

    if (importPassDialog) {
        val uri = importUri
        PassphraseDialog(
            title = "Datei entschlüsseln",
            hint = "Passphrase der verschlüsselten Datei eingeben.",
            requireConfirm = false,
            onConfirm = { pass ->
                importPassDialog = false
                if (uri != null) prepareImport(uri, VaultFormat.ENCRYPTED, pass.toCharArray())
            },
            onDismiss = { importPassDialog = false; importUri = null; importFormat = null }
        )
    }

    // What the file holds, shown before a single row is written.
    importPreview?.let { (preview, decrypted) ->
        val uri = importUri
        ImportPreviewDialog(
            preview = preview,
            onConfirm = {
                importPreview = null
                if (uri == null) return@ImportPreviewDialog
                busy = true
                scope.launch {
                    resultMessage = runCatching {
                        vm.applyImport(preview.format, { openImport(uri) }, decrypted)
                    }.fold(
                        onSuccess = { (imported, present) ->
                            "Import abgeschlossen: $imported Nachrichten übernommen, " +
                                "$present waren bereits vorhanden."
                        },
                        onFailure = { importError(it) }
                    )
                    busy = false
                    importUri = null
                    importFormat = null
                }
            },
            onDismiss = { importPreview = null; importUri = null; importFormat = null }
        )
    }

    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text("Export & Import") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) { Text("OK") }
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

/**
 * Turns a failed export/import into something the user can act on. A wrong passphrase is by far
 * the most common cause and must not read like a corrupt file.
 */
private fun importError(t: Throwable): String = when (t) {
    is javax.crypto.AEADBadTagException ->
        "Entschlüsselung fehlgeschlagen — falsche Passphrase oder beschädigte Datei."
    is IllegalArgumentException ->
        "Datei konnte nicht gelesen werden: ${t.message}"
    else -> "Import fehlgeschlagen: ${t.message}"
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

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

