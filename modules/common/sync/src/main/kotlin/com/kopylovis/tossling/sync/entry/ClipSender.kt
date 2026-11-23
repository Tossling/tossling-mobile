package com.kopylovis.tossling.sync.entry

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import com.kopylovis.tossling.protocol.ClipKind
import com.kopylovis.tossling.protocol.Transfer
import com.kopylovis.tossling.sync.R
import com.kopylovis.tossling.sync.clipboard.ClipboardBridge
import com.kopylovis.tossling.sync.clipboard.Outgoing
import com.kopylovis.tossling.sync.clipboard.TooLargeException
import com.kopylovis.tossling.sync.data.ClipRepository
import com.kopylovis.tossling.sync.data.SyncSettings
import com.kopylovis.tossling.sync.notifications.SyncNotifications
import com.kopylovis.tossling.sync.work.PausedSend
import com.kopylovis.tossling.sync.work.PausedSends
import com.kopylovis.tossling.sync.work.SendControl
import com.kopylovis.tossling.sync.work.SendWorker
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

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
    private val control: SendControl,
    private val pausedSends: PausedSends,
) {

    /** Files the user paused, oldest first. */
    val paused: StateFlow<List<PausedSend>> = pausedSends.items

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

    /** Stops the upload with this work id, or the current one, and keeps the file for Continue. */
    fun pauseSending(work: UUID? = null) {
        control.request(id = work ?: control.active, value = SendControl.Request.PAUSE)
    }

    /** Drops the upload with this work id, or the current one. The files queued after it still go. */
    fun cancelSending(work: UUID? = null) {
        if (control.request(id = work ?: control.active, value = SendControl.Request.CANCEL)) return
        // Not uploading this second (waiting for the network or a retry): WorkManager drops it and the files queued after it.
        notifications.clearTransfer()
        if (work != null) SendWorker.cancel(context = context, id = work) else SendWorker.cancel(context = context)
    }

    /** Sends a paused file again, from the first byte. */
    fun resume(id: String) {
        val paused = pausedSends.take(id = id) ?: return
        notifications.clearPaused(id = id)
        val outgoing = paused.outgoing()
        if (!outgoing.file.exists()) {
            toast(R.string.sync_paused_gone)
            return
        }
        enqueue(outgoing = outgoing, targets = paused.targets?.toSet())
    }

    fun discard(id: String) {
        notifications.clearPaused(id = id)
        pausedSends.take(id = id)?.let { File(it.path).delete() }
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
            Icon.createWithResource(context, R.drawable.ic_stat_tossling),
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
