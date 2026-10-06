package com.kopylovis.tossling.navigation.delegates

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

abstract class Navigator<T : Any> {

    private val delegateRef = atomic<NavigationDelegate<T>?>(null)
    protected val delegate: NavigationDelegate<T>? by delegateRef
    private val pendingCallback = atomic<(() -> Unit)?>(null)

    fun setDelegate(delegate: NavigationDelegate<T>) {
        delegateRef.value = delegate
        pendingCallback.getAndSet(value = null)?.invoke()
    }

    fun pushNew(configuration: T, onComplete: (Boolean) -> Unit = {}) {
        whenReady { delegate?.pushNew(configuration, onComplete) }
    }

    fun replaceAll(vararg configuration: T, onComplete: () -> Unit = {}) {
        whenReady { delegate?.replaceAll(*configuration, onComplete = onComplete) }
    }

    fun replaceCurrent(configuration: T, onComplete: () -> Unit = {}) {
        whenReady { delegate?.replaceCurrent(configuration = configuration, onComplete = onComplete) }
    }

    fun pop(onComplete: (Boolean) -> Unit = {}) {
        whenReady { delegate?.pop(onComplete) }
    }

    fun popTo(index: Int, onComplete: (Boolean) -> Unit = {}) {
        whenReady { delegate?.popTo(index, onComplete) }
    }

    fun popToFirst(onComplete: (Boolean) -> Unit = {}) {
        whenReady { delegate?.popToFirst(onComplete = onComplete) }
    }

    fun bringToFront(configuration: T, onComplete: () -> Unit = {}) {
        whenReady { delegate?.bringToFront(configuration, onComplete) }
    }

    suspend fun pushNewSuspend(configuration: T) = suspendCancellableCoroutine { continuation ->
        whenReady {
            delegate?.pushNew(configuration) { continuation.resume(Unit) }
        }
    }

    suspend fun replaceAllSuspend(vararg configuration: T) =
        suspendCancellableCoroutine { continuation ->
            whenReady {
                delegate?.replaceAll(*configuration) { continuation.resume(Unit) }
            }
        }

    private fun whenReady(callback: () -> Unit) {
        delegateRef.value?.let {
            callback()
        } ?: run {
            pendingCallback.value = callback
        }
    }

    fun onDestroy() {
        delegateRef.value = null
    }
}
