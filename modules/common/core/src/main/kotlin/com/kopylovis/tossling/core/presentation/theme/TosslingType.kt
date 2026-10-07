package com.kopylovis.tossling.core.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kopylovis.tossling.core.R

val Onest = FontFamily(
    Font(resId = R.font.onest_regular, weight = FontWeight.Normal),
    Font(resId = R.font.onest_medium, weight = FontWeight.Medium),
    Font(resId = R.font.onest_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.onest_bold, weight = FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(resId = R.font.jetbrains_mono_medium, weight = FontWeight.Medium),
)

@Immutable
data class TosslingType(
    val largeTitle: TextStyle = style(size = 40, line = 44, weight = FontWeight.Bold, spacing = -0.025),
    val title: TextStyle = style(size = 34, line = 40, weight = FontWeight.Bold, spacing = -0.025),
    val title2: TextStyle = style(size = 22, line = 28, weight = FontWeight.Bold, spacing = -0.015),
    val compact: TextStyle = style(size = 17, line = 22, weight = FontWeight.SemiBold),
    val headline: TextStyle = style(size = 19, line = 24, weight = FontWeight.SemiBold, spacing = -0.01),
    val lead: TextStyle = style(size = 19, line = 26, weight = FontWeight.Medium),
    val button: TextStyle = style(size = 16, line = 20, weight = FontWeight.SemiBold),
    val row: TextStyle = style(size = 16, line = 22, weight = FontWeight.Medium),
    val body: TextStyle = style(size = 15, line = 21, weight = FontWeight.Normal),
    val hint: TextStyle = style(size = 14, line = 19, weight = FontWeight.Normal),
    val footnote: TextStyle = style(size = 13, line = 18, weight = FontWeight.Normal),
    val mono: TextStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    val monoSmall: TextStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0): TextStyle =
    TextStyle(
        fontFamily = Onest,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = spacing.em,
    )

val LocalTosslingType = staticCompositionLocalOf { TosslingType() }
