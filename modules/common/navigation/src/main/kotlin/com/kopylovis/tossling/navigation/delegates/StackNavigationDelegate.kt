package com.kopylovis.tossling.navigation.delegates

import android.util.Log
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.popToFirst
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.kopylovis.tossling.core.extensions.launchSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StackNavigationDelegate<T : Any>(
    private val navigation: StackNavigation<T>,
) : NavigationDelegate<T> {

    private companion object {
        private const val TAG = "StackNavigationDelegate"
    }

    private val scope = CoroutineScope(context = Dispatchers.Main.immediate + SupervisorJob())

    override fun pushNew(configuration: T, onComplete: (Boolean) -> Unit) {
        launchCoroutine { navigation.pushNew(configuration = configuration, onComplete = onComplete) }
    }

    override fun pop(onComplete: (Boolean) -> Unit) {
        launchCoroutine { navigation.pop(onComplete = onComplete) }
    }

    override fun popTo(index: Int, onComplete: (Boolean) -> Unit) {
        launchCoroutine { navigation.popTo(index = index, onComplete = onComplete) }
    }

    override fun popToFirst(onComplete: (Boolean) -> Unit) {
        launchCoroutine { navigation.popToFirst(onComplete = onComplete) }
    }

    override fun bringToFront(configuration: T, onComplete: () -> Unit) {
        launchCoroutine { navigation.bringToFront(configuration = configuration, onComplete = onComplete) }
    }

    override fun replaceAll(vararg configurations: T, onComplete: () -> Unit) {
        launchCoroutine { navigation.replaceAll(*configurations, onComplete = onComplete) }
    }

    override fun replaceCurrent(configuration: T, onComplete: () -> Unit) {
        launchCoroutine { navigation.replaceCurrent(configuration = configuration, onComplete = onComplete) }
    }

    private fun launchCoroutine(onAction: suspend CoroutineScope.() -> Unit) {
        scope.launchSafe(
            dispatcher = Dispatchers.Main.immediate,
            errorDispatcher = Dispatchers.Main.immediate,
            onError = { error -> Log.e(TAG, "Navigation failed: ${error.message}", error) },
            onAction = onAction,
        )
    }
}

fun <T : Any> StackNavigation<T>.toDelegate() = StackNavigationDelegate(navigation = this)
