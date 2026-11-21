package com.adhdkiller.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pure black canvas. The palette is intentionally monochrome: white type on true black is the
 * highest-contrast, lowest-distraction surface a phone can offer. The only colour in the app is
 * the [Accent], which is driven by the user's level (see `GamificationRepository`).
 */

// Core surfaces
val Black = Color(0xFF000000)
val Ink = Color(0xFF0A0A0A)      // raised surface (cards)
val InkHigh = Color(0xFF141414)  // pressed / selected surface
val Hairline = Color(0xFF1E1E1E) // borders
val HairlineStrong = Color(0xFF2E2E2E)

// Text
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA6A6A6)
val TextTertiary = Color(0xFF6B6B6B)

// Semantic
val Danger = Color(0xFFFF4D4D)
val Success = Color(0xFF3DDC84)
val Warning = Color(0xFFFFB020)

/** Level-driven accent ramp. Index = level tier. */
enum class Accent(val color: Color, val label: String) {
    MINT(Color(0xFF3DDC84), "Mint"),
    CYAN(Color(0xFF4DD8FF), "Cyan"),
    VIOLET(Color(0xFF9D7BFF), "Violet"),
    AMBER(Color(0xFFFFB020), "Amber"),
    ROSE(Color(0xFFFF6B8A), "Rose"),
    LIME(Color(0xFFB8FF3D), "Lime"),
}

val AccentList: List<Accent> = Accent.entries
