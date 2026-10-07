package com.kopylovis.tossling

import android.app.Application
import com.kopylovis.tossling.di.initKoin
import com.kopylovis.tossling.sync.data.SyncCoordinator
import org.koin.android.ext.android.inject

class TosslingApp : Application() {

    private val coordinator: SyncCoordinator by inject()

    override fun onCreate() {
        super.onCreate()
        initKoin(context = this)
        coordinator.onAppStarted()
    }
}
