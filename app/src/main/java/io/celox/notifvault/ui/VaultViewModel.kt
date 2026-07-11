package io.celox.notifvault.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.data.ConversationSummary
import io.celox.notifvault.data.DatabaseProvider
import io.celox.notifvault.data.MessageDao
import io.celox.notifvault.data.SettingsStore
import io.celox.notifvault.util.escapeLike
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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

    fun deleteConversation(conversationKey: String, pkg: String) = viewModelScope.launch {
        dao.deleteConversation(conversationKey, pkg)
    }

    fun clearAll() = viewModelScope.launch { dao.clear() }

    suspend fun exportAll() = dao.exportAll()

    fun setCaptureAll(value: Boolean) = viewModelScope.launch { settings.setCaptureAll(value) }
    fun setMonitored(packages: Set<String>) = viewModelScope.launch { settings.setMonitored(packages) }
    fun setBiometric(value: Boolean) = viewModelScope.launch { settings.setBiometricLock(value) }
    fun setRetentionDays(days: Int) = viewModelScope.launch { settings.setRetentionDays(days) }

    /**
     * Restores a decoded backup. Insert-IGNORE + content-hash ids make this an idempotent
     * merge; for rows that already existed unflagged, the backup's deleted/edited flags are
     * re-applied separately (IGNORE keeps the existing row untouched).
     * @return imported count to already-present count.
     */
    suspend fun importBackup(messages: List<CapturedMessage>): Pair<Int, Int> {
        val rowIds = dao.insertAll(messages)
        var imported = 0
        val deletedIds = mutableListOf<String>()
        val editedIds = mutableListOf<String>()
        for ((i, m) in messages.withIndex()) {
            if (rowIds[i] != -1L) imported++
            else {
                if (m.deletionSuspected) deletedIds += m.id
                if (m.editSuperseded) editedIds += m.id
            }
        }
        // Chunked: SQLite caps bound variables per statement (999 classic limit).
        deletedIds.chunked(500).forEach { dao.applyDeletedFlags(it) }
        editedIds.chunked(500).forEach { dao.applyEditedFlags(it) }
        return imported to (messages.size - imported)
    }
}
