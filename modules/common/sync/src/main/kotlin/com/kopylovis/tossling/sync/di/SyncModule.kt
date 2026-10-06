package com.kopylovis.tossling.sync.di

import com.kopylovis.tossling.sync.alerts.AlertNotifications
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.alerts.AlertStore
import com.kopylovis.tossling.sync.alerts.ProjectIcons
import com.kopylovis.tossling.sync.clipboard.ClipboardBridge
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.PairingStore
import com.kopylovis.tossling.sync.data.RoomBackup
import com.kopylovis.tossling.sync.data.SyncCoordinator
import com.kopylovis.tossling.sync.data.SyncSettings
import com.kopylovis.tossling.sync.entry.ClipSender
import com.kopylovis.tossling.sync.network.NtfyClient
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import com.kopylovis.tossling.sync.push.PushRegistrar
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val syncModule = module {
    singleOf(constructor = ::PairingStore)
    singleOf(constructor = ::SyncSettings)
    singleOf(constructor = ::NtfyClient)
    singleOf(constructor = ::ClipboardBridge)
    singleOf(constructor = ::SyncNotifications)
    singleOf(constructor = ::PushRegistrar)
    singleOf(constructor = ::RoomBackup)
    singleOf(constructor = ::ClipRepository)
    singleOf(constructor = ::ClipSender)
    singleOf(constructor = ::ProjectIcons)
    singleOf(constructor = ::AlertStore)
    singleOf(constructor = ::AlertNotifications)
    singleOf(constructor = ::AlertRepository)
    singleOf(constructor = ::SyncCoordinator)
}
