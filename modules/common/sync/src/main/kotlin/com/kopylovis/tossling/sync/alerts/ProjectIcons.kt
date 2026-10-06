package com.kopylovis.tossling.sync.alerts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppChoice(
    val packageName: String,
    val label: String,
)

class ProjectIcons internal constructor(private val context: Context) {

    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    suspend fun apps(): List<AppChoice> = withContext(Dispatchers.IO) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcher, 0)
            .map { AppChoice(packageName = it.activityInfo.packageName, label = it.loadLabel(context.packageManager).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun match(name: String): AppChoice? {
        val wanted = name.trim().lowercase()
        if (wanted.isEmpty()) return null
        return apps().firstOrNull { it.label.lowercase() == wanted || it.packageName.substringAfterLast('.').lowercase() == wanted }
    }

    suspend fun saveApp(packageName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = drawable.toBitmap(width = ICON_PX, height = ICON_PX)
            write(name = "app-$packageName.png", bitmap = bitmap)
        }.getOrNull()
    }

    suspend fun saveUrl(url: String): String? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        val file = File(dir, "url-${hash(url)}.png")
        if (file.exists()) return@withContext file.absolutePath
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val bytes = connection.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
                if (bytes.size > MAX_BYTES) return@runCatching null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                write(name = file.name, bitmap = Bitmap.createScaledBitmap(bitmap, ICON_PX, ICON_PX, true))
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    suspend fun saveImage(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply { inSampleSize = Integer.highestOneBit((longest / ICON_PX).coerceAtLeast(1)) }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
            val side = minOf(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            write(name = "custom-${System.currentTimeMillis()}.png", bitmap = Bitmap.createScaledBitmap(square, ICON_PX, ICON_PX, true))
        }.getOrNull()
    }

    fun forget(path: String) {
        File(path).takeIf { it.parentFile == dir }?.delete()
    }

    private fun write(name: String, bitmap: Bitmap): String {
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        return file.absolutePath
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(HASH_BYTES).joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        private const val DIR = "project-icons"
        private const val ICON_PX = 192
        private const val TIMEOUT_MS = 10_000
        private const val MAX_BYTES = 1_000_000
        private const val PNG_QUALITY = 100
        private const val HASH_BYTES = 12
    }
}
