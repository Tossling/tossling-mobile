package com.kopylovis.tossling.sync.entry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kopylovis.tossling.sync.data.SyncCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class LiveBootReceiver : BroadcastReceiver(), KoinComponent {

    private val coordinator: SyncCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) coordinator.restoreLive()
    }
}
