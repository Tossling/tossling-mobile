package com.kopylovis.tossling.core.di

import com.kopylovis.tossling.core.presentation.messages.AppMessenger
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val coreModule = module {
    singleOf(constructor = ::AppMessenger)
}
