package com.kopylovis.tossling.sync.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

internal fun deviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
        ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

internal fun stableDeviceId(context: Context): String {
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)?.takeIf { it.isNotBlank() }
        ?: return UUID.randomUUID().toString().replace("-", "").take(16)
    return deviceIdFrom(androidId = androidId)
}

internal fun deviceIdFrom(androidId: String): String =
    MessageDigest.getInstance("SHA-256").digest("tossy-device:$androidId".toByteArray()).joinToString(separator = "") { "%02x".format(it) }.take(16)
