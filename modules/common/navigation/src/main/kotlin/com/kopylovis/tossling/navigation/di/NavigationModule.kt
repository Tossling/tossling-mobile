package com.kopylovis.tossling.navigation.di

import com.kopylovis.tossling.navigation.GlobalNavigator
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val navigationModule = module {
    singleOf(constructor = ::GlobalNavigator)
}
