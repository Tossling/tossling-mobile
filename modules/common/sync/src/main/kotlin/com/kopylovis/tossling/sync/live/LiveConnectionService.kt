package com.kopylovis.tossling.sync.live

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kopylovis.tossling.protocol.Endpoint
import com.kopylovis.tossling.protocol.network.NtfyClient
import com.kopylovis.tossling.sync.alerts.AlertRepository
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncCoordinator
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import com.kopylovis.tossling.sync.work.FetchWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class LiveConnectionService : Service(), KoinComponent {

    private val clips: ClipRepository by inject()
    private val alerts: AlertRepository by inject()
    private val coordinator: SyncCoordinator by inject()
    private val client: NtfyClient by inject()
    private val notifications: SyncNotifications by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val network = MutableStateFlow(0)
    private var loop: Job? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notifications.createChannels()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        runCatching { ServiceCompat.startForeground(this, LIVE_NOTIFICATION, notifications.liveNotification(), type) }
            .onFailure { error ->
                Log.e(TAG, "could not start in the foreground", error)
                stopSelf()
                return START_NOT_STICKY
            }
        if (loop == null) {
            watchNetwork()
            loop = scope.launch { run() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        callback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun run() {
        combine(clips.pairings, alerts.projects, network) { _, _, generation -> targets() to generation }
            .distinctUntilChanged()
            .collectLatest { (targets, _) ->
                if (targets.isEmpty()) {
                    stopSelf()
                    return@collectLatest
                }
                coroutineScope {
                    targets.forEach { (endpoint, topics) -> launch { follow(endpoint = endpoint, topics = topics) } }
                }
            }
    }

    private fun targets(): Map<Endpoint, List<String>> = liveTargets(topics = clips.pairings.value.map { it.endpoint to it.inTopic } + alerts.liveTopics())

    private suspend fun follow(endpoint: Endpoint, topics: List<String>) {
        var pause = MIN_PAUSE_MS
        while (scope.isActive) {
            val started = SystemClock.elapsedRealtime()
            try {
                client.stream(
                    endpoint = endpoint,
                    topics = topics,
                    onOpen = { topics.forEach { topic -> deliver(topic = topic) } },
                    onEvent = { event -> if (event.event == MESSAGE_EVENT && event.topic.isNotEmpty()) deliver(topic = event.topic) },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "connection to ${endpoint.server} dropped: ${error.message}")
            }
            if (SystemClock.elapsedRealtime() - started > STABLE_MS) pause = MIN_PAUSE_MS
            delay(pause)
            pause = (pause * 2).coerceAtMost(MAX_PAUSE_MS)
        }
    }

    private fun deliver(topic: String) {
        scope.launch {
            runCatching { coordinator.fetch(topic = topic) }
                .onFailure { error ->
                    Log.w(TAG, "fetch of $topic failed, handing it to the worker", error)
                    FetchWorker.enqueue(context = applicationContext, topic = topic)
                }
        }
    }

    private fun watchNetwork() {
        val manager = getSystemService(ConnectivityManager::class.java)
        val watcher = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                this@LiveConnectionService.network.value += 1
            }

            override fun onLost(network: Network) {
                this@LiveConnectionService.network.value += 1
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(watcher) }
            .onSuccess { callback = watcher }
            .onFailure { Log.w(TAG, "no network callback", it) }
    }

    companion object {
        private const val TAG = "LiveConnection"
        private const val MESSAGE_EVENT = "message"
        private const val LIVE_NOTIFICATION = 7
        private const val MIN_PAUSE_MS = 1_000L
        private const val MAX_PAUSE_MS = 60_000L
        private const val STABLE_MS = 60_000L

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LiveConnectionService::class.java)) }
                .onFailure { Log.w(TAG, "could not start the live connection", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveConnectionService::class.java))
        }
    }
}

internal fun liveTargets(topics: List<Pair<Endpoint, String>>): Map<Endpoint, List<String>> =
    topics.filter { it.second.isNotBlank() }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, list) -> list.distinct().sorted() }
