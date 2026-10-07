package com.kopylovis.tossling.sync.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kopylovis.tossling.core.presentation.theme.ProjectColors
import com.kopylovis.tossling.protocol.alerts.Alert
import com.kopylovis.tossling.protocol.alerts.Project
import com.kopylovis.tossling.sync.R
import com.kopylovis.tossling.sync.entry.AlertActionReceiver
import com.kopylovis.tossling.sync.entry.AlertLinkActivity
import com.kopylovis.tossling.sync.entry.TosslingIntents

internal class AlertNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        manager.createNotificationChannelGroup(
            NotificationChannelGroupCompat.Builder(GROUP).setName(context.getString(R.string.sync_channel_projects)).build(),
        )
        manager.createNotificationChannelsCompat(
            listOf(
                channel(id = CHANNEL_URGENT, importance = NotificationManagerCompat.IMPORTANCE_HIGH, name = R.string.sync_channel_urgent),
                channel(id = CHANNEL_IMPORTANT, importance = NotificationManagerCompat.IMPORTANCE_HIGH, name = R.string.sync_channel_important),
                channel(id = CHANNEL_NORMAL, importance = NotificationManagerCompat.IMPORTANCE_DEFAULT, name = R.string.sync_channel_normal),
                channel(id = CHANNEL_QUIET, importance = NotificationManagerCompat.IMPORTANCE_LOW, name = R.string.sync_channel_quiet),
            ),
        )
        manager.notificationChannelsCompat
            .filter { it.id.startsWith(LEGACY_PREFIX) }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    fun show(project: Project, alert: Alert, isQuietTime: Boolean) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val channel = when {
            alert.isUrgent -> CHANNEL_URGENT
            project.isMuted -> CHANNEL_QUIET
            alert.priority == IMPORTANT_PRIORITY -> CHANNEL_IMPORTANT
            alert.isQuiet -> CHANNEL_QUIET
            else -> CHANNEL_NORMAL
        }
        val title = alert.titleFor(project = project.name).ifBlank { project.name }
        val text = plain(alert.message)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_tossling)
            .setLargeIcon(project.iconFile.takeIf { it.isNotEmpty() }?.let(BitmapFactory::decodeFile) ?: avatar(project = project))
            .setSubText(project.name)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(alert.time)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setSilent(isQuietTime && !alert.isUrgent)
            .setGroup(project.topic)
            .setPriority(if (channel == CHANNEL_URGENT || channel == CHANNEL_IMPORTANT) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openDetail(alert = alert))
        alert.click?.let { url ->
            builder.addAction(0, context.getString(R.string.sync_alert_open), openLink(alert = alert, url = url))
        }
        builder.addAction(0, context.getString(R.string.sync_alert_read), markRead(alert = alert))
        manager.notify(TAG, alert.id.hashCode(), builder.build())
    }

    fun cancel(alertId: String) = manager.cancel(TAG, alertId.hashCode())

    fun cancelProject(topic: String) {
        manager.activeNotifications.filter { it.tag == TAG && it.notification.group == topic }.forEach { manager.cancel(TAG, it.id) }
    }

    fun cancelAll() {
        manager.activeNotifications.filter { it.tag == TAG }.forEach { manager.cancel(TAG, it.id) }
    }

    private fun channel(id: String, importance: Int, name: Int): NotificationChannelCompat =
        NotificationChannelCompat.Builder(id, importance)
            .setName(context.getString(name))
            .setGroup(GROUP)
            .build()

    private fun openDetail(alert: Alert): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(TosslingIntents.EXTRA_ALERT, alert.id)
        } ?: return null
        return PendingIntent.getActivity(context, alert.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openLink(alert: Alert, url: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            AlertLinkActivity.intent(context = context, alertId = alert.id, url = url),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun markRead(alert: Alert): PendingIntent {
        val intent = Intent(context, AlertActionReceiver::class.java)
            .setAction(TosslingIntents.ACTION_MARK_READ)
            .putExtra(TosslingIntents.EXTRA_ALERT, alert.id)
        return PendingIntent.getBroadcast(context, alert.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun avatar(project: Project): Bitmap {
        val size = (AVATAR_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ProjectColors[project.colorIndex].toArgb() }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, fill)
        val initials = project.initials
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * if (initials.length > 1) 0.36f else 0.44f
        }
        canvas.drawText(initials, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2f, text)
        return bitmap
    }

    private fun plain(text: String): String =
        text.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").replace(Regex("[*_`#>]+"), "").trim()

    private companion object {
        private const val TAG = "alert"
        private const val GROUP = "projects"
        private const val CHANNEL_URGENT = "projects.urgent"
        private const val CHANNEL_IMPORTANT = "projects.important"
        private const val CHANNEL_NORMAL = "projects.normal"
        private const val CHANNEL_QUIET = "projects.quiet"
        private const val LEGACY_PREFIX = "alerts."
        private const val IMPORTANT_PRIORITY = 4
        private const val AVATAR_DP = 48
    }
}
