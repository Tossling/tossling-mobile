package com.kopylovis.tossling.navigation

import com.kopylovis.tossling.navigation.delegates.Navigator
import kotlinx.serialization.Serializable

class GlobalNavigator : Navigator<GlobalNavigator.Config>() {

    @Serializable
    sealed interface Config {

        @Serializable
        data object Welcome : Config

        @Serializable
        data class Pairing(val isReconnect: Boolean = false, val link: String? = null, val isDemo: Boolean = false) : Config

        @Serializable
        data object Main : Config

        @Serializable
        data object Settings : Config

        @Serializable
        data object Devices : Config

        @Serializable
        data class Device(val pairingId: String, val deviceId: String) : Config

        @Serializable
        data object AddMac : Config

        @Serializable
        data object Projects : Config

        @Serializable
        data class Project(val topic: String? = null) : Config

        @Serializable
        data class Alert(val id: String) : Config
    }
}
