package com.kopylovis.tossling.core.decompose.base

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.kopylovis.tossling.core.decompose.asValueUtil
import com.kopylovis.tossling.core.decompose.coroutineScope
import com.kopylovis.tossling.core.extensions.launchSafe
import com.kopylovis.tossling.core.presentation.messages.AppMessenger
import com.kopylovis.tossling.core.presentation.tr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

abstract class BaseComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext, KoinComponent {

    protected val scope: CoroutineScope by lazy {
        coroutineScope(context = SupervisorJob() + Dispatchers.Main.immediate)
    }

    protected val messenger: AppMessenger by inject()

    protected fun <T, M> StateFlow<T>.map(
        coroutineScope: CoroutineScope = scope,
        mapper: (value: T) -> M,
    ): StateFlow<M> =
        map(mapper).stateIn(coroutineScope, SharingStarted.Eagerly, mapper(value))

    protected fun <T1, T2, R> combine(
        flow1: StateFlow<T1>,
        flow2: StateFlow<T2>,
        coroutineScope: CoroutineScope = scope,
        transform: (T1, T2) -> R,
    ): StateFlow<R> =
        kotlinx.coroutines.flow.combine(flow1, flow2, transform)
            .stateIn(coroutineScope, SharingStarted.Eagerly, transform(flow1.value, flow2.value))

    protected fun <T1, T2, T3, R> combine(
        flow1: StateFlow<T1>,
        flow2: StateFlow<T2>,
        flow3: StateFlow<T3>,
        coroutineScope: CoroutineScope = scope,
        transform: (T1, T2, T3) -> R,
    ): StateFlow<R> =
        kotlinx.coroutines.flow.combine(flow1, flow2, flow3, transform)
            .stateIn(
                coroutineScope,
                SharingStarted.Eagerly,
                transform(flow1.value, flow2.value, flow3.value),
            )

    protected fun <T1, T2, T3, T4, R> combine(
        flow1: StateFlow<T1>,
        flow2: StateFlow<T2>,
        flow3: StateFlow<T3>,
        flow4: StateFlow<T4>,
        coroutineScope: CoroutineScope = scope,
        transform: (T1, T2, T3, T4) -> R,
    ): StateFlow<R> =
        kotlinx.coroutines.flow.combine(flow1, flow2, flow3, flow4, transform)
            .stateIn(
                coroutineScope,
                SharingStarted.Eagerly,
                transform(flow1.value, flow2.value, flow3.value, flow4.value),
            )

    protected fun <T : Any> StateFlow<T>.asValue(
        lifecycle: Lifecycle = this@BaseComponent.lifecycle,
        context: CoroutineContext = Dispatchers.Main.immediate,
    ): Value<T> = asValueUtil(lifecycle, context)

    protected fun launchCoroutine(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        errorDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
        onError: (suspend (Throwable) -> Unit)? = null,
        onAction: suspend CoroutineScope.() -> Unit,
    ) = scope.launchSafe(
        dispatcher = dispatcher,
        errorDispatcher = errorDispatcher,
        onError = { error ->
            onError?.invoke(error) ?: messenger.error(error.message ?: tr("Something went wrong", "Что-то пошло не так"))
        },
        onAction = onAction,
    )
}
