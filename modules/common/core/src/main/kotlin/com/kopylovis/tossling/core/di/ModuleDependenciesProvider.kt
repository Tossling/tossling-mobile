package com.kopylovis.tossling.core.di

fun interface ModuleDependenciesProvider<out T> {
    fun getDependencies(): T
}
