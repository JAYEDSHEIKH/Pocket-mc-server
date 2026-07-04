package com.pocketcraft.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Control-room palette ──────────────────────────────────────────────────────
// Primary surface: deep navy-black, like a server room with the lights low.
// Accent: bright teal/mint for active states ("this thing is alive").
// Warm white text keeps it legible without the eye-sting of pure #FFFFFF.

// Backgrounds
val Navy900 = Color(0xFF080C14)   // app background
val Navy800 = Color(0xFF0F1620)   // card surface
val Navy700 = Color(0xFF1A2235)   // surface variant / elevated
val Navy600 = Color(0xFF243048)   // borders, dividers

// Primary: Teal / Mint
val Teal300  = Color(0xFF00E5CC)  // primary (bright active accent)
val Teal500  = Color(0xFF00BFA5)  // primary variant
val Teal900  = Color(0xFF003330)  // onPrimary (text on teal bg)
val TealContainer = Color(0xFF004D44)

// Secondary: Soft blue (labels, secondary actions)
val Blue200  = Color(0xFF90CAF9)
val Blue700  = Color(0xFF1565C0)   // medium blue — light-scheme secondary
val Blue900  = Color(0xFF0D1B2A)

// Tertiary: Amber (warnings, STARTING/STOPPING status)
val Amber400 = Color(0xFFFFA726)
val Amber900 = Color(0xFF3D2000)

// Status colors
val StatusRunning  = Color(0xFF00E676)   // bright green — server live
val StatusStopped  = Color(0xFF546E7A)   // slate — idle
val StatusStarting = Color(0xFFFFA726)   // amber — in motion
val StatusCrashed  = Color(0xFFEF5350)   // red — something went wrong
val StatusDownload = Color(0xFF42A5F5)   // blue — downloading

// Error
val Red400 = Color(0xFFEF5350)
val Red900 = Color(0xFF3B0A0A)

// Text
val White95  = Color(0xFFF0F2F8)   // primary text (slightly cool white)
val White70  = Color(0xFFB0B8CF)   // secondary text
val White40  = Color(0xFF626E8A)   // disabled / hint

// Console log level colors
val LogInfo  = Color(0xFF90CAF9)   // blue-ish white — readable
val LogWarn  = Color(0xFFFFF176)   // yellow
val LogError = Color(0xFFEF9A9A)   // soft red (not harsh on eyes)
val LogJoin  = Color(0xFF80CBC4)   // teal — player join/leave
val LogDefault = Color(0xFFCFD8DC) // default console text (warm grey)
