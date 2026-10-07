package com.kopylovis.tossling.sync.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.kopylovis.tossling.protocol.deviceIdFrom
import java.util.UUID

internal fun deviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
        ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

internal fun stableDeviceId(context: Context): String {
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)?.takeIf { it.isNotBlank() }
        ?: return UUID.randomUUID().toString().replace("-", "").take(16)
    return deviceIdFrom(androidId = androidId)
}
