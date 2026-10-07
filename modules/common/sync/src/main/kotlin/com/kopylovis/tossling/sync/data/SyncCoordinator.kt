package com.kopylovis.tossling.sync.data

import android.content.Context
import android.util.Log
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.live.LiveConnectionService
import com.kopylovis.tossling.sync.network.NtfyClient
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import com.kopylovis.tossling.sync.push.PushRegistrar
import com.kopylovis.tossling.sync.work.FetchWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SyncCoordinator internal constructor(
    private val context: Context,
    private val clips: ClipRepository,
    private val alerts: AlertRepository,
    private val notifications: SyncNotifications,
    private val settings: SyncSettings,
    private val push: PushRegistrar,
    private val client: NtfyClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isPushAvailable: Boolean get() = push.isAvailable

    fun onAppStarted() {
        notifications.createChannels()
        alerts.ensureChannels()
        settings.setHasPush(value = push.isAvailable)
        if (clips.isPaired.value || alerts.projects.value.isNotEmpty()) FetchWorker.enqueue(context = context, resubscribe = true)
        scope.launch {
            clips.pairings
                .map { pairings -> pairings.firstOrNull()?.endpoint }
                .distinctUntilChanged()
                .collect { endpoint -> checkServerPush(endpoint = endpoint) }
        }
        scope.launch {
            combine(settings.isLive, clips.isPaired, alerts.projects) { live, paired, projects -> live && (paired || projects.isNotEmpty()) }
                .distinctUntilChanged()
                .collect { wanted -> if (wanted) LiveConnectionService.start(context = context) else LiveConnectionService.stop(context = context) }
        }
    }

    private suspend fun checkServerPush(endpoint: Endpoint?) {
        if (endpoint == null) {
            settings.setServerPush(value = null)
            return
        }
        val health = runCatching { client.tosslingHealth(endpoint = endpoint) }.getOrNull()
        when {
            health != null -> settings.setServerPush(value = health.push)
            client.isReachable(endpoint = endpoint) -> settings.setServerPush(value = null)
        }
        if (health != null) moveIfAsked(endpoint = endpoint, url = health.url)
    }

    private suspend fun moveIfAsked(endpoint: Endpoint, url: String) {
        val target = serverMoveTarget(current = endpoint.server, url = url) ?: return
        val next = endpoint.copy(server = target)
        val isTossling = runCatching { client.tosslingHealth(endpoint = next) }.getOrNull()?.isTossling == true
        val accountOk = isTossling && runCatching { client.subscriptions(endpoint = next) }.isSuccess
        if (!acceptsServerMove(isTossling = isTossling, accountOk = accountOk)) {
            Log.w(TAG, "the server asks to move to $target, but it does not answer there; staying on ${endpoint.server}")
            return
        }
        clips.moveServer(from = endpoint.server, to = target)
        alerts.moveServer(from = endpoint.server, to = target)
        Log.i(TAG, "moved from ${endpoint.server} to $target")
    }

    fun restoreLive() {
        if (settings.isLive.value && (clips.isPaired.value || alerts.projects.value.isNotEmpty())) LiveConnectionService.start(context = context)
    }

    fun setLive(value: Boolean) = settings.setLive(value = value)

    suspend fun fetch(topic: String? = null, onTransfer: ((Transfer) -> Unit)? = null): Int {
        var received = 0
        var clipsFailure: Exception? = null
        var alertsFailure: Exception? = null
        val wantsClips = topic == null || clips.owns(topic)
        try {
            if (wantsClips) received += if (onTransfer == null) clips.fetch(topic = topic) else clips.fetch(topic = topic, onTransfer = onTransfer)
        } catch (error: Exception) {
            clipsFailure = error
        }
        try {
            if (topic == null || alerts.owns(topic)) received += alerts.fetch(topic = topic)
        } catch (error: Exception) {
            alertsFailure = error
        }
        clipsFailure?.let { throw it }
        if (!wantsClips || !clips.isPaired.value) alertsFailure?.let { throw it }
        return received
    }

    suspend fun resubscribe() {
        if (!settings.isInstant.value) return
        clips.subscribeAll(subscribe = true)
        alerts.subscribeAll(subscribe = true)
    }

    suspend fun setInstant(value: Boolean) {
        settings.setInstant(value = value)
        clips.subscribeAll(subscribe = value)
        alerts.subscribeAll(subscribe = value)
    }

    private companion object {
        private const val TAG = "SyncCoordinator"
    }
}
