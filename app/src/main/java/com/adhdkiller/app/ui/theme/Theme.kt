package com.adhdkiller.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app is always dark and always pure black — a light theme would defeat the purpose of a
 * distraction-free tool, and a white screen at 2am is exactly the wrong reward.
 */
private val BlackColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = Black,
    primaryContainer = InkHigh,
    onPrimaryContainer = TextPrimary,
    secondary = Accent.MINT.color,
    onSecondary = Black,
    tertiary = Accent.CYAN.color,
    onTertiary = Black,
    background = Black,
    onBackground = TextPrimary,
    surface = Black,
    onSurface = TextPrimary,
    surfaceVariant = Ink,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Ink,
    surfaceContainer = Ink,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = InkHigh,
    outline = HairlineStrong,
    outlineVariant = Hairline,
    error = Danger,
    onError = Black,
    scrim = Black,
)

/** Level-driven accent, exposed to every screen. */
val LocalAccent = staticCompositionLocalOf { Accent.MINT }

@Composable
fun ADHDKillerTheme(
    accent: Accent = Accent.MINT,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme() // the app is dark-only by design; this call documents the decision

    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = BlackColorScheme.copy(
                secondary = accent.color,
                tertiary = accent.color,
            ),
            typography = ADHDTypography,
            content = content,
        )
    }
}
