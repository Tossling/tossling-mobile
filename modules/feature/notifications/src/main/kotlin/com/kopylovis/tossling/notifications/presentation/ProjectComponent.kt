package com.kopylovis.tossling.notifications.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent
import com.kopylovis.tossling.sync.alerts.AppChoice
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data class ProjectScreenState(
    val isEdit: Boolean = false,
    val name: String = "",
    val channel: String = "",
    val color: Int = 0,
    val server: String = "",
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val isSheetVisible: Boolean = false,
    val app: String? = null,
    val appLabel: String = "",
    val iconPath: String = "",
    val isPickerVisible: Boolean = false,
    val apps: ImmutableList<AppChoice> = persistentListOf(),
    val appQuery: String = "",
    val isCustomIcon: Boolean = false,
    val token: String = "",
    val tokenExample: String = "",
)

internal interface ProjectComponent : CommonComponent {

    val state: Value<ProjectScreenState>

    fun onBackClicked()

    fun onNameChanged(name: String)

    fun onChannelChanged(channel: String)

    fun onColorClicked(index: Int)

    fun onCopyExample(example: String)

    fun onDoneClicked()

    fun onSaveClicked()

    fun onDeleteClicked()

    fun onDeleteConfirmed()

    fun onSheetDismissed()

    fun onIconClicked()

    fun onAppPicked(app: AppChoice?)

    fun onAppQueryChanged(query: String)

    fun onPickerDismissed()

    fun onImagePicked(bytes: ByteArray?)
}
