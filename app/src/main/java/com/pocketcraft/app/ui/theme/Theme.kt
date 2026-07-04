package com.pocketcraft.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark color scheme — "control room" ───────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary            = Teal300,
    onPrimary          = Teal900,
    primaryContainer   = TealContainer,
    onPrimaryContainer = Teal300,

    secondary          = Blue200,
    onSecondary        = Blue900,
    secondaryContainer = Color(0xFF1A2A3D),
    onSecondaryContainer = Blue200,

    tertiary           = Amber400,
    onTertiary         = Amber900,
    tertiaryContainer  = Color(0xFF3D2800),
    onTertiaryContainer = Amber400,

    background         = Navy900,
    onBackground       = White95,

    surface            = Navy800,
    onSurface          = White95,
    surfaceVariant     = Navy700,
    onSurfaceVariant   = White70,

    outline            = Navy600,
    outlineVariant     = Color(0xFF1E2B3F),

    error              = Red400,
    onError            = Red900,
    errorContainer     = Color(0xFF4A1010),
    onErrorContainer   = Red400,

    inverseSurface     = White95,
    inverseOnSurface   = Navy900,
    inversePrimary     = Teal500,

    scrim              = Color(0x80000000),
    surfaceTint        = Teal300
)

@Composable
fun PocketCraftTheme(content: @Composable () -> Unit) {
    // Always dark — intentional design choice for the control-room aesthetic.
    // A light theme is a future stretch goal.
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
