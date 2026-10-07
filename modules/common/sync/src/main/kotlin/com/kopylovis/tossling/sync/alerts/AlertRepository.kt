package com.kopylovis.tossling.sync.alerts

import android.util.Log
import com.kopylovis.tossling.sync.data.Endpoint
import com.kopylovis.tossling.sync.data.PairingStore
import com.kopylovis.tossling.sync.data.SyncSettings
import com.kopylovis.tossling.sync.data.isRoomTopic
import com.kopylovis.tossling.sync.network.NtfyClient
import com.kopylovis.tossling.sync.network.NtfyEvent
import com.kopylovis.tossling.sync.network.NtfyException
import com.kopylovis.tossling.sync.push.PushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AlertRepository internal constructor(
    private val store: AlertStore,
    private val pairings: PairingStore,
    private val icons: ProjectIcons,
    private val client: NtfyClient,
    private val notifications: AlertNotifications,
    private val push: PushRegistrar,
    private val settings: SyncSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val syncMutex = Mutex()
    @Volatile private var syncedAt = 0L

    val projects: StateFlow<List<Project>> = store.projects

    val alerts: StateFlow<List<Alert>> = store.alerts

    val unreadCount: StateFlow<Int> = store.alerts
        .map { list -> list.count { !it.isRead } }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = store.alerts.value.count { !it.isRead })

    init {
        store.projects.value.forEachIndexed { index, project ->
            if (project.color !in 0 until Project.PROJECT_COLORS) store.saveProject(project = project.copy(color = index % Project.PROJECT_COLORS))
        }
        scope.launch {
            store.projects.value.filter { !it.isIconChosen && it.app.isEmpty() }.forEach { project ->
                val app = icons.match(name = project.name) ?: return@forEach
                val file = icons.saveApp(packageName = app.packageName) ?: return@forEach
                store.projects.value.firstOrNull { it.topic == project.topic }?.let { store.saveProject(project = it.copy(app = app.packageName, iconFile = file)) }
            }
        }
    }

    suspend fun apps(): List<AppChoice> = icons.apps()

    suspend fun matchApp(name: String): AppChoice? = icons.match(name = name)

    suspend fun saveImage(bytes: ByteArray): String? = icons.saveImage(bytes = bytes)

    fun setImage(topic: String, path: String) {
        val project = store.projects.value.firstOrNull { it.topic == topic } ?: return
        project.iconFile.takeIf { it.isNotEmpty() && it != path }?.let(icons::forget)
        store.saveProject(project = project.copy(app = "", iconFile = path, isIconChosen = true, isCustomIcon = true))
    }

    suspend fun setApp(topic: String, packageName: String?) {
        val project = store.projects.value.firstOrNull { it.topic == topic } ?: return
        val file = packageName?.let { icons.saveApp(packageName = it) }
        val fallback = project.iconUrl.takeIf { it.isNotEmpty() }?.let { icons.saveUrl(url = it) }.orEmpty()
        val current = store.projects.value.firstOrNull { it.topic == topic } ?: return
        current.iconFile.takeIf { current.isCustomIcon && it.isNotEmpty() }?.let(icons::forget)
        store.saveProject(project = current.copy(app = if (file != null) packageName.orEmpty() else "", iconFile = file ?: fallback, isIconChosen = true, isCustomIcon = false))
    }

    fun owns(topic: String): Boolean = store.projects.value.any { it.topic == topic }

    fun alert(id: String): Alert? = store.alerts.value.firstOrNull { it.id == id }

    fun project(topic: String): Project? = store.projects.value.firstOrNull { it.topic == topic }

    suspend fun syncProjects(force: Boolean = false) = syncMutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && now - syncedAt < SYNC_INTERVAL_MS) return@withLock
        val server = pairings.pairings.value.firstOrNull()?.endpoint ?: store.projects.value.firstOrNull()?.endpoint ?: return@withLock
        runCatching {
            val removed = store.pendingRemovals()
            removed.forEach { topic -> client.removeSubscription(endpoint = server, topic = topic) }
            if (removed.isNotEmpty()) store.setPendingRemovals(topics = emptySet())
            val remote = client.subscriptions(endpoint = server)
                .filter { it.baseUrl.trimEnd('/') == server.server && TOPIC.matches(it.topic) && !isRoomTopic(topic = it.topic) }
            val remoteTopics = remote.map { it.topic }.toSet()
            val paired = pairings.pairings.value.map { it.server }.toSet() + server.server
            movedProjects(projects = store.projects.value, paired = paired, remoteTopics = remoteTopics).forEach { project ->
                moveProject(project = project, server = server)
            }
            store.projects.value.filter { it.endpoint.server == server.server }.forEach { project ->
                when {
                    project.topic in remoteTopics -> {
                        val name = remote.first { it.topic == project.topic }.displayName?.takeIf { it.isNotBlank() }
                        if (!project.isSynced || (name != null && name != project.name)) {
                            store.saveProject(project = project.copy(name = name ?: project.name, isSynced = true))
                        }
                    }
                    project.isSynced -> {
                        push.unsubscribe(topic = project.topic)
                        store.removeProject(topic = project.topic)
                        notifications.cancelProject(topic = project.topic)
                    }
                    else -> {
                        client.saveSubscription(endpoint = server, topic = project.topic, name = project.name, isNew = true)
                        store.saveProject(project = project.copy(isSynced = true))
                    }
                }
            }
            remote.filterNot { owns(it.topic) }.forEach { subscription ->
                runCatching { create(name = subscription.displayName.orEmpty(), topic = subscription.topic, server = server, color = -1, app = null, image = null, isSynced = true) }
                    .onFailure { Log.w(TAG, "could not add ${subscription.topic} from the server", it) }
            }
            syncedAt = now
        }.onFailure { Log.w(TAG, "project sync failed", it) }
    }

    private suspend fun moveProject(project: Project, server: Endpoint) {
        val backlog = runCatching { client.poll(endpoint = server, topic = project.topic, since = BACKLOG) }.getOrDefault(emptyList())
        store.add(alerts = backlog.mapNotNull { it.toAlert(isRead = true) })
        store.saveLastId(topic = project.topic, id = backlog.lastOrNull()?.id.orEmpty())
        store.saveProject(project = project.copy(endpoint = server, isSynced = true))
        Log.i(TAG, "${project.topic} moved from ${project.endpoint.host} to ${server.host}")
    }

    suspend fun addProject(name: String, topic: String, endpoint: Endpoint?, color: Int = -1, app: String? = null, image: String? = null): AddedProject {
        val cleanTopic = topic.trim()
        if (!TOPIC.matches(cleanTopic)) throw ProjectException(ProjectProblem.INVALID_TOPIC)
        if (owns(cleanTopic)) throw ProjectException(ProjectProblem.DUPLICATE)
        val server = endpoint ?: throw ProjectException(ProjectProblem.NO_SERVER)
        if (client.isTosslingServer(endpoint = server)) {
            val created = try {
                client.createProject(endpoint = server, topic = cleanTopic, name = name.trim().ifEmpty { cleanTopic })
            } catch (error: NtfyException) {
                throw ProjectException(if (error.code == 401 || error.code == 403) ProjectProblem.TOKEN else ProjectProblem.NETWORK, error)
            } catch (error: Exception) {
                throw ProjectException(ProjectProblem.NETWORK, error)
            }
            val project = create(name = name, topic = cleanTopic, server = server, color = color, app = app, image = image, isSynced = true)
            return AddedProject(project = project, token = created.token?.takeIf { it.isNotEmpty() }, example = created.example?.takeIf { it.isNotEmpty() })
        }
        val project = create(name = name, topic = cleanTopic, server = server, color = color, app = app, image = image, isSynced = false)
        val synced = runCatching { client.saveSubscription(endpoint = server, topic = cleanTopic, name = project.name, isNew = true) }.isSuccess
        if (synced) store.saveProject(project = project.copy(isSynced = true))
        return AddedProject(project = project)
    }

    private suspend fun create(name: String, topic: String, server: Endpoint, color: Int, app: String?, image: String?, isSynced: Boolean): Project {
        val cleanTopic = topic
        val backlog = try {
            client.poll(endpoint = server, topic = cleanTopic, since = BACKLOG)
        } catch (error: NtfyException) {
            throw ProjectException(if (error.code == 401 || error.code == 403) ProjectProblem.TOKEN else ProjectProblem.NETWORK, error)
        } catch (error: Exception) {
            throw ProjectException(ProjectProblem.NETWORK, error)
        }
        val tint = if (color in 0 until Project.PROJECT_COLORS) color else store.projects.value.size % Project.PROJECT_COLORS
        val chosen = if (image != null) "" else app ?: icons.match(name = name)?.packageName
        val iconFile = image ?: chosen?.takeIf { it.isNotEmpty() }?.let { icons.saveApp(packageName = it) }
        val project = Project(
            topic = cleanTopic,
            name = name.trim().ifEmpty { cleanTopic },
            endpoint = server,
            color = tint,
            app = if (iconFile != null && image == null) chosen.orEmpty() else "",
            iconFile = iconFile.orEmpty(),
            isIconChosen = app != null || image != null,
            isCustomIcon = image != null,
            isSynced = isSynced,
        )
        store.saveProject(project = project)
        val alerts = backlog.mapNotNull { it.toAlert(isRead = true) }
        store.add(alerts = alerts)
        backlog.lastOrNull()?.let { store.saveLastId(topic = cleanTopic, id = it.id) }
        if (settings.isInstant.value) push.subscribe(topic = cleanTopic)
        return refreshIcon(project = project, icon = alerts.lastOrNull { !it.icon.isNullOrBlank() }?.icon)
    }

    fun updateProject(topic: String, name: String? = null, color: Int? = null, isMuted: Boolean? = null) {
        val project = store.projects.value.firstOrNull { it.topic == topic } ?: return
        val updated = project.copy(
            name = name?.trim()?.ifEmpty { project.name } ?: project.name,
            color = color ?: project.color,
            isMuted = isMuted ?: project.isMuted,
        )
        store.saveProject(project = updated)
        if (updated.isSynced && updated.name != project.name) {
            scope.launch { runCatching { client.saveSubscription(endpoint = endpointOf(project = updated), topic = topic, name = updated.name, isNew = false) } }
        }
    }

    suspend fun removeProject(topic: String) {
        store.projects.value.firstOrNull { it.topic == topic }?.let { project ->
            val server = endpointOf(project = project)
            val revoked = client.isTosslingServer(endpoint = server) && runCatching { client.deleteProject(endpoint = server, topic = topic) }.isSuccess
            val removed = revoked || runCatching { client.removeSubscription(endpoint = server, topic = topic) }.isSuccess
            if (!removed && project.isSynced) store.setPendingRemovals(topics = store.pendingRemovals() + topic)
        }
        store.projects.value.firstOrNull { it.topic == topic }?.iconFile?.takeIf { it.isNotEmpty() }?.let(icons::forget)
        push.unsubscribe(topic = topic)
        store.removeProject(topic = topic)
        notifications.cancelProject(topic = topic)
    }

    internal suspend fun subscribeAll(subscribe: Boolean) {
        store.projects.value.forEach { project ->
            if (subscribe) push.subscribe(topic = project.topic) else push.unsubscribe(topic = project.topic)
        }
    }

    fun ensureChannels() = notifications.ensureChannels()

    suspend fun fetch(topic: String? = null): Int = mutex.withLock {
        if (topic == null) syncProjects()
        val targets = store.projects.value.filter { topic == null || it.topic == topic }
        var received = 0
        var failure: Exception? = null
        var succeeded = false
        targets.forEach { project ->
            try {
                val events = client.poll(endpoint = endpointOf(project = project), topic = project.topic, since = store.lastId(project.topic).ifEmpty { BACKLOG })
                succeeded = true
                val added = store.add(alerts = events.mapNotNull { it.toAlert(isRead = false) })
                events.lastOrNull()?.let { store.saveLastId(topic = project.topic, id = it.id) }
                val shown = refreshIcon(project = project, icon = added.lastOrNull { !it.icon.isNullOrBlank() }?.icon)
                added.forEach { alert -> notifications.show(project = shown, alert = alert, isQuietTime = settings.isQuietTime()) }
                received += added.size
            } catch (error: Exception) {
                Log.e(TAG, "fetch ${project.topic} failed", error)
                failure = error
            }
        }
        if (!succeeded) failure?.let { throw it }
        received
    }

    fun markRead(id: String) {
        store.markRead(ids = setOf(id))
        notifications.cancel(alertId = id)
    }

    fun markUnread(id: String) {
        store.markUnread(id = id)
    }

    fun markAllRead() {
        store.markRead(ids = null)
        notifications.cancelAll()
    }

    fun delete(id: String) {
        store.delete(id = id)
        notifications.cancel(alertId = id)
    }

    fun clear(topic: String? = null) {
        store.clear(topic = topic)
        if (topic == null) notifications.cancelAll() else notifications.cancelProject(topic = topic)
    }

    private suspend fun refreshIcon(project: Project, icon: String?): Project {
        if (icon == null || icon == project.iconUrl) return project
        val file = icons.saveUrl(url = icon) ?: return project
        val current = store.projects.value.firstOrNull { it.topic == project.topic } ?: return project
        val updated = if (current.app.isEmpty() && !current.isCustomIcon) current.copy(iconUrl = icon, iconFile = file) else current.copy(iconUrl = icon)
        store.saveProject(project = updated)
        return updated
    }

    internal fun liveTopics(): List<Pair<Endpoint, String>> = store.projects.value.map { endpointOf(project = it) to it.topic }

    private fun endpointOf(project: Project): Endpoint =
        pairings.pairings.value.firstOrNull { it.server == project.endpoint.server }?.endpoint ?: project.endpoint

    private fun NtfyEvent.toAlert(isRead: Boolean): Alert? {
        if (event != MESSAGE_EVENT) return null
        return Alert(
            id = id,
            topic = topic,
            title = title.orEmpty(),
            message = message.orEmpty(),
            priority = priority ?: Alert.DEFAULT_PRIORITY,
            click = click?.takeIf { it.isNotBlank() },
            tags = tags.orEmpty(),
            isMarkdown = contentType == MARKDOWN,
            icon = icon?.takeIf { it.isNotBlank() },
            time = time * 1000,
            isRead = isRead,
        )
    }

    private companion object {
        private const val TAG = "AlertRepository"
        private const val MESSAGE_EVENT = "message"
        private const val MARKDOWN = "text/markdown"
        private const val BACKLOG = "24h"
        private val TOPIC = Regex("^[A-Za-z0-9_-]{1,64}$")
        private const val SYNC_INTERVAL_MS = 5 * 60_000L
    }
}

internal fun movedProjects(projects: List<Project>, paired: Set<String>, remoteTopics: Set<String>): List<Project> =
    projects.filter { it.endpoint.server !in paired && it.topic in remoteTopics }
