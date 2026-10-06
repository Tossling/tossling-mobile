package com.kopylovis.tossling.devices.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.kopylovis.tossling.core.decompose.base.BaseComponent
import com.kopylovis.tossling.core.presentation.tr
import com.kopylovis.tossling.navigation.GlobalNavigator
import com.kopylovis.tossling.sync.data.ClipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.component.inject

internal class DeviceComponentImpl(
    componentContext: ComponentContext,
    private val repository: ClipRepository,
    private val pairingId: String,
    private val deviceId: String,
) : BaseComponent(componentContext), DeviceComponent {

    private val globalNavigator: GlobalNavigator by inject()
    private val draft = MutableStateFlow<String?>(null)
    private val isSheetVisible = MutableStateFlow(false)
    private var isLeaving = false

    override val state: Value<DeviceScreenState> =
        combine(repository.devices, draft, isSheetVisible) { devices, name, sheet ->
            val device = devices.firstOrNull { it.pairingId == pairingId && it.id == deviceId }
            DeviceScreenState(device = device, name = name ?: device?.name.orEmpty(), isSheetVisible = sheet)
        }.asValue()

    init {
        lifecycle.doOnDestroy { onNameDone() }
    }

    override fun onBackClicked() {
        onNameDone()
        globalNavigator.pop()
    }

    override fun onNameChanged(name: String) {
        draft.value = name.take(MAX_NAME)
    }

    override fun onNameDone() {
        val name = draft.value?.trim() ?: return
        draft.value = null
        val device = state.value.device ?: return
        if (name == device.name) return
        when {
            device.isSelf -> if (name.isNotEmpty()) repository.renameLater(name = name)
            name.isEmpty() || name == device.ownName -> if (device.hasAlias) repository.setAlias(pairingId = pairingId, deviceId = deviceId, alias = "")
            else -> repository.setAlias(pairingId = pairingId, deviceId = deviceId, alias = name)
        }
    }

    override fun onDisconnectClicked() {
        isSheetVisible.value = true
    }

    override fun onDisconnectConfirmed() {
        val device = state.value.device ?: return
        if (isLeaving) return
        isLeaving = true
        isSheetVisible.value = false
        messenger.busy(tr("Disconnecting", "Отключаю"))
        launchCoroutine(onError = { error ->
            isLeaving = false
            messenger.error(error.message ?: tr("Could not disconnect", "Не получилось отключить"))
        }) {
            if (device.isSelf || device.isOwner) repository.leave(pairingId = pairingId) else repository.revoke(pairingId = pairingId, deviceId = deviceId)
            messenger.done(if (device.isOwner) tr("Left the room", "Телефон вышел из комнаты") else tr("${device.name} disconnected", "${device.name} отключён"))
            if (repository.isPaired.value) globalNavigator.pop() else globalNavigator.replaceAll(GlobalNavigator.Config.Welcome)
        }
    }

    override fun onSheetDismissed() {
        isSheetVisible.value = false
    }

    private companion object {
        private const val MAX_NAME = 40
    }
}
