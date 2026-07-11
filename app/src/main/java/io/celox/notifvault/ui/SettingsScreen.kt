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
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.service.NotificationCaptureService
import io.celox.notifvault.ui.theme.Motion
import io.celox.notifvault.util.PermissionUtils
import io.celox.notifvault.util.VaultBackup
import io.celox.notifvault.util.VaultCodec
import io.celox.notifvault.util.shareExport
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
    var confirmClear by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }

    // Access is granted in system Settings — re-read on ON_RESUME (same pattern as AppNav).
    var hasAccess by remember { mutableStateOf(PermissionUtils.hasNotificationAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = PermissionUtils.hasNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- Backup / Restore state ----
    var backupPassDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var backupPass by remember { mutableStateOf("") }

    val backupCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pass = backupPass
        backupPass = ""
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            resultMessage = runCatching {
                withContext(Dispatchers.IO) {
                    val all = vm.exportAll()
                    val bytes = VaultBackup.encrypt(VaultCodec.encode(all), pass.toCharArray())
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Datei konnte nicht geschrieben werden")
                    all.size
                }
            }.fold(
                onSuccess = { "Backup mit $it Nachrichten erstellt. Passphrase gut aufbewahren — ohne sie ist das Backup wertlos." },
                onFailure = { "Backup fehlgeschlagen: ${it.message}" }
            )
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) restoreUri = uri }

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
            Section("Daten ($total Nachrichten)")
            Button(
                onClick = { scope.launch { shareExport(context, vm.exportAll(), csv = true) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Als CSV exportieren") }
            OutlinedButton(
                onClick = { scope.launch { shareExport(context, vm.exportAll(), csv = false) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Als JSON exportieren") }
            RetentionRow(retention) { showRetentionDialog = true }
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = total > 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Alle Daten löschen") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Backup")
            Text(
                "Verschlüsseltes Backup des gesamten Archivs (.kpvault, AES-256). " +
                    "Ohne die Passphrase ist die Datei nicht lesbar — auch nicht für dich.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { backupPassDialog = true },
                enabled = total > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Backup erstellen") }
            OutlinedButton(
                onClick = { restorePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Backup wiederherstellen") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("Hinweise")
            Text(
                "• Medien (Fotos, Sprachnachrichten) können technisch nicht gesichert werden.\n" +
                "• Stummgeschaltete Chats und Nachrichten, die du im offenen Chat empfängst, " +
                "lösen oft keine Benachrichtigung aus und werden daher nicht erfasst.",
                style = MaterialTheme.typography.bodySmall
            )

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

    if (backupPassDialog) {
        PassphraseDialog(
            title = "Backup verschlüsseln",
            hint = "Mindestens ${VaultBackup.MIN_PASSPHRASE_LENGTH} Zeichen. Ohne diese Passphrase " +
                "lässt sich das Backup nie wieder öffnen.",
            requireConfirm = true,
            onConfirm = { pass ->
                backupPassDialog = false
                backupPass = pass
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
                backupCreator.launch("kleene-petze-$date.${VaultBackup.FILE_EXTENSION}")
            },
            onDismiss = { backupPassDialog = false }
        )
    }

    restoreUri?.let { uri ->
        PassphraseDialog(
            title = "Backup entschlüsseln",
            hint = "Passphrase des Backups eingeben.",
            requireConfirm = false,
            onConfirm = { pass ->
                restoreUri = null
                scope.launch {
                    resultMessage = runCatching {
                        val messages = withContext(Dispatchers.IO) {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: error("Datei konnte nicht gelesen werden")
                            VaultCodec.decode(VaultBackup.decrypt(bytes, pass.toCharArray()))
                        }
                        vm.importBackup(messages)
                    }.fold(
                        onSuccess = { (imported, skipped) ->
                            "Wiederhergestellt: $imported Nachrichten importiert, $skipped bereits vorhanden."
                        },
                        onFailure = {
                            if (it is javax.crypto.AEADBadTagException)
                                "Entschlüsselung fehlgeschlagen — falsche Passphrase oder beschädigte Datei."
                            else "Wiederherstellung fehlgeschlagen: ${it.message}"
                        }
                    )
                }
            },
            onDismiss = { restoreUri = null }
        )
    }

    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text("Backup") },
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

private fun formatLastCapture(millis: Long): String {
    if (millis <= 0) return "noch nie"
    val mins = (System.currentTimeMillis() - millis) / 60_000
    return when {
        mins < 1 -> "gerade eben"
        mins < 60 -> "vor $mins min"
        mins < 24 * 60 -> "vor ${mins / 60} h"
        else -> formatTimestamp(millis)
    }
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
    val options = listOf(0, 30, 90, 180, 365)
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

