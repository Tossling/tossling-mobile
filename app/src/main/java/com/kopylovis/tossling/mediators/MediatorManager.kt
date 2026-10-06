package com.kopylovis.tossling.mediators

import com.kopylovis.tossling.mediators.mediator.DevicesMediator
import com.kopylovis.tossling.mediators.mediator.HomeMediator
import com.kopylovis.tossling.mediators.mediator.NotificationsMediator
import com.kopylovis.tossling.mediators.mediator.PairingMediator
import com.kopylovis.tossling.mediators.mediator.SettingsMediator

internal class MediatorManager {

    companion object {
        val devicesMediator: DevicesMediator = DevicesMediator()
        val homeMediator: HomeMediator = HomeMediator()
        val notificationsMediator: NotificationsMediator = NotificationsMediator()
        val pairingMediator: PairingMediator = PairingMediator()
        val settingsMediator: SettingsMediator = SettingsMediator()
    }
}
