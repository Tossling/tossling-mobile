package com.kopylovis.tossling.navigation.delegates

interface NavigationDelegate<T : Any> {

    fun pushNew(configuration: T, onComplete: (Boolean) -> Unit = {})

    fun pop(onComplete: (Boolean) -> Unit = {})

    fun popTo(index: Int, onComplete: (Boolean) -> Unit = {})

    fun popToFirst(onComplete: (Boolean) -> Unit = {})

    fun bringToFront(configuration: T, onComplete: () -> Unit = {})

    fun replaceAll(vararg configurations: T, onComplete: () -> Unit = {})

    fun replaceCurrent(configuration: T, onComplete: () -> Unit = {})
}
