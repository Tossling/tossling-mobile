package com.kopylovis.tossling.notifications.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.protocol.alerts.Project
import com.kopylovis.tossling.protocol.alerts.ProjectException
import com.kopylovis.tossling.protocol.alerts.ProjectProblem
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.alerts.AppChoice
import com.kopylovis.tossling.sync.data.ClipRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.inject

internal class ProjectComponentImpl(
    componentContext: ComponentContext,
    private val alerts: AlertRepository,
    private val clips: ClipRepository,
    private val topic: String?,
) : BaseComponent(componentContext), ProjectComponent {

    private val globalNavigator: GlobalNavigator by inject()
    private val existing = topic?.let(alerts::project)
    private var isChannelTouched = existing != null
    private val form = MutableStateFlow(
        ProjectScreenState(
            isEdit = existing != null,
            name = existing?.name.orEmpty(),
            channel = existing?.topic.orEmpty(),
            color = existing?.colorIndex ?: (alerts.projects.value.size % Project.PROJECT_COLORS),
            server = existing?.endpoint?.server ?: clips.endpoint()?.server.orEmpty(),
            app = existing?.app?.takeIf { it.isNotEmpty() },
            iconPath = existing?.iconFile.orEmpty(),
            isCustomIcon = existing?.isCustomIcon == true,
        ).validated(),
    )
    private var isAppChosen = existing?.isIconChosen == true

    init {
        launchCoroutine {
            val apps = alerts.apps()
            form.update { current -> current.copy(apps = apps.toImmutableList(), appLabel = apps.firstOrNull { it.packageName == current.app }?.label.orEmpty()) }
        }
    }

    override val state: Value<ProjectScreenState> = form.asValue()

    override fun onBackClicked() {
        globalNavigator.pop()
    }

    override fun onNameChanged(name: String) {
        form.update { current ->
            current.copy(name = name.take(MAX_NAME), channel = if (isChannelTouched) current.channel else slug(name)).validated()
        }
        if (!isAppChosen && existing == null) {
            val match = form.value.apps.firstOrNull { it.label.equals(name.trim(), ignoreCase = true) }
            form.update { it.copy(app = match?.packageName, appLabel = match?.label.orEmpty()) }
        }
    }

    override fun onChannelChanged(channel: String) {
        if (existing != null) return
        isChannelTouched = true
        form.update { it.copy(channel = channel.lowercase().filter { char -> char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '_' }.take(MAX_CHANNEL)).validated() }
    }

    override fun onColorClicked(index: Int) {
        form.update { it.copy(color = index) }
    }

    override fun onCopyExample(example: String) {
        clips.copyText(text = example)
        messenger.done(tr("Copied", "Скопировано"))
    }

    override fun onSaveClicked() {
        val current = form.value
        if (!current.canSave || current.isSaving) return
        if (existing != null) {
            alerts.updateProject(topic = existing.topic, name = current.name, color = current.color)
            messenger.done(tr("Saved", "Сохранено"))
            globalNavigator.pop()
            return
        }
        form.update { it.copy(isSaving = true) }
        messenger.busy(tr("Checking the channel", "Проверяю канал"))
        launchCoroutine(onError = { error ->
            form.update { it.copy(isSaving = false) }
            messenger.error(problemText(error = error))
        }) {
            val added = alerts.addProject(
                name = current.name,
                topic = current.channel,
                endpoint = clips.endpoint(),
                color = current.color,
                app = if (isAppChosen) current.app.orEmpty() else null,
                image = current.iconPath.takeIf { current.isCustomIcon },
            )
            messenger.done(tr("Project added", "Проект добавлен"))
            val token = added.token
            if (token == null) {
                globalNavigator.pop()
            } else {
                form.update { it.copy(isSaving = false, token = token, tokenExample = added.example.orEmpty()) }
            }
        }
    }

    override fun onDoneClicked() {
        globalNavigator.pop()
    }

    override fun onDeleteClicked() {
        form.update { it.copy(isSheetVisible = true) }
    }

    override fun onDeleteConfirmed() {
        val project = existing ?: return
        form.update { it.copy(isSheetVisible = false) }
        launchCoroutine {
            alerts.removeProject(topic = project.topic)
            messenger.done(tr("Project deleted", "Проект удалён"))
            globalNavigator.pop()
        }
    }

    override fun onSheetDismissed() {
        form.update { it.copy(isSheetVisible = false) }
    }

    override fun onIconClicked() {
        form.update { it.copy(isPickerVisible = true, appQuery = "") }
    }

    override fun onAppPicked(app: AppChoice?) {
        isAppChosen = true
        form.update { it.copy(app = app?.packageName, appLabel = app?.label.orEmpty(), isPickerVisible = false, isCustomIcon = false, iconPath = if (it.isCustomIcon) "" else it.iconPath) }
        val project = existing ?: return
        launchCoroutine {
            alerts.setApp(topic = project.topic, packageName = app?.packageName)
            form.update { it.copy(iconPath = alerts.project(topic = project.topic)?.iconFile.orEmpty()) }
        }
    }

    override fun onAppQueryChanged(query: String) {
        form.update { it.copy(appQuery = query) }
    }

    override fun onImagePicked(bytes: ByteArray?) {
        bytes ?: return
        form.update { it.copy(isPickerVisible = false) }
        launchCoroutine {
            val path = alerts.saveImage(bytes = bytes) ?: return@launchCoroutine messenger.error(tr("Could not read the image", "Не получилось прочитать картинку"))
            isAppChosen = true
            existing?.let { alerts.setImage(topic = it.topic, path = path) }
            form.update { it.copy(app = null, appLabel = "", iconPath = path, isCustomIcon = true) }
        }
    }

    override fun onPickerDismissed() {
        form.update { it.copy(isPickerVisible = false) }
    }

    private fun ProjectScreenState.validated(): ProjectScreenState =
        copy(canSave = name.isNotBlank() && CHANNEL.matches(channel) && (isEdit || alerts.project(topic = channel) == null))

    private fun problemText(error: Throwable): String = when ((error as? ProjectException)?.problem) {
        ProjectProblem.INVALID_TOPIC -> tr("Channel: latin letters, digits, - and _", "Канал: латиница, цифры, - и _")
        ProjectProblem.DUPLICATE -> tr("This channel is already added", "Этот канал уже добавлен")
        ProjectProblem.NO_SERVER -> tr("Pair with a Mac first: it brings the server", "Сначала подключите Mac: сервер берётся от него")
        ProjectProblem.TOKEN -> tr("No access to the channel: ntfy access", "Нет доступа к каналу: ntfy access")
        ProjectProblem.NETWORK, null -> tr("No connection to the server", "Нет связи с сервером")
    }

    private companion object {
        private const val MAX_NAME = 40
        private const val MAX_CHANNEL = 64
        private val CHANNEL = Regex("^[a-z0-9_-]{1,64}$")
        private val TRANSLIT = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
        )

        private fun slug(name: String): String =
            name.lowercase().map { char -> TRANSLIT[char] ?: char.toString() }.joinToString(separator = "")
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(MAX_CHANNEL)
    }
}
