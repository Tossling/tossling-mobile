package com.kopylovis.tossling.sync.work

import android.content.Context
import androidx.core.content.edit
import com.kopylovis.tossling.protocol.ClipKind
import com.kopylovis.tossling.protocol.SyncJson
import com.kopylovis.tossling.sync.clipboard.Outgoing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** A file the user paused. ntfy cannot append to an attachment, so Continue sends it again from the first byte. */
@Serializable
data class PausedSend(
    val id: String,
    val path: String,
    val kind: String,
    val mime: String,
    val name: String?,
    val targets: List<String>?,
    val sensitive: Boolean,
    val size: Long,
    val device: String,
) {
    internal fun outgoing(): Outgoing = Outgoing(kind = ClipKind.valueOf(kind), mime = mime, file = File(path), name = name, sensitive = sensitive)
}

/** Paused files wait in files/paused: the system may clear the cache, where the queue keeps them. */
internal class PausedSends(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, DIR)
    private val lock = Any()

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<PausedSend>> = _items.asStateFlow()

    fun add(outgoing: Outgoing, targets: Set<String>?, device: String): PausedSend {
        val id = UUID.randomUUID().toString()
        dir.mkdirs()
        val kept = File(dir, id)
        if (!outgoing.file.renameTo(kept)) {
            outgoing.file.copyTo(kept, overwrite = true)
            outgoing.file.delete()
        }
        val paused = PausedSend(
            id = id,
            path = kept.absolutePath,
            kind = outgoing.kind.name,
            mime = outgoing.mime,
            name = outgoing.name,
            targets = targets?.toList(),
            sensitive = outgoing.sensitive,
            size = kept.length(),
            device = device,
        )
        synchronized(lock) {
            _items.update { it + paused }
            write()
        }
        return paused
    }

    /** Removes it from the list and hands it back; the file stays for the caller to send or delete. */
    fun take(id: String): PausedSend? = synchronized(lock) {
        val found = _items.value.firstOrNull { it.id == id } ?: return null
        _items.update { list -> list.filter { it.id != id } }
        write()
        found
    }

    private fun write() = prefs.edit { putString(KEY_ITEMS, SyncJson.encodeToString(Serializer, _items.value)) }

    private fun read(): List<PausedSend> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching { SyncJson.decodeFromString(Serializer, raw) }.getOrDefault(emptyList()).filter { File(it.path).exists() }
    }

    private companion object {
        private const val PREFS = "paused_sends"
        private const val KEY_ITEMS = "items"
        private const val DIR = "paused"
        private val Serializer = ListSerializer(PausedSend.serializer())
    }
}
