package com.kopylovis.tossling.di

import android.content.Context
import com.kopylovis.tossling.core.di.coreModule
import com.kopylovis.tossling.devices.DevicesFeature
import com.kopylovis.tossling.home.HomeFeature
import com.kopylovis.tossling.navigation.di.navigationModule
import com.kopylovis.tossling.notifications.NotificationsFeature
import com.kopylovis.tossling.pairing.PairingFeature
import com.kopylovis.tossling.settings.SettingsFeature
import com.kopylovis.tossling.sync.di.syncModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

fun initKoin(context: Context) {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        androidContext(context)
        modules(
            coreModule,
            navigationModule,
            syncModule,
            DevicesFeature.koinModule,
            HomeFeature.koinModule,
            NotificationsFeature.koinModule,
            PairingFeature.koinModule,
            SettingsFeature.koinModule,
        )
    }
}
