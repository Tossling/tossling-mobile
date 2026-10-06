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

object Tossy {

    val palette: TossyPalette
        @Composable @ReadOnlyComposable get() = LocalTossyPalette.current

    val type: TossyType
        @Composable @ReadOnlyComposable get() = LocalTossyType.current
}

@Composable
fun TossyTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) TossyPalette.Dark else TossyPalette.Light
    val scheme = if (palette.isDark) {
        darkColorScheme(primary = palette.accent, background = palette.background, surface = palette.background, onSurface = palette.ink)
    } else {
        lightColorScheme(primary = palette.accent, background = palette.background, surface = palette.background, onSurface = palette.ink)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalTossyPalette provides palette,
            LocalTossyType provides TossyType(),
            LocalContentColor provides palette.ink,
            LocalTextSelectionColors provides TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.accentSoft),
            content = content,
        )
    }
}
