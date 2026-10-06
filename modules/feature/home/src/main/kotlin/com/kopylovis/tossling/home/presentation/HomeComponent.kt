package com.kopylovis.tossling.home.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.sync.data.ClipItem
import com.kopylovis.tossling.sync.data.RoomDevice
import com.kopylovis.tossling.sync.data.Transfer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data class HomeScreenState(
    val isRoom: Boolean = false,
    val devices: ImmutableList<RoomDevice> = persistentListOf(),
    val macName: String = "",
    val server: String = "",
    val isOnline: Boolean = true,
    val lastContact: Long = 0,
    val isInstant: Boolean = true,
    val showsTileHint: Boolean = false,
    val isRefreshing: Boolean = false,
    val history: ImmutableList<ClipItem> = persistentListOf(),
    val pick: PickState? = null,
    val transfer: Transfer? = null,
    val preview: ClipItem? = null,
    val query: String = "",
    val leaving: String? = null,
)

internal data class PickState(
    val preview: String,
    val targets: ImmutableList<RoomDevice>,
)

internal interface HomeComponent : CommonComponent {

    val state: Value<HomeScreenState>

    fun onSendClicked()

    fun onTargetPicked(device: RoomDevice?)

    fun onPickDismissed()

    fun onRefresh()

    fun onItemClicked(item: ClipItem)

    fun onSettingsClicked()

    fun onDeviceClicked(device: RoomDevice)

    fun onAddMacClicked()

    fun onTileHintClicked()

    fun onTileHintClosed()

    fun onTransferCancelled()

    fun onPreviewCopied()

    fun onPreviewShared()

    fun onPinToggled(item: ClipItem)

    fun onDeleteClicked(item: ClipItem)

    fun onQueryChanged(query: String)

    fun onLeaveRoomClicked(pairingId: String)

    fun onLeaveConfirmed()

    fun onLeaveDismissed()

    fun onPreviewDismissed()
}
