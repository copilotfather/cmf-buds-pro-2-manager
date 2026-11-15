package com.cmf.budscontroller.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Apple-inspired Cupertino Accent Palettes (Space Black, Silver, Deep Navy, Alpine Green, Pacific Blue, Sunset Orange)
enum class VibeColorTheme(
    val title: String,
    val accent: Color,
    val accentMuted: Color,
    val accentSubtle: Color
) {
    CMF_ORANGE("Sunset Orange", Color(0xFFFF9500), Color(0xFFFF7A00), Color(0x24FF9500)),
    CMF_BLUE("Pacific Blue", Color(0xFF007AFF), Color(0xFF0056B3), Color(0x24007AFF)),
    CMF_MINT("Alpine Green", Color(0xFF34C759), Color(0xFF28A745), Color(0x2434C759)),
    CMF_MONO("Silver & White", Color(0xFFFFFFFF), Color(0xFF8E8E93), Color(0x20FFFFFF))
}

// Backgrounds & Glass Surfaces - Apple iOS / macOS Dark Cupertino Frosted Glass
val CmfBlack = Color(0xFF000000)          // Pure iOS Deep OLED Black
val CmfSurface = Color(0xCC1C1C1E)        // Apple Secondary System Background (frosted)
val CmfSurfaceSubtle = Color(0x1FFFFFFF)  // Apple System Fill (thin translucent blur)
val CmfSurfaceHigh = Color(0xE02C2C2E)    // Apple Tertiary System Background
val CmfBorder = Color(0x24FFFFFF)         // Delicate Apple glass hairline border (14% white)
val CmfBorderSubtle = Color(0x12FFFFFF)   // Ultra-subtle divider (7% white)

// Apple Typography (SF Pro style)
val CmfTextPrimary = Color(0xFFFFFFFF)    // Apple Label Primary
val CmfTextSecondary = Color(0xFF8E8E93)  // Apple Label Secondary (systemGray)
val CmfTextDisabled = Color(0xFF48484A)   // Apple Label Quaternary

// Status & Accents
val CmfGreen = Color(0xFF34C759)          // Apple System Green
val CmfOrange = Color(0xFFFF9500)         // Apple System Orange
val CmfOrangeSubtle = Color(0x24FF9500)
val CmfRed = Color(0xFFFF3B30)            // Apple System Red

// Apple Frosted Glass Card Brush
val CmfGlassCardBrush = Brush.verticalGradient(
    listOf(
        Color(0xCC1F1F24), // iOS Frosted Glass Top Sheen
        Color(0xB3141418)  // iOS Frosted Glass Bottom
    )
)

val CmfGlassBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0x40FFFFFF), // Hairline specular edge
        Color(0x18FFFFFF),
        Color(0x0CFFFFFF)
    )
)

val CmfGlassPillBrush = Brush.verticalGradient(
    listOf(
        Color(0x28FFFFFF),
        Color(0x12FFFFFF)
    )
)
