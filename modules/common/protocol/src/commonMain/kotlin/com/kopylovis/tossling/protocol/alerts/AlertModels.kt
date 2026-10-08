package com.kopylovis.tossling.protocol.alerts

import com.kopylovis.tossling.protocol.Endpoint
import com.kopylovis.tossling.protocol.SyncJson
import com.kopylovis.tossling.protocol.network.NtfyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class Project(
    val topic: String,
    val name: String,
    val endpoint: Endpoint,
    val color: Int = -1,
    val isMuted: Boolean = false,
    val app: String = "",
    val iconUrl: String = "",
    val iconFile: String = "",
    val isIconChosen: Boolean = false,
    val isCustomIcon: Boolean = false,
    val isSynced: Boolean = false,
) {
    val colorIndex: Int get() = if (color in 0 until PROJECT_COLORS) color else (topic.hashCode() and Int.MAX_VALUE) % PROJECT_COLORS

    val initials: String get() = projectInitials(name = name)

    companion object {
        const val PROJECT_COLORS = 6
    }
}

fun projectInitials(name: String): String {
    val clean = name.trim()
    val first = clean.firstOrNull() ?: return "?"
    val second = clean.getOrNull(1)
    return if (first.isLowerCase() && second != null && second.isLetter()) clean.take(2) else first.uppercase()
}

@Serializable
data class Alert(
    val id: String,
    val topic: String,
    val title: String,
    val message: String,
    val priority: Int = DEFAULT_PRIORITY,
    val click: String? = null,
    val tags: List<String> = emptyList(),
    val isMarkdown: Boolean = false,
    val time: Long,
    val isRead: Boolean = false,
    val icon: String? = null,
) {
    fun titleFor(project: String): String {
        val parts = title.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
        val kept = mutableListOf<String>()
        parts.forEach { part ->
            val isProject = part.equals(project.trim(), ignoreCase = true)
            val isRepeat = kept.any { it.equals(part, ignoreCase = true) }
            if (!isProject && !isRepeat) kept += part
        }
        return kept.joinToString(separator = " · ")
    }

    val isUrgent: Boolean get() = priority >= URGENT_PRIORITY

    val isQuiet: Boolean get() = priority <= QUIET_PRIORITY

    companion object {
        const val DEFAULT_PRIORITY = 3
        const val URGENT_PRIORITY = 5
        const val QUIET_PRIORITY = 2
    }
}

enum class ProjectProblem { INVALID_TOPIC, DUPLICATE, NO_SERVER, TOKEN, NETWORK }

data class AddedProject(
    val project: Project,
    val token: String? = null,
    val example: String? = null,
)

class ProjectException(val problem: ProjectProblem, cause: Throwable? = null) : Exception(problem.name, cause)

fun alertOf(event: NtfyEvent): Alert? {
    if (event.event != "message") return null
    return Alert(
        id = event.id,
        topic = event.topic,
        title = event.title.orEmpty(),
        message = event.message.orEmpty(),
        priority = event.priority ?: Alert.DEFAULT_PRIORITY,
        click = event.click?.takeIf { it.isNotBlank() },
        tags = event.tags.orEmpty(),
        isMarkdown = event.contentType == "text/markdown",
        time = event.time,
        icon = event.icon?.takeIf { it.isNotBlank() },
    )
}

fun encodeAlerts(alerts: List<Alert>): String = SyncJson.encodeToString(ListSerializer(Alert.serializer()), alerts)

fun decodeAlerts(text: String): List<Alert> =
    runCatching { SyncJson.decodeFromString(ListSerializer(Alert.serializer()), text) }.getOrDefault(emptyList())
