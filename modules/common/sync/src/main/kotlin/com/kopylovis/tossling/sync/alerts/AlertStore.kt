package com.kopylovis.tossling.sync.alerts

import android.content.Context
import androidx.core.content.edit
import com.kopylovis.tossling.protocol.SyncJson
import com.kopylovis.tossling.protocol.alerts.Alert
import com.kopylovis.tossling.protocol.alerts.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

internal class AlertStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val file = File(context.filesDir, FILE)
    private val lock = Any()

    private val _projects = MutableStateFlow(readProjects())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _alerts = MutableStateFlow(readAlerts())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    fun pendingRemovals(): Set<String> = prefs.getStringSet(KEY_REMOVED, null).orEmpty()

    fun setPendingRemovals(topics: Set<String>) = prefs.edit { putStringSet(KEY_REMOVED, topics) }

    fun formerServers(): Set<String> = prefs.getStringSet(KEY_FORMER_SERVERS, null).orEmpty()

    fun moveServer(from: String, to: String) {
        prefs.edit { putStringSet(KEY_FORMER_SERVERS, formerServers() + from - to) }
        if (_projects.value.none { it.endpoint.server == from }) return
        _projects.update { list -> list.map { if (it.endpoint.server == from) it.copy(endpoint = it.endpoint.copy(server = to)) else it } }
        writeProjects()
    }

    fun lastId(topic: String): String = prefs.getString("$KEY_LAST_ID$topic", null).orEmpty()

    fun saveLastId(topic: String, id: String) = prefs.edit { putString("$KEY_LAST_ID$topic", id) }

    fun saveProject(project: Project) {
        _projects.update { list ->
            val index = list.indexOfFirst { it.topic == project.topic }
            if (index >= 0) list.toMutableList().apply { set(index, project) } else list + project
        }
        writeProjects()
    }

    fun removeProject(topic: String) {
        _projects.update { list -> list.filterNot { it.topic == topic } }
        prefs.edit { remove("$KEY_LAST_ID$topic") }
        writeProjects()
        updateAlerts { list -> list.filterNot { it.topic == topic } }
    }

    fun add(alerts: List<Alert>): List<Alert> {
        if (alerts.isEmpty()) return emptyList()
        var added = emptyList<Alert>()
        updateAlerts { list ->
            val known = list.mapTo(HashSet()) { it.id }
            added = alerts.filterNot { it.id in known }
            (added + list).sortedByDescending { it.time }.take(CAPACITY)
        }
        return added
    }

    fun markRead(ids: Set<String>?) = updateAlerts { list -> list.map { if (ids == null || it.id in ids) it.copy(isRead = true) else it } }

    fun markUnread(id: String) = updateAlerts { list -> list.map { if (it.id == id) it.copy(isRead = false) else it } }

    fun delete(id: String) = updateAlerts { list -> list.filterNot { it.id == id } }

    fun clear(topic: String?) = updateAlerts { list -> if (topic == null) emptyList() else list.filterNot { it.topic == topic } }

    private fun updateAlerts(change: (List<Alert>) -> List<Alert>) {
        synchronized(lock) {
            _alerts.update(change)
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(SyncJson.encodeToString(AlertsSerializer, _alerts.value))
            tmp.renameTo(file)
        }
    }

    private fun writeProjects() = prefs.edit { putString(KEY_PROJECTS, SyncJson.encodeToString(ProjectsSerializer, _projects.value)) }

    private fun readProjects(): List<Project> =
        prefs.getString(KEY_PROJECTS, null)?.let { raw ->
            runCatching { SyncJson.decodeFromString(ProjectsSerializer, raw) }.getOrNull()
        }.orEmpty()

    private fun readAlerts(): List<Alert> =
        runCatching { if (file.exists()) SyncJson.decodeFromString(AlertsSerializer, file.readText()) else emptyList() }.getOrDefault(emptyList())

    private companion object {
        private const val PREFS = "tossy_alerts"
        private const val FILE = "alerts.json"
        private const val KEY_PROJECTS = "projects"
        private const val KEY_LAST_ID = "last_id."
        private const val KEY_FORMER_SERVERS = "former_servers"
        private const val KEY_REMOVED = "removed_topics"
        private const val CAPACITY = 500
        private val ProjectsSerializer = ListSerializer(Project.serializer())
        private val AlertsSerializer = ListSerializer(Alert.serializer())
    }
}
