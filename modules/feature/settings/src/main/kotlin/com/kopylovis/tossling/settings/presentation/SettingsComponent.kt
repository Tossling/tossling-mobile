package com.kopylovis.tossling.settings.presentation

import com.arkivanov.decompose.value.Value
import com.kopylovis.tossling.core.decompose.base.CommonComponent

internal data class SettingsScreenState(
    val isPaused: Boolean = false,
    val sendsImages: Boolean = true,
    val isInstant: Boolean = true,
    val isInstantAvailable: Boolean = true,
    val isLive: Boolean = false,
    val isServerPushless: Boolean = false,
    val isQuietHours: Boolean = false,
    val projects: Int = 0,
    val devices: Int = 0,
    val version: String = "",
)

internal interface SettingsComponent : CommonComponent {

    val state: Value<SettingsScreenState>

    fun onPauseClicked()

    fun onImagesClicked()

    fun onInstantClicked()

    fun onLiveClicked()

    fun onQuietHoursClicked()

    fun onProjectsClicked()

    fun onDevicesClicked()

    fun onBackClicked()
}
