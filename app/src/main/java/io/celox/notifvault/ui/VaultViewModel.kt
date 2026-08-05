package io.celox.notifvault.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.celox.notifvault.data.BackupMerge
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.data.ConversationSummary
import io.celox.notifvault.data.DatabaseProvider
import io.celox.notifvault.data.MessageDao
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.service.ListenerWatchdog
import io.celox.notifvault.util.VaultFormat
import io.celox.notifvault.util.VaultTransfer
import io.celox.notifvault.util.escapeLike
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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

    /**
     * Streams the whole archive into [out] — the file is written block by block, so the export
     * does not depend on the archive fitting in memory.
     * @return number of exported messages.
     */
    suspend fun exportTo(
        out: java.io.OutputStream,
        format: VaultFormat,
        passphrase: CharArray?
    ): Int = withContext(Dispatchers.IO) {
        VaultTransfer.export(dao, out, format, passphrase, total = totalCount.value)
    }

    /** Reads an import file without writing anything, so the user can confirm what it holds. */
    suspend fun previewImport(
        format: VaultFormat,
        open: () -> java.io.InputStream,
        passphrase: CharArray?
    ): Pair<VaultTransfer.Preview, List<CapturedMessage>?> = withContext(Dispatchers.IO) {
        VaultTransfer.preview(format, open, passphrase)
    }

    /** Applies a previewed import in batches, merging via [importBackup]. */
    suspend fun applyImport(
        format: VaultFormat,
        open: () -> java.io.InputStream,
        decrypted: List<CapturedMessage>?
    ): VaultTransfer.Result = withContext(Dispatchers.IO) {
        VaultTransfer.apply(format, open, decrypted) { batch ->
            val (imported, present) = importBackup(batch)
            VaultTransfer.Result(imported, present)
        }
    }

    fun setCaptureAll(value: Boolean) = viewModelScope.launch { settings.setCaptureAll(value) }
    fun setMonitored(packages: Set<String>) = viewModelScope.launch { settings.setMonitored(packages) }
    fun setBiometric(value: Boolean) = viewModelScope.launch { settings.setBiometricLock(value) }
    fun setCaptureImages(value: Boolean) = viewModelScope.launch { settings.setCaptureImages(value) }
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
