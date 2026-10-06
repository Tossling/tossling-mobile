package com.kopylovis.tossling.core.presentation

import java.util.Locale

fun tr(en: String, ru: String): String = if (Locale.getDefault().language == "ru") ru else en
