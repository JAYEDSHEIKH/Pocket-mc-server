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

// ── Light color scheme ────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary            = Teal500,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFB2EBF2),
    onPrimaryContainer = Teal900,

    secondary          = Blue700,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFBBDEFB),
    onSecondaryContainer = Blue900,

    background         = Color(0xFFF5F8FA),
    onBackground       = Color(0xFF0D1117),

    surface            = Color.White,
    onSurface          = Color(0xFF0D1117),
    surfaceVariant     = Color(0xFFE8EDF2),
    onSurfaceVariant   = Color(0xFF4A5568),

    outline            = Color(0xFFB0BEC5),

    error              = Color(0xFFB00020),
    onError            = Color.White,
    errorContainer     = Color(0xFFFFDAD6),
    onErrorContainer   = Color(0xFF93000A)
)

@Composable
fun PocketCraftTheme(
    darkTheme: Boolean = true,   // default to dark for the control-room aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
