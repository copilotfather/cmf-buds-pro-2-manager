package com.cmf.budscontroller.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalVibeTheme = staticCompositionLocalOf { VibeColorTheme.CMF_ORANGE }

data class CmfAdaptiveDimens(
    val cardPadding: Dp = 15.dp,
    val cardCorner: Dp = 20.dp,
    val cardSpacing: Dp = 11.dp,
    val spacerLarge: Dp = 14.dp,
    val spacerMedium: Dp = 10.dp,
    val spacerSmall: Dp = 6.dp,
    val dialSize: Dp = 50.dp,
    val playButtonSize: Dp = 50.dp,
    val skipButtonSize: Dp = 42.dp,
    val isCompact: Boolean = false
)

val LocalCmfDimens = staticCompositionLocalOf { CmfAdaptiveDimens() }

val VibeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun CMFTheme(
    vibeTheme: VibeColorTheme = VibeColorTheme.CMF_ORANGE,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    // Adapt to diverse aspect ratios, phone sizes (compact/standard/tall/wide)
    val isCompact = screenWidth < 380 || screenHeight < 740

    val dimens = remember(screenWidth, screenHeight) {
        if (isCompact) {
            CmfAdaptiveDimens(
                cardPadding = 14.dp,
                cardCorner = 22.dp,
                cardSpacing = 10.dp,
                spacerLarge = 12.dp,
                spacerMedium = 8.dp,
                spacerSmall = 4.dp,
                dialSize = 48.dp,
                playButtonSize = 46.dp,
                skipButtonSize = 38.dp,
                isCompact = true
            )
        } else {
            CmfAdaptiveDimens(
                cardPadding = 18.dp,
                cardCorner = 26.dp,
                cardSpacing = 12.dp,
                spacerLarge = 14.dp,
                spacerMedium = 10.dp,
                spacerSmall = 6.dp,
                dialSize = 54.dp,
                playButtonSize = 52.dp,
                skipButtonSize = 44.dp,
                isCompact = false
            )
        }
    }

    val colorScheme = darkColorScheme(
        primary = vibeTheme.accent,
        secondary = vibeTheme.accentMuted,
        background = CmfBlack,
        surface = CmfSurface,
        surfaceVariant = CmfSurfaceSubtle,
        onPrimary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = CmfTextSecondary,
        outline = CmfBorder,
        outlineVariant = CmfBorderSubtle
    )

    CompositionLocalProvider(
        LocalVibeTheme provides vibeTheme,
        LocalCmfDimens provides dimens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = VibeShapes,
            content = content
        )
    }
}
