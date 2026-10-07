package com.kopylovis.tossling

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.arkivanov.decompose.defaultComponentContext
import com.kopylovis.tossling.core.presentation.modifiers.dismissKeyboardOnTap
import com.kopylovis.tossling.core.presentation.theme.TosslingTheme
import com.kopylovis.tossling.presentation.app.AppComponentImpl
import com.kopylovis.tossling.presentation.app.AppContent
import com.kopylovis.tossling.sync.entry.TosslingIntents
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

class MainActivity : ComponentActivity() {

    private var appComponent: AppComponentImpl? = null

    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FileKit.init(activity = this)

        val appComponent = AppComponentImpl(componentContext = defaultComponentContext())
        this.appComponent = appComponent
        if (savedInstanceState == null) openAlert(intent = intent)

        setContent {
            TosslingTheme {
                AppContent(component = appComponent, modifier = Modifier.dismissKeyboardOnTap())
            }
        }
        askForNotifications()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openAlert(intent = intent)
    }

    private fun openAlert(intent: Intent?) {
        val id = intent?.getStringExtra(TosslingIntents.EXTRA_ALERT) ?: return
        intent.removeExtra(TosslingIntents.EXTRA_ALERT)
        appComponent?.onAlertOpened(id = id)
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
