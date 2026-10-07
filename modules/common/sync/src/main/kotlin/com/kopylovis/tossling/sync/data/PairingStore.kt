package com.kopylovis.tossling.sync.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer

internal class PairingStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _pairings = MutableStateFlow(readPairings())
    val pairings: StateFlow<List<Pairing>> = _pairings.asStateFlow()

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<ClipItem>> = _history.asStateFlow()

    fun lastId(pairing: Pairing): String = prefs.getString(lastIdKey(pairing.id), null).orEmpty()

    fun saveLastId(pairing: Pairing, id: String) = prefs.edit { putString(lastIdKey(pairing.id), id) }

    fun savePairing(pairing: Pairing) {
        val existing = _pairings.value.firstOrNull { it.id == pairing.id }
        val merged = if (existing != null) pairing.copy(members = existing.members) else pairing
        _pairings.update { list -> list.filterNot { it.id == pairing.id } + merged }
        prefs.edit {
            putString(KEY_PAIRINGS, SyncJson.encodeToString(PairingsSerializer, _pairings.value))
            if (existing == null) remove(lastIdKey(pairing.id))
        }
    }

    fun saveMember(pairingId: String, member: Member, renewed: Boolean = false): Boolean {
        var isNew = false
        _pairings.update { list ->
            list.map { pairing ->
                if (pairing.id != pairingId) return@map pairing
                val known = pairing.members.firstOrNull { it.id == member.id }
                isNew = known == null
                val merged = if (known == null) member else member.copy(since = known.since, alias = known.alias, seen = maxOf(member.seen, known.seen), pk = if (renewed && member.pk.isNotEmpty()) member.pk else known.pk.ifEmpty { member.pk })
                pairing.copy(members = pairing.members.filterNot { it.id == member.id } + merged)
            }
        }
        writePairings()
        return isNew
    }

    fun removeMember(pairingId: String, memberId: String) {
        _pairings.update { list ->
            list.map { pairing -> if (pairing.id != pairingId) pairing else pairing.copy(members = pairing.members.filterNot { it.id == memberId }) }
        }
        writePairings()
    }

    fun setAlias(pairingId: String, memberId: String, alias: String) {
        _pairings.update { list ->
            list.map { pairing ->
                if (pairing.id != pairingId) pairing else pairing.copy(members = pairing.members.map { if (it.id == memberId) it.copy(alias = alias) else it })
            }
        }
        writePairings()
    }

    fun rekey(pairingId: String, room: String, key: String, token: String?, without: String?): Pairing? {
        val old = _pairings.value.firstOrNull { it.id == pairingId } ?: return null
        val forgetsMac = without != null && without == old.macId
        val moved = old.copy(
            inTopic = room,
            outTopic = room,
            room = room,
            key = key,
            token = token ?: old.token,
            members = old.members.filterNot { it.id == without },
            macId = if (forgetsMac) "" else old.macId,
            macName = if (forgetsMac) "" else old.macName,
        )
        _pairings.update { list -> list.filterNot { it.id == pairingId || it.id == room } + moved }
        prefs.edit {
            putString(KEY_PAIRINGS, SyncJson.encodeToString(PairingsSerializer, _pairings.value))
            remove(lastIdKey(pairingId))
        }
        return moved
    }

    fun moveToRoom(pairingId: String, room: String): Pairing? {
        val old = _pairings.value.firstOrNull { it.id == pairingId } ?: return null
        val moved = old.copy(inTopic = room, outTopic = room, room = room)
        _pairings.update { list -> list.filterNot { it.id == pairingId || it.id == room } + moved }
        prefs.edit {
            putString(KEY_PAIRINGS, SyncJson.encodeToString(PairingsSerializer, _pairings.value))
            remove(lastIdKey(pairingId))
        }
        return moved
    }

    fun moveServer(from: String, to: String) {
        if (_pairings.value.none { it.server == from }) return
        _pairings.update { list -> list.map { if (it.server == from) it.copy(server = to) else it } }
        writePairings()
    }

    fun removePairing(id: String) {
        _pairings.update { list -> list.filterNot { it.id == id } }
        prefs.edit {
            putString(KEY_PAIRINGS, SyncJson.encodeToString(PairingsSerializer, _pairings.value))
            remove(lastIdKey(id))
        }
    }

    fun clear() {
        prefs.edit { clear() }
        _pairings.value = emptyList()
        _history.value = emptyList()
    }

    fun saveMacName(id: String, name: String) {
        val pairing = _pairings.value.firstOrNull { it.id == id } ?: return
        if (name.isEmpty() || name == pairing.macName) return
        _pairings.update { list -> list.map { if (it.id == id) it.copy(macName = name) else it } }
        prefs.edit { putString(KEY_PAIRINGS, SyncJson.encodeToString(PairingsSerializer, _pairings.value)) }
    }

    fun addHistory(item: ClipItem): List<ClipItem> {
        var dropped = emptyList<ClipItem>()
        _history.update { items ->
            val all = listOf(item) + items.filterNot { it.id == item.id }
            val loose = all.filterNot { it.isPinned }
            dropped = loose.drop(HISTORY_SIZE)
            all.filterNot { it in dropped }
        }
        writeHistory()
        return dropped
    }

    fun setPinned(id: String, isPinned: Boolean) {
        _history.update { items -> items.map { if (it.id == id) it.copy(isPinned = isPinned) else it } }
        writeHistory()
    }

    fun removeHistory(id: String): ClipItem? {
        val item = _history.value.firstOrNull { it.id == id } ?: return null
        _history.update { items -> items.filterNot { it.id == id } }
        writeHistory()
        return item
    }

    private fun writeHistory() = prefs.edit { putString(KEY_HISTORY, SyncJson.encodeToString(HistorySerializer, _history.value)) }

    private fun writePairings() = prefs.edit { putString(KEY_PAIRINGS, SyncJson.encodeToString(PairingsSerializer, _pairings.value)) }

    private fun readPairings(): List<Pairing> {
        prefs.getString(KEY_PAIRINGS, null)?.let { raw ->
            return runCatching { SyncJson.decodeFromString(PairingsSerializer, raw) }.getOrDefault(emptyList())
        }
        val legacy = prefs.getString(KEY_LEGACY_PAIRING, null)?.let { raw ->
            runCatching { SyncJson.decodeFromString(Pairing.serializer(), raw) }.getOrNull()
        } ?: return emptyList()
        val migrated = legacy.copy(macName = legacy.macName.ifEmpty { prefs.getString(KEY_LEGACY_MAC, null).orEmpty() })
        val lastId = prefs.getString(KEY_LEGACY_LAST_ID, null)
        prefs.edit {
            putString(KEY_PAIRINGS, SyncJson.encodeToString(PairingsSerializer, listOf(migrated)))
            lastId?.let { putString(lastIdKey(migrated.id), it) }
            remove(KEY_LEGACY_PAIRING)
            remove(KEY_LEGACY_MAC)
            remove(KEY_LEGACY_LAST_ID)
        }
        return listOf(migrated)
    }

    private fun readHistory(): List<ClipItem> =
        prefs.getString(KEY_HISTORY, null)?.let { raw ->
            runCatching { SyncJson.decodeFromString(HistorySerializer, raw) }.getOrNull()
        }.orEmpty()

    private fun lastIdKey(id: String): String = "$KEY_LAST_ID_PREFIX$id"

    private companion object {
        private const val PREFS = "tossy_sync"
        private const val KEY_PAIRINGS = "pairings"
        private const val KEY_HISTORY = "history"
        private const val KEY_LAST_ID_PREFIX = "last_id."
        private const val KEY_LEGACY_PAIRING = "pairing"
        private const val KEY_LEGACY_MAC = "mac_name"
        private const val KEY_LEGACY_LAST_ID = "last_id"
        private const val HISTORY_SIZE = 100
        private val PairingsSerializer = ListSerializer(Pairing.serializer())
        private val HistorySerializer = ListSerializer(ClipItem.serializer())
    }
}
