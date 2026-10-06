package com.kopylovis.tossling.sync.entry

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.service.quicksettings.TileService
import android.view.View
import android.view.WindowManager
import org.koin.android.ext.android.inject

internal class ClipTileService : TileService() {

    private val sender: ClipSender by inject()

    override fun onClick() {
        if (isLocked) unlockAndRun(::grab) else grab()
    }

    private fun grab() {
        showDialog(GrabDialog(context = this) { sender.sendClipboard() })
    }

    private class GrabDialog(context: Context, private val onFocus: () -> Unit) : Dialog(context) {

        private var handled = false

        init {
            setContentView(View(context))
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setWindowAnimations(0)
                setLayout(1, 1)
            }
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            if (!hasFocus || handled) return
            handled = true
            onFocus()
            window?.decorView?.post { dismiss() }
        }
    }
}
