package com.kopylovis.tossling.sync.entry

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import android.widget.RemoteViews
import com.kopylovis.tossling.protocol.ClipItem
import com.kopylovis.tossling.protocol.ClipKind
import com.kopylovis.tossling.sync.R
import com.kopylovis.tossling.sync.data.ClipRepository
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

internal class ClipWidget : AppWidgetProvider(), KoinComponent {

    private val repository: ClipRepository by inject()

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context = context, manager = manager, ids = ids, last = repository.history.value.firstOrNull())
    }

    companion object {

        private const val THUMB_SIZE = 192

        fun refresh(context: Context, last: ClipItem?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClipWidget::class.java))
            if (ids.isNotEmpty()) render(context = context, manager = manager, ids = ids, last = last)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, last: ClipItem?) {
            val views = RemoteViews(context.packageName, R.layout.sync_widget)
            views.setTextViewText(R.id.sync_widget_meta, last?.let { meta(context = context, item = it) } ?: context.getString(R.string.sync_widget_label))
            views.setTextViewText(R.id.sync_widget_text, last?.let { body(context = context, item = it) } ?: context.getString(R.string.sync_widget_empty))
            val thumb = last?.let(::thumbnail)
            thumb?.let { views.setImageViewBitmap(R.id.sync_widget_thumb, it) }
            views.setViewVisibility(R.id.sync_widget_thumb, if (thumb != null) View.VISIBLE else View.GONE)
            val grab = Intent(context, ClipGrabActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            views.setOnClickPendingIntent(R.id.sync_widget_send, PendingIntent.getActivity(context, 0, grab, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { open ->
                views.setOnClickPendingIntent(R.id.sync_widget_last, PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun thumbnail(item: ClipItem): Bitmap? {
            val isImage = item.kind == ClipKind.IMAGE || item.mime?.startsWith("image/") == true
            val path = item.file?.takeIf { isImage && File(it).isFile } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIZE) sample *= 2
            return runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull()
        }

        private fun meta(context: Context, item: ClipItem): String {
            val who = when {
                item.incoming -> context.getString(R.string.sync_widget_from, item.device.ifEmpty { "Mac" })
                item.device.isEmpty() -> context.getString(R.string.sync_widget_sent)
                else -> context.getString(R.string.sync_widget_to, item.device)
            }
            val time = DateUtils.formatDateTime(context, item.time, DateUtils.FORMAT_SHOW_TIME)
            return "$who · $time"
        }

        private fun body(context: Context, item: ClipItem): String = when (item.kind) {
            ClipKind.TEXT -> item.text.orEmpty().trim()
            ClipKind.IMAGE -> listOfNotNull(
                context.getString(R.string.sync_widget_image),
                item.size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
            ).joinToString(separator = " · ")
            ClipKind.FILE -> item.name ?: "file"
        }
    }
}

internal class ClipGrabActivity : Activity() {

    private val sender: ClipSender by inject()
    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true
        sender.sendClipboard()
        finish()
    }
}
