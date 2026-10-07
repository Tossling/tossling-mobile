package com.kopylovis.tossling.sync.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.util.Patterns
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.kopylovis.tossling.protocol.ClipItem
import com.kopylovis.tossling.protocol.ClipKind
import com.kopylovis.tossling.protocol.Transfer
import com.kopylovis.tossling.sync.R

internal class SyncNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_RECEIVED, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.sync_channel_received))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_WORK, NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName(context.getString(R.string.sync_channel_work))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ERRORS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.sync_channel_errors))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ROOM, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.sync_channel_room))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_TRANSFER, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.sync_channel_transfer))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_LIVE, NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName(context.getString(R.string.sync_channel_live))
                    .setShowBadge(false)
                    .build(),
            ),
        )
    }

    fun showReceived(item: ClipItem) {
        val from = item.device.ifEmpty { "Mac" }
        val builder = base(channel = CHANNEL_RECEIVED)
            .setContentTitle(context.getString(R.string.sync_received_title, from))
            .setSilent(true)
            .setTimeoutAfter(RECEIVED_TIMEOUT_MS)
        when (item.kind) {
            ClipKind.TEXT -> {
                builder.setContentText(item.text.orEmpty().lineSequence().firstOrNull().orEmpty())
                linkOf(text = item.text.orEmpty())?.let { link ->
                    val intent = Intent(Intent.ACTION_VIEW, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val open = PendingIntent.getActivity(context, link.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    builder.addAction(0, context.getString(R.string.sync_open_link), open).setTimeoutAfter(FILE_TIMEOUT_MS)
                }
            }
            ClipKind.FILE -> {
                builder.setContentTitle(context.getString(R.string.sync_received_file_title, from))
                    .setContentText(context.getString(R.string.sync_received_file, item.name.orEmpty()))
                    .setTimeoutAfter(FILE_TIMEOUT_MS)
                item.file?.let(Uri::parse)?.let { uri -> builder.setContentIntent(openFile(uri = uri, mime = item.mime)) }
            }

            ClipKind.IMAGE -> {
                builder.setContentText(context.getString(R.string.sync_received_image))
                item.file?.let { path -> decodePreview(path = path) }?.let { bitmap ->
                    builder.setLargeIcon(bitmap).setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap).bigLargeIcon(null as Bitmap?))
                }
            }
        }
        post(id = ID_RECEIVED, notification = builder.build())
    }

    fun showJoined(name: String) {
        val text = context.getString(R.string.sync_joined_text)
        val notification = base(channel = CHANNEL_ROOM)
            .setContentTitle(context.getString(R.string.sync_joined_title, name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSilent(true)
            .build()
        post(id = ID_JOINED + name.hashCode(), notification = notification)
    }

    fun showRemoved(by: String) {
        val notification = base(channel = CHANNEL_ROOM)
            .setContentTitle(context.getString(R.string.sync_removed_title))
            .setContentText(context.getString(R.string.sync_removed_text, by.ifEmpty { "?" }))
            .build()
        post(id = ID_REMOVED, notification = notification)
    }

    fun showSendFailed(reason: String) {
        val notification = base(channel = CHANNEL_ERRORS)
            .setContentTitle(context.getString(R.string.sync_send_failed))
            .setContentText(reason)
            .build()
        post(id = ID_ERROR, notification = notification)
    }

    fun showMissed(name: String, device: String) {
        val text = context.getString(R.string.sync_missed_text, device.ifEmpty { "Mac" })
        val notification = base(channel = CHANNEL_ERRORS)
            .setContentTitle(context.getString(R.string.sync_missed_title, name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
        post(id = ID_MISSED + name.hashCode(), notification = notification)
    }

    fun liveNotification(): Notification =
        base(channel = CHANNEL_LIVE)
            .setContentTitle(context.getString(R.string.sync_live_title))
            .setContentText(context.getString(R.string.sync_live_text))
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .build()

    fun foregroundInfo(): ForegroundInfo {
        val notification = base(channel = CHANNEL_WORK)
            .setContentTitle(context.getString(R.string.sync_working))
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_WORK, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_WORK, notification)
        }
    }

    fun showTransfer(transfer: Transfer, cancel: PendingIntent? = null) {
        post(id = ID_TRANSFER, notification = transferNotification(transfer = transfer, cancel = cancel))
    }

    fun transferInfo(transfer: Transfer, cancel: PendingIntent?): ForegroundInfo {
        val notification = transferNotification(transfer = transfer, cancel = cancel)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_TRANSFER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_TRANSFER, notification)
        }
    }

    fun clearTransfer() {
        manager.cancel(ID_TRANSFER)
    }

    fun showSent(name: String, device: String) {
        val notification = base(channel = CHANNEL_TRANSFER)
            .setContentTitle(context.getString(R.string.sync_sent_title, name))
            .setContentText(if (device.isEmpty()) context.getString(R.string.sync_sent_all) else context.getString(R.string.sync_sent_to, device))
            .setSilent(true)
            .setTimeoutAfter(SENT_TIMEOUT_MS)
            .build()
        post(id = ID_SENT, notification = notification)
    }

    private fun transferNotification(transfer: Transfer, cancel: PendingIntent?): Notification {
        val title = when {
            transfer.incoming -> context.getString(R.string.sync_transfer_receive, transfer.name, transfer.device.ifEmpty { "?" })
            transfer.device.isEmpty() -> context.getString(R.string.sync_transfer_send, transfer.name)
            else -> context.getString(R.string.sync_transfer_send_to, transfer.name, transfer.device)
        }
        val text = context.getString(
            R.string.sync_transfer_progress,
            transfer.percent,
            Formatter.formatShortFileSize(context, transfer.done),
            Formatter.formatShortFileSize(context, transfer.total),
        )
        val builder = base(channel = CHANNEL_TRANSFER)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(PERCENT, transfer.percent, transfer.total <= 0)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setGroup(GROUP_TRANSFER)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        cancel?.let { builder.addAction(0, context.getString(R.string.sync_transfer_cancel), it) }
        return builder.build()
    }

    private fun linkOf(text: String): Uri? {
        val clean = text.trim()
        if (clean.any { it.isWhitespace() } || !Patterns.WEB_URL.matcher(clean).matches()) return null
        val uri = Uri.parse(if (clean.contains("://")) clean else "https://$clean")
        return uri.takeIf { it.scheme == "https" || it.scheme == "http" }
    }

    private fun base(channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_tossling)
            .setAutoCancel(true)
            .setContentIntent(openApp())

    private fun openFile(uri: Uri, mime: String?): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, uri.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openApp(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun post(id: Int, notification: Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.notify(id, notification)
    }

    private fun decodePreview(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > PREVIEW_SIZE * 2 || bounds.outHeight / sample > PREVIEW_SIZE * 2) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        private const val CHANNEL_RECEIVED = "received"
        private const val CHANNEL_WORK = "work"
        private const val CHANNEL_ERRORS = "errors"
        private const val CHANNEL_ROOM = "room"
        private const val CHANNEL_TRANSFER = "transfer"
        private const val CHANNEL_LIVE = "live"
        private const val GROUP_TRANSFER = "transfer"
        private const val ID_RECEIVED = 1
        private const val ID_WORK = 2
        private const val ID_ERROR = 3
        private const val ID_REMOVED = 4
        private const val ID_TRANSFER = 5
        private const val ID_SENT = 6
        private const val SENT_TIMEOUT_MS = 60_000L
        private const val PERCENT = 100
        private const val ID_JOINED = 100
        private const val ID_MISSED = 1000
        private const val RECEIVED_TIMEOUT_MS = 15_000L
        private const val FILE_TIMEOUT_MS = 10 * 60_000L
        private const val PREVIEW_SIZE = 512
    }
}
