package io.celox.notifvault.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.celox.notifvault.data.CapturedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Serializes [messages] as CSV/JSON and opens the system share sheet. Serialization and the
 * file write run on Dispatchers.IO (an export can be several MB); the chooser is launched
 * back on the caller's (main) context — starting an Activity from a background thread is
 * unreliable on some OEMs. Used by the full export in Settings and the per-chat export.
 */
suspend fun shareExport(
    context: Context,
    messages: List<CapturedMessage>,
    csv: Boolean,
    fileBaseName: String = "kleene-petze_export"
) {
    val uri = withContext(Dispatchers.IO) {
        val content = if (csv) ExportUtils.toCsv(messages) else ExportUtils.toJson(messages)
        val ext = if (csv) "csv" else "json"
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$fileBaseName.$ext")
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
