package com.kopylovis.tossling.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors

object Tossling {

    val palette: TosslingPalette
        @Composable @ReadOnlyComposable get() = LocalTosslingPalette.current

    val type: TosslingType
        @Composable @ReadOnlyComposable get() = LocalTosslingType.current
}

@Composable
fun TosslingTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) TosslingPalette.Dark else TosslingPalette.Light
    val scheme = if (palette.isDark) {
        darkColorScheme(primary = palette.accent, background = palette.background, surface = palette.background, onSurface = palette.ink)
    } else {
        lightColorScheme(primary = palette.accent, background = palette.background, surface = palette.background, onSurface = palette.ink)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalTosslingPalette provides palette,
            LocalTosslingType provides TosslingType(),
            LocalContentColor provides palette.ink,
            LocalTextSelectionColors provides TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.accentSoft),
            content = content,
        )
    }
}
