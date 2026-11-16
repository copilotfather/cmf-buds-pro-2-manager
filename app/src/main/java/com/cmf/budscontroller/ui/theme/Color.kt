package com.cmf.budscontroller.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Optional device accent palettes. The app shell uses the restrained system blue by default.
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

// iOS-style grouped dark surfaces.
val CmfBlack = Color(0xFF000000)
val CmfSurface = Color(0xFF1C1C1E)
val CmfSurfaceSubtle = Color(0xFF2C2C2E)
val CmfSurfaceHigh = Color(0xFF3A3A3C)
val CmfBorder = Color(0xFF38383A)
val CmfBorderSubtle = Color(0xFF2C2C2E)

// High-Contrast Crisp Typography
val CmfTextPrimary = Color(0xFFF5F5F7)
val CmfTextSecondary = Color(0xFF98989D)
val CmfTextDisabled = Color(0xFF636366)

// Status & Accents (Vivid & punchy)
val CmfGreen = Color(0xFF30D158)
val CmfOrange = Color(0xFF0A84FF)
val CmfOrangeSubtle = Color(0x330A84FF)
val CmfRed = Color(0xFFFF453A)

// Neutral grouped surfaces; avoid the previous high-contrast glass effect.
val CmfGlassCardBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF1C1C1E),
        Color(0xFF1C1C1E)
    )
)

val CmfGlassBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF38383A),
        Color(0xFF38383A),
        Color(0xFF38383A)
    )
)

val CmfGlassPillBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF3A3A3C),
        Color(0xFF2C2C2E)
    )
)
