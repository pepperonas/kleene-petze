package io.celox.notifvault.ui

import android.content.Intent
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.ui.theme.Motion
import io.celox.notifvault.util.ExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: VaultViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureAll by vm.settings.captureAll.collectAsStateWithLifecycle(initialValue = false)
    val monitored by vm.settings.monitoredPackages.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_PACKAGES)
    val biometric by vm.settings.biometricLock.collectAsStateWithLifecycle(initialValue = false)
    val total by vm.totalCount.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

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
                onClick = { scope.launch { export(context, vm, csv = true) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Als CSV exportieren") }
            OutlinedButton(
                onClick = { scope.launch { export(context, vm, csv = false) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Als JSON exportieren") }
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

private suspend fun export(
    context: android.content.Context,
    vm: VaultViewModel,
    csv: Boolean
) {
    val all = vm.exportAll()
    val content = if (csv) ExportUtils.toCsv(all) else ExportUtils.toJson(all)
    val ext = if (csv) "csv" else "json"
    // Write the file off the main thread, then launch the chooser back on the main thread
    // (starting an Activity from a background thread is unreliable on some OEMs).
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "kleene-petze_export.$ext")
        file.writeText(content)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val share = Intent(Intent.ACTION_SEND).apply {
        type = if (csv) "text/csv" else "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(share, "Export teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
