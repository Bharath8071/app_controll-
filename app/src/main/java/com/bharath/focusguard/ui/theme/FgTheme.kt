package com.bharath.focusguard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System fonts used as stand-ins for Newsreader / Atkinson until font files are bundled in res/font.
val FgTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
)

val FgLightColors = lightColorScheme(
    background = FgColor.Mist,
    surface = FgColor.Paper,
    onBackground = FgColor.Ink,
    onSurface = FgColor.Ink,
    primary = FgColor.Lock,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = FgColor.Tide,
    outline = FgColor.Line,
)

@Composable
fun FocusGuardLightTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = FgLightColors,
        typography = FgTypography,
        content = content,
    )
}
