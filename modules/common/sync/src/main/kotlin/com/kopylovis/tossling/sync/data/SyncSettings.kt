package com.kopylovis.tossling.sync.data

import android.content.Context
import androidx.core.content.edit
import com.kopylovis.tossling.protocol.crypto.DeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64
import java.util.Calendar

class SyncSettings internal constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _isPaused = MutableStateFlow(prefs.getBoolean(KEY_PAUSED, false))
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _sendsImages = MutableStateFlow(prefs.getBoolean(KEY_IMAGES, true))
    val sendsImages: StateFlow<Boolean> = _sendsImages.asStateFlow()

    private val _isInstant = MutableStateFlow(prefs.getBoolean(KEY_INSTANT, true))
    val isInstant: StateFlow<Boolean> = _isInstant.asStateFlow()

    private var liveChoice: Boolean? = if (prefs.contains(KEY_LIVE)) prefs.getBoolean(KEY_LIVE, false) else null
    private var hasPush = true

    private val _serverPush = MutableStateFlow(if (prefs.contains(KEY_SERVER_PUSH)) prefs.getBoolean(KEY_SERVER_PUSH, true) else null)
    val serverPush: StateFlow<Boolean?> = _serverPush.asStateFlow()

    private val _isLive = MutableStateFlow(liveDecision(choice = liveChoice, hasPush = hasPush, serverPush = _serverPush.value))
    val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    @Volatile var deviceId: String = prefs.getString(KEY_DEVICE_ID, null) ?: stableDeviceId(context = context).also { id ->
        prefs.edit { putString(KEY_DEVICE_ID, id) }
    }
        private set

    @Volatile internal var identity: DeviceIdentity = prefs.getString(KEY_IDENTITY, null)
        ?.let { runCatching { DeviceIdentity(privateKey = Base64.getDecoder().decode(it)) }.getOrNull() }
        ?: DeviceIdentity.generate().also { identity ->
            prefs.edit { putString(KEY_IDENTITY, Base64.getEncoder().encodeToString(identity.privateKey)) }
        }
        private set

    private val _deviceName = MutableStateFlow(prefs.getString(KEY_DEVICE_NAME, null) ?: deviceName(context = context))
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _isQuietHours = MutableStateFlow(prefs.getBoolean(KEY_QUIET_HOURS, false))
    val isQuietHours: StateFlow<Boolean> = _isQuietHours.asStateFlow()

    private val _isTileHintHidden = MutableStateFlow(prefs.getBoolean(KEY_TILE_HINT, false))
    val isTileHintHidden: StateFlow<Boolean> = _isTileHintHidden.asStateFlow()

    fun setPaused(value: Boolean) = save(flow = _isPaused, key = KEY_PAUSED, value = value)

    fun setSendsImages(value: Boolean) = save(flow = _sendsImages, key = KEY_IMAGES, value = value)

    internal fun setInstant(value: Boolean) = save(flow = _isInstant, key = KEY_INSTANT, value = value)

    internal fun setLive(value: Boolean) {
        prefs.edit { putBoolean(KEY_LIVE, value) }
        liveChoice = value
        refreshLive()
    }

    internal fun setHasPush(value: Boolean) {
        hasPush = value
        refreshLive()
    }

    internal fun setServerPush(value: Boolean?) {
        prefs.edit { if (value == null) remove(KEY_SERVER_PUSH) else putBoolean(KEY_SERVER_PUSH, value) }
        _serverPush.value = value
        refreshLive()
    }

    private fun refreshLive() {
        _isLive.value = liveDecision(choice = liveChoice, hasPush = hasPush, serverPush = _serverPush.value)
    }

    fun isQuietTime(now: Calendar = Calendar.getInstance()): Boolean {
        if (!_isQuietHours.value) return false
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour >= QUIET_FROM || hour < QUIET_UNTIL
    }

    fun setQuietHours(value: Boolean) = save(flow = _isQuietHours, key = KEY_QUIET_HOURS, value = value)

    internal fun restoreDevice(id: String, identity: DeviceIdentity, name: String) {
        prefs.edit {
            putString(KEY_DEVICE_ID, id)
            putString(KEY_IDENTITY, Base64.getEncoder().encodeToString(identity.privateKey))
            if (name.isNotBlank()) putString(KEY_DEVICE_NAME, name)
        }
        deviceId = id
        this.identity = identity
        if (name.isNotBlank()) _deviceName.value = name
    }

    internal fun setDeviceName(value: String) {
        prefs.edit { putString(KEY_DEVICE_NAME, value) }
        _deviceName.value = value
    }

    fun hideTileHint() = save(flow = _isTileHintHidden, key = KEY_TILE_HINT, value = true)

    private fun save(flow: MutableStateFlow<Boolean>, key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
        flow.value = value
    }

    private companion object {
        private const val PREFS = "tossy_settings"
        private const val KEY_PAUSED = "paused"
        private const val KEY_IMAGES = "images"
        private const val KEY_INSTANT = "instant"
        private const val KEY_LIVE = "live_choice"
        private const val KEY_SERVER_PUSH = "server_push"
        private const val KEY_TILE_HINT = "tile_hint_hidden"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_IDENTITY = "identity"
        private const val KEY_QUIET_HOURS = "quiet_hours"
        private const val QUIET_FROM = 23
        private const val QUIET_UNTIL = 8
    }
}

internal fun liveDecision(choice: Boolean?, hasPush: Boolean, serverPush: Boolean?): Boolean =
    choice ?: (!hasPush || serverPush == false)
