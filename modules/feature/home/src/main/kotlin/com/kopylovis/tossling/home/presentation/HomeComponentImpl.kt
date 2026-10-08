package com.kopylovis.tossling.home.presentation

import android.content.Context
import android.content.Intent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnResume
import com.kopylovis.tossling.core.decompose.asValueUtil
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.protocol.ClipItem
import com.kopylovis.tossling.protocol.ClipKind
import com.kopylovis.tossling.protocol.MacDevice
import com.kopylovis.tossling.protocol.Pairing
import com.kopylovis.tossling.protocol.RoomDevice
import com.kopylovis.tossling.protocol.localDeviceNames
import com.kopylovis.tossling.sync.clipboard.Outgoing
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator
import com.kopylovis.tossling.sync.data.SyncSettings
import com.kopylovis.tossling.sync.entry.ClipSender
import com.kopylovis.tossling.sync.entry.Prepared
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.component.inject

internal class HomeComponentImpl(
    componentContext: ComponentContext,
    private val repository: ClipRepository,
    private val sender: ClipSender,
    private val settings: SyncSettings,
) : BaseComponent(componentContext), HomeComponent {

    private val globalNavigator: GlobalNavigator by inject()
    private val coordinator: SyncCoordinator by inject()
    private val context: Context by inject()
    private val isRefreshing = MutableStateFlow(false)
    private val pick = MutableStateFlow<PickState?>(null)
    private val preview = MutableStateFlow<ClipItem?>(null)
    private val query = MutableStateFlow("")
    private val leaving = MutableStateFlow<String?>(null)
    private var pending: Outgoing? = null

    override val state: Value<HomeScreenState> =
        combine(repository.macs, repository.devices, repository.pairings, repository.history, ::homeState)
            .combine(settings.isInstant) { state, instant -> state.copy(isInstant = instant && repository.isPushAvailable) }
            .combine(settings.isTileHintHidden) { state, hidden -> state.copy(showsTileHint = sender.canRequestTile && !hidden) }
            .combine(isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) }
            .combine(pick) { state, pick -> state.copy(pick = pick) }
            .combine(repository.transfer) { state, transfer -> state.copy(transfer = transfer) }
            .combine(preview) { state, preview -> state.copy(preview = preview) }
            .combine(query) { state, query -> state.copy(query = query) }
            .combine(leaving) { state, leaving -> state.copy(leaving = leaving) }
            .asValueUtil(
                initialValue = homeState(macs = repository.macs.value, devices = repository.devices.value, pairings = repository.pairings.value, history = repository.history.value),
                lifecycle = lifecycle,
            )

    init {
        lifecycle.doOnResume { refresh(quiet = true) }
        lifecycle.doOnDestroy { dropPending() }
    }

    override fun onSendClicked() {
        when (val prepared = sender.prepareClipboard()) {
            is Prepared.Refused -> messenger.info(sender.text(id = prepared.message))
            is Prepared.Ready -> {
                val others = repository.devices.value.filter { !it.isSelf }
                if (others.size <= 1) {
                    send(outgoing = prepared.outgoing, target = null)
                } else {
                    dropPending()
                    pending = prepared.outgoing
                    pick.value = PickState(preview = preview(outgoing = prepared.outgoing), targets = others.toImmutableList())
                }
            }
        }
    }

    override fun onTargetPicked(device: RoomDevice?) {
        val outgoing = pending ?: return
        pending = null
        pick.value = null
        send(outgoing = outgoing, target = device)
    }

    override fun onPickDismissed() {
        pick.value = null
        dropPending()
    }

    override fun onRefresh() {
        refresh(quiet = false)
    }

    override fun onItemClicked(item: ClipItem) {
        if (item.kind == ClipKind.FILE) {
            openFile(item = item)
            return
        }
        if (item.kind == ClipKind.IMAGE) {
            if (repository.fileUri(item = item) == null) return messenger.info(tr("The image is gone", "Картинки уже нет"))
            preview.value = item
            return
        }
        repository.copyAgain(item = item)
        messenger.done(tr("Copied", "Скопировано"))
    }

    private fun openFile(item: ClipItem) {
        val uri = repository.fileUri(item = item) ?: return messenger.info(tr("The file is gone", "Файла уже нет"))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, item.mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { messenger.info(tr("No app opens this file", "Нет приложения для этого файла")) }
    }

    override fun onSettingsClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Settings)
    }

    override fun onDeviceClicked(device: RoomDevice) {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.Device(pairingId = device.pairingId, deviceId = device.id))
    }

    override fun onAddMacClicked() {
        globalNavigator.pushNew(configuration = GlobalNavigator.Config.AddMac)
    }

    override fun onTileHintClicked() {
        sender.requestTile()
    }

    override fun onTileHintClosed() {
        settings.hideTileHint()
    }

    override fun onTransferCancelled() {
        sender.cancelSending()
    }

    override fun onPreviewCopied() {
        val item = preview.value ?: return
        preview.value = null
        repository.copyAgain(item = item)
        messenger.done(tr("Copied", "Скопировано"))
    }

    override fun onPinToggled(item: ClipItem) {
        repository.setPinned(item = item, isPinned = !item.isPinned)
        messenger.done(if (item.isPinned) tr("Unpinned", "Откреплено") else tr("Pinned: it stays until you remove it", "Закреплено: останется, пока не удалишь"))
    }

    override fun onDeleteClicked(item: ClipItem) {
        repository.delete(item = item)
    }

    override fun onQueryChanged(query: String) {
        this.query.value = query
    }

    override fun onLeaveRoomClicked(pairingId: String) {
        leaving.value = pairingId
    }

    override fun onLeaveDismissed() {
        leaving.value = null
    }

    override fun onLeaveConfirmed() {
        val pairingId = leaving.value ?: return
        leaving.value = null
        messenger.busy(tr("Leaving the room", "Выхожу из комнаты"))
        launchCoroutine(onError = { error -> messenger.error(error.message ?: tr("Could not leave", "Не получилось выйти")) }) {
            repository.leave(pairingId = pairingId)
            messenger.done(tr("The phone left the room", "Телефон вышел из комнаты"))
            if (!repository.isPaired.value) globalNavigator.replaceAll(GlobalNavigator.Config.Welcome)
        }
    }

    override fun onPreviewShared() {
        val item = preview.value ?: return
        val uri = repository.fileUri(item = item) ?: return messenger.info(tr("The image is gone", "Картинки уже нет"))
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.mime ?: "image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }.onFailure { messenger.info(tr("No app to share with", "Нет приложения, чтобы поделиться")) }
    }

    override fun onPreviewDismissed() {
        preview.value = null
    }

    private fun send(outgoing: Outgoing, target: RoomDevice?) {
        if (outgoing.kind == ClipKind.FILE) {
            sender.enqueue(outgoing = outgoing, targets = target?.let { setOf(it.id) })
            messenger.info(tr("Sending in the background", "Отправляю в фоне"))
            return
        }
        val where = target?.name ?: if (repository.devices.value.count { !it.isSelf } > 1) tr("all devices", "все устройства") else "Mac"
        messenger.busy(tr("Sending to $where", "Отправляю на $where"))
        launchCoroutine(
            onError = { error ->
                outgoing.file.delete()
                messenger.error(error.message ?: tr("Not sent", "Не отправилось"))
            },
        ) {
            repository.send(outgoing = outgoing, targets = target?.let { setOf(it.id) })
            messenger.done(tr("Sent to $where", "Отправлено на $where"))
        }
    }

    private fun preview(outgoing: Outgoing): String =
        when (outgoing.kind) {
            ClipKind.TEXT -> runCatching { outgoing.file.readText().take(PREVIEW_CHARS) }.getOrDefault("").lineSequence().firstOrNull().orEmpty()
            ClipKind.IMAGE -> tr("image", "картинка")
            ClipKind.FILE -> outgoing.name ?: tr("file", "файл")
        }

    private fun dropPending() {
        pending?.file?.delete()
        pending = null
    }

    private fun refresh(quiet: Boolean) {
        if (!repository.isPaired.value || isRefreshing.value) return
        isRefreshing.value = !quiet
        launchCoroutine(
            onError = {
                isRefreshing.value = false
                if (!quiet) messenger.error(tr("No connection to the server", "Нет связи с сервером"))
            },
        ) {
            coordinator.fetch()
            repository.ping()
            isRefreshing.value = false
            if (!quiet) messenger.done(tr("Updated", "Обновлено"))
        }
    }

    private companion object {
        private const val PREVIEW_CHARS = 200
    }

    private fun homeState(macs: List<MacDevice>, devices: List<RoomDevice>, pairings: List<Pairing>, history: List<ClipItem>): HomeScreenState =
        HomeScreenState(
            isRoom = pairings.any { it.isRoom },
            devices = devices.toImmutableList(),
            macName = macs.joinToString(separator = ", ") { it.name }.ifEmpty { "Mac" },
            server = macs.map { it.host }.distinct().joinToString(separator = ", "),
            isOnline = macs.all { it.link.isOnline },
            lastContact = macs.maxOfOrNull { it.link.lastContact } ?: 0,
            history = history.map { it.copy(device = localDeviceNames(names = it.device, devices = devices)) }.toImmutableList(),
        )
}
