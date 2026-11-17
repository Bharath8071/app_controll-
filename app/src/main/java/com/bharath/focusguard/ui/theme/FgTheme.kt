package com.bharath.focusguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Hand-written cursive font for headings and sketchbook titles — works offline on all devices */
val CaveatFamily: FontFamily = FontFamily.Cursive

/** Casual font for body text */
val PatrickHandFamily: FontFamily = FontFamily.Default

val SketchTypography = Typography(
    displayLarge  = TextStyle(fontFamily = CaveatFamily, fontWeight = FontWeight.Bold,   fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = CaveatFamily, fontWeight = FontWeight.Bold,   fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium= TextStyle(fontFamily = CaveatFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge    = TextStyle(fontFamily = CaveatFamily, fontWeight = FontWeight.Bold,   fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium   = TextStyle(fontFamily = CaveatFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge     = TextStyle(fontFamily = PatrickHandFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = PatrickHandFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge    = TextStyle(fontFamily = PatrickHandFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.08.sp),
    labelSmall    = TextStyle(fontFamily = PatrickHandFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
)

val SketchColorScheme = lightColorScheme(
    background        = FgColor.BgDot,
    surface           = FgColor.Paper,
    onBackground      = FgColor.Ink,
    onSurface         = FgColor.Ink,
    primary           = FgColor.Ink,
    onPrimary         = FgColor.Paper,
    primaryContainer  = FgColor.Yellow,
    onPrimaryContainer= FgColor.Ink,
    secondary         = FgColor.Blue,
    onSecondary       = FgColor.Paper,
    secondaryContainer= FgColor.BlueSoft,
    onSecondaryContainer = FgColor.Ink,
    error             = FgColor.Red,
    onError           = FgColor.Paper,
    outline           = FgColor.Line,
    surfaceVariant    = FgColor.BgDot,
    onSurfaceVariant  = FgColor.Slate,
)

@Composable
fun SketchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SketchColorScheme,
        typography  = SketchTypography,
        content     = content,
    )
}

// Keep old name as alias for backward compat
@Composable
fun FocusGuardLightTheme(content: @Composable () -> Unit) = SketchTheme(content)
