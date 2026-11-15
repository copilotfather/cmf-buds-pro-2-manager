package com.cmf.budscontroller.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// -------------------------------------------------------------
// Pure Frosted Gray Glass Palette (Liquid Gray Glass & Specular)
// -------------------------------------------------------------

val GrayGlassDeep = Color(0x38181A20)         // 22% deep smoked translucent glass
val GrayGlassSurface = Color(0x28262933)      // 16% neutral liquid gray glass
val GrayGlassPillBg = Color(0x352E323D)       // 21% elevated gray glass
val GrayGlassActive = Color(0x50424859)       // 31% focused gray glass with gloss

// Dual-layer specular hairline border brush (white-to-transparent rim lighting)
val LiquidGlassBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0x70FFFFFF), // 44% specular top rim highlight
        Color(0x20FFFFFF), // 12% subtle body light
        Color(0x08FFFFFF), // 3% lower diffusion
        Color(0x25FFFFFF)  // 14% bottom ambient bounce
    )
)

val LiquidGlassActiveBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0xB0FFFFFF), // 70% intense top reflection
        Color(0x40FFFFFF),
        Color(0x15FFFFFF),
        Color(0x50FFFFFF)
    )
)

val LiquidGlassBackgroundBrush = Brush.verticalGradient(
    listOf(
        Color(0x422D303B), // Top gloss gray sheen
        Color(0x26191B22), // Center frosted smoked gray
        Color(0x3522252E)  // Bottom depth gray
    )
)

val SubsurfaceGlassGradient = Brush.radialGradient(
    colors = listOf(
        Color(0x354A5163), // Internal glass dispersion glow
        Color(0x1820232B),
        Color(0x00000000)
    )
)

// -------------------------------------------------------------
// AGSL Frosted Liquid Gray Glass Shader (Android 13+ / Tiramisu)
// -------------------------------------------------------------

private const val LIQUID_GLASS_SHADER_SRC = """
    uniform float2 resolution;
    uniform float time;
    uniform shader composable;

    vec4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        
        // Subtle optical chromatic dispersion on glass edges
        float distFromCenter = length(uv - 0.5);
        float dispersion = 0.003 * distFromCenter;
        
        vec4 colCenter = composable.eval(fragCoord);
        vec4 colR = composable.eval(fragCoord + float2(dispersion * resolution.x, 0.0));
        vec4 colB = composable.eval(fragCoord - float2(dispersion * resolution.x, 0.0));
        
        vec4 outCol = vec4(colR.r, colCenter.g, colB.b, colCenter.a);
        
        // Specular top light streak
        float lightStreak = smoothstep(0.0, 0.15, uv.y) * (1.0 - smoothstep(0.0, 0.45, uv.y));
        outCol.rgb += vec3(0.08) * lightStreak;
        
        return outCol;
    }
"""

/**
 * Applies high-end frosted gray glass styling with physical refraction and specular borders.
 */
fun Modifier.liquidGrayGlass(
    shape: Shape = RoundedCornerShape(26.dp),
    borderWidth: Dp = 1.dp,
    borderBrush: Brush = LiquidGlassBorderBrush,
    bgBrush: Brush = LiquidGlassBackgroundBrush
): Modifier = this
    .clip(shape)
    .background(bgBrush)
    .border(borderWidth, borderBrush, shape)

/**
 * Interactive pulsating ambient glow behind frosted cards.
 */
@Composable
fun Modifier.liquidGlassShimmer(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidGlassShimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    return this.graphicsLayer {
        alpha = alphaAnim
    }
}
