package io.celox.notifvault.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.celox.notifvault.data.BackupMerge
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.data.ConversationSummary
import io.celox.notifvault.data.DatabaseProvider
import io.celox.notifvault.data.MessageDao
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.service.ListenerWatchdog
import io.celox.notifvault.ui.theme.ThemeMode
import io.celox.notifvault.util.VaultFormat
import io.celox.notifvault.util.VaultTransfer
import io.celox.notifvault.util.escapeLike
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val dao: MessageDao = DatabaseProvider.get(app).messageDao()
    val settings = SettingsStore(app)

    val conversations: StateFlow<List<ConversationSummary>> =
        dao.conversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> =
        dao.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Everything "uncovered": deleted-by-sender originals + earlier versions of edits. */
    val flagged: StateFlow<List<CapturedMessage>> =
        dao.flagged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val query = MutableStateFlow("")

    val searchResults: StateFlow<List<CapturedMessage>> = query
        .debounce(180)                 // don't hit the DB on every keystroke
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else dao.search(escapeLike(q.trim()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { query.value = q }

    fun messagesFor(conversationKey: String, pkg: String) =
        dao.messagesFor(conversationKey, pkg)

    /** Which messages in this chat have a picture — ids only, the bytes stay in the database. */
    fun attachmentIdsFor(conversationKey: String, pkg: String) =
        dao.attachmentIdsFor(conversationKey, pkg)

    suspend fun attachmentBytes(messageId: String): ByteArray? = withContext(Dispatchers.IO) {
        dao.attachment(messageId)?.bytes
    }

    val attachmentCount: StateFlow<Int> =
        dao.attachmentCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val attachmentBytesTotal: StateFlow<Long> =
        dao.attachmentBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun deleteAllImages() = viewModelScope.launch { dao.clearAttachments() }

    // Attachments before messages in every delete path — see the note in MessageDao.
    fun deleteConversation(conversationKey: String, pkg: String) = viewModelScope.launch {
        dao.deleteAttachmentsFor(conversationKey, pkg)
        dao.deleteConversation(conversationKey, pkg)
    }

    fun clearAll() = viewModelScope.launch {
        dao.clearAttachments()
        dao.clear()
    }

    suspend fun exportAll() = dao.exportAll()

    // ---- Export / Import ------------------------------------------------------------------
    //
    // Lives here, not in SettingsScreen: the system file picker sends the activity to the
    // background, a rotation recreates it, and the old `remember` state in the screen was gone by
    // the time the picker returned — the export silently did nothing and left an empty file. The
    // ViewModel is scoped to the activity and outlives both, and the work runs in viewModelScope
    // so leaving the Settings screen no longer cancels a running export halfway through.

    /** Everything the export/import dialogs need to render. */
    data class TransferUi(
        val busy: Boolean = false,
        /** Format chosen, waiting for the file picker to return a target. */
        val pendingExport: VaultFormat? = null,
        val importUri: Uri? = null,
        /** An encrypted file was picked; the passphrase dialog is up. */
        val needsImportPass: Boolean = false,
        val preview: VaultTransfer.Preview? = null,
        val message: String? = null
    )

    private val _transfer = MutableStateFlow(TransferUi())
    val transfer: StateFlow<TransferUi> = _transfer

    // Kept out of TransferUi so it never ends up in anything that is logged or compared.
    private var exportPass: CharArray? = null
    private var decrypted: List<CapturedMessage>? = null

    private val resolver get() = getApplication<Application>().contentResolver

    /** Step 1 of an export: remember the choice until the picker hands back a file. */
    fun beginExport(format: VaultFormat, passphrase: String?) {
        exportPass = passphrase?.takeIf { it.isNotEmpty() }?.toCharArray()
        _transfer.value = _transfer.value.copy(pendingExport = format)
    }

    /** Step 2: the picker returned (or was cancelled — then [uri] is null). */
    fun onExportTarget(uri: Uri?) {
        val format = _transfer.value.pendingExport
        val pass = exportPass
        exportPass = null
        _transfer.value = _transfer.value.copy(pendingExport = null)
        if (uri == null || format == null) return
        _transfer.value = _transfer.value.copy(busy = true)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri, "wt")
                        ?: error("Datei konnte nicht geschrieben werden")
                    // Only the raw stream is closed here. VaultTransfer finishes its own writer on
                    // success only, so a failure never leaves a well-formed partial archive.
                    out.use { VaultTransfer.export(dao, it, format, pass, total = dao.countNow()) }
                }
            }
            pass?.fill(' ')
            result.exceptionOrNull()?.let { deleteQuietly(uri) }
            val message = result.fold(
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
            _transfer.value = _transfer.value.copy(busy = false, message = message)
        }
    }

    /**
     * A failed export must not leave a file behind that looks like a backup. NonCancellable,
     * because the typical failure here *is* a cancellation.
     */
    private suspend fun deleteQuietly(uri: Uri) = withContext(NonCancellable + Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }

    private fun openImport(uri: Uri) =
        resolver.openInputStream(uri) ?: error("Datei konnte nicht gelesen werden")

    /** The import picker returned: sniff the format, then ask for a passphrase or preview. */
    fun onImportPicked(uri: Uri?) {
        if (uri == null) return
        _transfer.value = TransferUi(busy = true, importUri = uri)
        viewModelScope.launch {
            val detected = runCatching {
                withContext(Dispatchers.IO) {
                    openImport(uri).use { stream ->
                        val buf = ByteArray(VaultFormat.SNIFF_BYTES)
                        val n = stream.read(buf)
                        VaultFormat.detect(if (n <= 0) ByteArray(0) else buf.copyOf(n))
                    }
                }
            }
            detected.fold(
                onSuccess = { format ->
                    if (format.encrypted) {
                        _transfer.value = _transfer.value.copy(busy = false, needsImportPass = true)
                    } else {
                        preparePreview(uri, format, null)
                    }
                },
                onFailure = { fail(it) }
            )
        }
    }

    fun submitImportPass(pass: String) {
        val uri = _transfer.value.importUri ?: return
        _transfer.value = _transfer.value.copy(needsImportPass = false, busy = true)
        viewModelScope.launch { preparePreview(uri, VaultFormat.ENCRYPTED, pass.toCharArray()) }
    }

    /** Reads the file and summarises it — nothing is written yet. */
    private suspend fun preparePreview(uri: Uri, format: VaultFormat, pass: CharArray?) {
        runCatching {
            withContext(Dispatchers.IO) { VaultTransfer.preview(format, { openImport(uri) }, pass) }
        }.fold(
            onSuccess = { (preview, messages) ->
                decrypted = messages
                _transfer.value = _transfer.value.copy(busy = false, preview = preview)
            },
            onFailure = { fail(it) }
        )
        pass?.fill(' ')
    }

    fun confirmImport() {
        val state = _transfer.value
        val uri = state.importUri ?: return
        val preview = state.preview ?: return
        val messages = decrypted
        _transfer.value = state.copy(preview = null, busy = true)
        viewModelScope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    VaultTransfer.apply(preview.format, { openImport(uri) }, messages) { batch ->
                        val (imported, present) = importBackup(batch)
                        VaultTransfer.Result(imported, present)
                    }
                }
            }.fold(
                onSuccess = { (imported, present) ->
                    "Import abgeschlossen: $imported Nachrichten übernommen, " +
                        "$present waren bereits vorhanden."
                },
                onFailure = { importError(it) }
            )
            decrypted = null
            _transfer.value = TransferUi(message = message)
        }
    }

    fun cancelImport() {
        decrypted = null
        _transfer.value = TransferUi()
    }

    fun cancelExport() {
        exportPass?.fill(' ')
        exportPass = null
        _transfer.value = _transfer.value.copy(pendingExport = null)
    }

    fun showMessage(message: String) {
        _transfer.value = _transfer.value.copy(message = message)
    }

    fun dismissMessage() {
        _transfer.value = _transfer.value.copy(message = null)
    }

    private fun fail(t: Throwable) {
        decrypted = null
        _transfer.value = TransferUi(message = importError(t))
    }

    fun setCaptureAll(value: Boolean) = viewModelScope.launch { settings.setCaptureAll(value) }
    fun setMonitored(packages: Set<String>) = viewModelScope.launch { settings.setMonitored(packages) }
    // ---- App lock session ----
    private val _unlocked = MutableStateFlow(false)
    /** Whether this session got past the lock. Reset on every trip to the background. */
    val unlocked: StateFlow<Boolean> = _unlocked
    fun unlock() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }

    fun setBiometric(value: Boolean) {
        // Turned on from inside the open app: this session is already the owner's.
        if (value) _unlocked.value = true
        viewModelScope.launch { settings.setBiometricLock(value) }
    }
    fun setCaptureImages(value: Boolean) = viewModelScope.launch { settings.setCaptureImages(value) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settings.setDynamicColor(value) }
    fun setRetentionDays(days: Int) = viewModelScope.launch { settings.setRetentionDays(days) }

    /** Also starts/stops the watchdog job right away — the toggle has to take effect now. */
    fun setAutoStart(value: Boolean) = viewModelScope.launch {
        settings.setAutoStartOnBoot(value)
        ListenerWatchdog.sync(getApplication())
    }

    /**
     * Restores a decoded backup. Insert-IGNORE + content-hash ids make this an idempotent
     * merge; for rows that already existed unflagged, the backup's deleted/edited flags are
     * re-applied separately (IGNORE keeps the existing row untouched).
     * @return imported count to already-present count.
     */
    suspend fun importBackup(messages: List<CapturedMessage>): Pair<Int, Int> {
        val plan = BackupMerge.plan(messages, dao.insertAll(messages))
        plan.deletedIds.chunked(BackupMerge.FLAG_CHUNK).forEach { dao.applyDeletedFlags(it) }
        plan.editedIds.chunked(BackupMerge.FLAG_CHUNK).forEach { dao.applyEditedFlags(it) }
        return plan.imported to plan.alreadyPresent
    }
}

/**
 * Turns a failed export/import into something the user can act on. A wrong passphrase is by far
 * the most common cause and must not read like a corrupt file.
 */
internal fun importError(t: Throwable): String = when (t) {
    is javax.crypto.AEADBadTagException ->
        "Entschlüsselung fehlgeschlagen — falsche Passphrase oder beschädigte Datei."
    is IllegalArgumentException ->
        "Datei konnte nicht gelesen werden: ${t.message}"
    else -> "Import fehlgeschlagen: ${t.message}"
}
