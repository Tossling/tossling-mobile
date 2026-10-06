package com.kopylovis.tossling.sync.entry

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import com.kopylovis.tossling.sync.R
import com.kopylovis.tossling.sync.clipboard.ClipboardBridge
import com.kopylovis.tossling.sync.clipboard.Outgoing
import com.kopylovis.tossling.sync.clipboard.TooLargeException
import com.kopylovis.tossling.sync.data.ClipKind
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncSettings
import com.kopylovis.tossling.sync.data.Transfer
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import com.kopylovis.tossling.sync.work.SendWorker

sealed interface Prepared {
    data class Ready(val outgoing: Outgoing) : Prepared
    data class Refused(val message: Int) : Prepared
}

class ClipSender internal constructor(
    private val context: Context,
    private val bridge: ClipboardBridge,
    private val repository: ClipRepository,
    private val settings: SyncSettings,
    private val notifications: SyncNotifications,
) {

    fun sendClipboard(): Boolean = send { bridge.readClipboard() }

    fun send(capture: () -> Outgoing?): Boolean =
        when (val result = prepare(capture = capture)) {
            is Prepared.Ready -> {
                enqueue(outgoing = result.outgoing)
                if (result.outgoing.kind == ClipKind.FILE) {
                    toast(text = context.getString(R.string.sync_sending_file, result.outgoing.name ?: "file"))
                } else {
                    toast(R.string.sync_sending)
                }
                true
            }

            is Prepared.Refused -> {
                toast(result.message)
                false
            }
        }

    fun enqueue(outgoing: Outgoing, targets: Set<String>? = null) {
        if (outgoing.kind == ClipKind.FILE) {
            notifications.showTransfer(transfer = Transfer(name = outgoing.name ?: "file", incoming = false, device = "", done = 0, total = outgoing.file.length()))
        }
        SendWorker.enqueue(context = context, outgoing = outgoing, targets = targets)
    }

    fun cancelSending() {
        SendWorker.cancel(context = context)
    }

    fun prepareClipboard(): Prepared = prepare { bridge.readClipboard() }

    fun prepare(capture: () -> Outgoing?): Prepared {
        if (!repository.isPaired.value) return Prepared.Refused(R.string.sync_not_paired)
        if (settings.isPaused.value) return Prepared.Refused(R.string.sync_paused)
        val outgoing = try {
            capture()
        } catch (_: TooLargeException) {
            return Prepared.Refused(R.string.sync_too_large)
        } catch (_: Exception) {
            return Prepared.Refused(R.string.sync_unreadable)
        } ?: return Prepared.Refused(R.string.sync_clipboard_empty)
        if (outgoing.kind == ClipKind.IMAGE && !settings.sendsImages.value) {
            outgoing.file.delete()
            return Prepared.Refused(R.string.sync_images_off)
        }
        return Prepared.Ready(outgoing = outgoing)
    }

    fun text(id: Int): String = context.getString(id)

    val canRequestTile: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun requestTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(context, ClipTileService::class.java),
            context.getString(R.string.sync_tile_label),
            Icon.createWithResource(context, R.drawable.ic_stat_tossy),
            context.mainExecutor,
        ) { }
    }

    private fun toast(text: Int) {
        toast(text = context.getString(text))
    }

    private fun toast(text: String) {
        context.mainExecutor.execute { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
