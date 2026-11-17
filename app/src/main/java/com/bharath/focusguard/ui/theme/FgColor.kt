package com.bharath.focusguard.ui.theme

import androidx.compose.ui.graphics.Color

/** Sketchbook palette — matches the HTML reference hand-drawn bento UI. */
object FgColor {
    // Core paper tones
    val BgDot   = Color(0xFFF3EDE0)   // dotted canvas background
    val Paper   = Color(0xFFFBF8F1)   // card / surface
    val Ink     = Color(0xFF25221E)   // primary text / borders
    val Slate   = Color(0xFF7C756C)   // secondary / soft text

    // Accent colours matching the HTML
    val Red     = Color(0xFFE65C4F)   // red pen
    val Blue    = Color(0xFF3B7DD8)   // blue pen
    val BlueSoft= Color(0xFFDCE9F9)   // blue-tinted surface
    val Green   = Color(0xFF56B881)   // green pen
    val GreenSoft=Color(0xFFBCE8CF)  // green-tinted surface
    val Yellow  = Color(0xFFFFDE59)   // highlighter / sticky note
    val YellowPad=Color(0xFFFFE156)   // todo sticky pad bg
    val Peach   = Color(0xFFFBC7BC)   // washi-tape / peach card
    val Pink    = Color(0xFFF9CBD6)   // pink accent

    // Semantic aliases kept for backward compat with existing screens
    val Mist        = BgDot
    val Line        = Color(0xFFD6CEBD)  // subtle border line
    val Tide        = Green
    val TideInk     = Color(0xFF3A9163)
    val TideField   = GreenSoft
    val Lock        = Blue
    val LockField   = BlueSoft
    val Flag        = Red
    val FlagField   = Color(0xFFFCE2DF)
}
