package com.kopylovis.tossling.core.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

inline fun CoroutineScope.launchSafe(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    errorDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    coroutineName: String? = null,
    crossinline onError: suspend (Throwable) -> Unit,
    crossinline onAction: suspend CoroutineScope.() -> Unit,
): Job {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        launch(context = errorDispatcher) {
            onError.invoke(throwable)
        }
    }
    val block: suspend CoroutineScope.() -> Unit = {
        onAction()
    }
    var coroutineContext = exceptionHandler + dispatcher
    coroutineName?.let { name ->
        coroutineContext += CoroutineName(name = name)
    }
    return this.launch(context = coroutineContext, block = block)
}
