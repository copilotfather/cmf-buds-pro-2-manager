package com.cmf.budscontroller.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Ultra-Vivid Brand & Accent Palettes (Signature CMF Electric Orange, Cyan, Neon Mint, Pure Silver)
enum class VibeColorTheme(
    val title: String,
    val accent: Color,
    val accentMuted: Color,
    val accentSubtle: Color
) {
    CMF_ORANGE("CMF Ultra Orange", Color(0xFFFF5722), Color(0xFFFF3D00), Color(0x35FF5722)),
    CMF_BLUE("Electric Cyan", Color(0xFF00E5FF), Color(0xFF00B0FF), Color(0x3500E5FF)),
    CMF_MINT("Neon Green", Color(0xFF00E676), Color(0xFF00C853), Color(0x3500E676)),
    CMF_MONO("Pure Platinum", Color(0xFFFFFFFF), Color(0xFFAAAAAA), Color(0x30FFFFFF))
}

// Backgrounds & Glass Surfaces - Deep OLED Pitch Black with crisp high-contrast cards
val CmfBlack = Color(0xFF08090C)          // Deep OLED Charcoal-Black
val CmfSurface = Color(0xFF141720)        // Clear elevated surface with high visibility
val CmfSurfaceSubtle = Color(0xFF1E2330)  // Rich contrast fill for chips/bars
val CmfSurfaceHigh = Color(0xFF252B3B)    // Tertiary distinct highlight container
val CmfBorder = Color(0x40FFFFFF)         // Bright specular crisp border (25% white)
val CmfBorderSubtle = Color(0x24FFFFFF)   // Clear visible divider (14% white)

// High-Contrast Crisp Typography
val CmfTextPrimary = Color(0xFFFFFFFF)    // Crisp White
val CmfTextSecondary = Color(0xFFB0B7C6)  // Bright high-readability Secondary
val CmfTextDisabled = Color(0xFF6B7280)   // Readable Quaternary

// Status & Accents (Vivid & punchy)
val CmfGreen = Color(0xFF00E676)          // Bright Punchy Neon Green
val CmfOrange = Color(0xFFFF5722)         // Iconic CMF Bright Orange
val CmfOrangeSubtle = Color(0x35FF5722)
val CmfRed = Color(0xFFFF334B)            // Vivid Carmine Red

// Frosted Glass Card Brush with vivid contrast
val CmfGlassCardBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF191D28), // Crisp high-contrast top
        Color(0xFF10131A)  // Deep bottom
    )
)

val CmfGlassBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0x75FFFFFF), // Ultra-bright top edge specular
        Color(0x30FFFFFF),
        Color(0x18FFFFFF)
    )
)

val CmfGlassPillBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF282E3E),
        Color(0xFF1C202C)
    )
)
