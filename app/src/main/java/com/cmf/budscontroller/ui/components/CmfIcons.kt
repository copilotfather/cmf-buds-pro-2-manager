package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom CMF / Nothing Geometric Vector Icons
 * Pure vector paths, zero emojis, zero raster bloat, pixel-perfect at any DPI.
 */

@Composable
fun CmfIconBolt(
    modifier: Modifier = Modifier.size(16.dp),
    tint: Color = Color(0xFFFF4400)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.58f, h * 0.05f)
            lineTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.95f)
            lineTo(w * 0.78f, h * 0.48f)
            lineTo(w * 0.50f, h * 0.48f)
            close()
        }
        drawPath(path, color = tint, style = Fill)
    }
}

@Composable
fun CmfIconPlay(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.32f, h * 0.20f)
            lineTo(w * 0.78f, h * 0.50f)
            lineTo(w * 0.32f, h * 0.80f)
            close()
        }
        drawPath(path, color = tint, style = Fill)
    }
}

@Composable
fun CmfIconPause(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.22f
        val barHeight = h * 0.60f
        val top = h * 0.20f
        val radius = CornerRadius(barWidth * 0.35f, barWidth * 0.35f)

        // Left bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.24f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
        )
        // Right bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.54f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
        )
    }
}

@Composable
fun CmfIconSkipNext(
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val triangle = Path().apply {
            moveTo(w * 0.22f, h * 0.22f)
            lineTo(w * 0.64f, h * 0.50f)
            lineTo(w * 0.22f, h * 0.78f)
            close()
        }
        drawPath(triangle, color = tint, style = Fill)

        val barWidth = w * 0.14f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.68f, h * 0.22f),
            size = Size(barWidth, h * 0.56f),
            cornerRadius = CornerRadius(barWidth * 0.4f, barWidth * 0.4f)
        )
    }
}

@Composable
fun CmfIconSkipPrevious(
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val barWidth = w * 0.14f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.22f),
            size = Size(barWidth, h * 0.56f),
            cornerRadius = CornerRadius(barWidth * 0.4f, barWidth * 0.4f)
        )

        val triangle = Path().apply {
            moveTo(w * 0.78f, h * 0.22f)
            lineTo(w * 0.36f, h * 0.50f)
            lineTo(w * 0.78f, h * 0.78f)
            close()
        }
        drawPath(triangle, color = tint, style = Fill)
    }
}

@Composable
fun CmfIconRotateCcw(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color(0xFFFF4400)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.12f

        // 270 degree circular arc
        drawArc(
            color = tint,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.64f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Arrowhead
        val arrow = Path().apply {
            moveTo(w * 0.08f, h * 0.38f)
            lineTo(w * 0.28f, h * 0.22f)
            lineTo(w * 0.32f, h * 0.46f)
            close()
        }
        drawPath(arrow, color = tint, style = Fill)
    }
}

@Composable
fun CmfIconRotateCw(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color(0xFFFF4400)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.12f

        // 270 degree circular arc
        drawArc(
            color = tint,
            startAngle = 315f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.18f),
            size = Size(w * 0.64f, h * 0.64f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Arrowhead
        val arrow = Path().apply {
            moveTo(w * 0.92f, h * 0.38f)
            lineTo(w * 0.72f, h * 0.22f)
            lineTo(w * 0.68f, h * 0.46f)
            close()
        }
        drawPath(arrow, color = tint, style = Fill)
    }
}

@Composable
fun CmfIconWaterDrop(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color(0xFF00E5FF)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val drop = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            cubicTo(w * 0.42f, h * 0.32f, w * 0.18f, h * 0.56f, w * 0.18f, h * 0.70f)
            cubicTo(w * 0.18f, h * 0.86f, w * 0.32f, h * 0.94f, w * 0.50f, h * 0.94f)
            cubicTo(w * 0.68f, h * 0.94f, w * 0.82f, h * 0.86f, w * 0.82f, h * 0.70f)
            cubicTo(w * 0.82f, h * 0.56f, w * 0.58f, h * 0.32f, w * 0.50f, h * 0.12f)
            close()
        }
        drawPath(drop, color = tint, style = Fill)

        // Acoustic spray ripples
        drawArc(
            color = tint.copy(alpha = 0.6f),
            startAngle = 200f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(w * 0.04f, h * 0.24f),
            size = Size(w * 0.92f, h * 0.60f),
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun CmfIconSpatial3D(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color(0xFFFF4400)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f

        // Central sound emitter node
        drawCircle(
            color = tint,
            radius = w * 0.12f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Left acoustic orbit ellipse
        drawOval(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.26f),
            size = Size(w * 0.76f, h * 0.48f),
            style = Stroke(width = stroke)
        )

        // Orthogonal 3D sound ring
        drawOval(
            color = tint.copy(alpha = 0.5f),
            topLeft = Offset(w * 0.26f, h * 0.12f),
            size = Size(w * 0.48f, h * 0.76f),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun CmfIconEarTipSeal(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color(0xFF00E676)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.10f

        // Outer seal boundary
        drawCircle(
            color = tint,
            radius = w * 0.42f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke)
        )

        // Checkmark inside for airtight seal
        val check = Path().apply {
            moveTo(w * 0.28f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.66f)
            lineTo(w * 0.72f, h * 0.34f)
        }
        drawPath(check, color = tint, style = Stroke(width = stroke * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun CmfIconSpeaker(
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val cone = Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.32f, h * 0.35f)
            lineTo(w * 0.55f, h * 0.18f)
            lineTo(w * 0.55f, h * 0.82f)
            lineTo(w * 0.32f, h * 0.65f)
            lineTo(w * 0.15f, h * 0.65f)
            close()
        }
        drawPath(cone, color = tint, style = Fill)

        // Sound radiation wave
        drawArc(
            color = tint,
            startAngle = 310f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(w * 0.40f, h * 0.30f),
            size = Size(w * 0.38f, h * 0.40f),
            style = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun CmfIconDialKnob(
    modifier: Modifier = Modifier.size(26.dp),
    tint: Color = Color(0xFFFF4400)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.45f
        val center = Offset(w * 0.5f, h * 0.5f)

        // Knurled outer ring
        drawCircle(
            color = tint,
            radius = r,
            center = center,
            style = Stroke(width = w * 0.08f)
        )

        // Dot indicator
        drawCircle(
            color = tint,
            radius = w * 0.12f,
            center = Offset(center.x, center.y - r * 0.55f),
            style = Fill
        )

        // Center hub
        drawCircle(
            color = tint.copy(alpha = 0.3f),
            radius = w * 0.18f,
            center = center,
            style = Fill
        )
    }
}

@Composable
fun CmfIconNavDevice(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawOval(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.15f),
            size = Size(w * 0.56f, h * 0.44f)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.42f, h * 0.45f),
            size = Size(w * 0.16f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
    }
}

@Composable
fun CmfIconNavEqualizer(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        val cols = listOf(0.24f, 0.50f, 0.76f)
        val knobYs = listOf(0.35f, 0.65f, 0.45f)

        cols.forEachIndexed { i, colX ->
            val cx = w * colX
            drawLine(
                color = tint.copy(alpha = 0.5f),
                start = Offset(cx, h * 0.15f),
                end = Offset(cx, h * 0.85f),
                strokeWidth = stroke
            )
            drawCircle(
                color = tint,
                radius = 3.5.dp.toPx(),
                center = Offset(cx, h * knobYs[i])
            )
        }
    }
}

@Composable
fun CmfIconNavBridge(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.28f, h * 0.20f),
            size = Size(w * 0.44f, h * 0.36f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(color = tint, start = Offset(w * 0.40f, h * 0.26f), end = Offset(w * 0.40f, h * 0.38f), strokeWidth = stroke)
        drawLine(color = tint, start = Offset(w * 0.60f, h * 0.26f), end = Offset(w * 0.60f, h * 0.38f), strokeWidth = stroke)
        drawLine(color = tint, start = Offset(w * 0.50f, h * 0.56f), end = Offset(w * 0.50f, h * 0.85f), strokeWidth = stroke * 1.5f)
    }
}

@Composable
fun CmfIconNavTools(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.5f)

        drawCircle(
            color = tint,
            radius = w * 0.36f,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = tint,
            radius = w * 0.12f,
            center = center
        )
    }
}

@Composable
fun CmfIconEarbudsHero(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Left earbud head & stem
        drawCircle(
            color = tint,
            radius = w * 0.14f,
            center = Offset(w * 0.32f, h * 0.32f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, h * 0.46f),
            end = Offset(w * 0.32f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Right earbud head & stem
        drawCircle(
            color = tint,
            radius = w * 0.14f,
            center = Offset(w * 0.68f, h * 0.32f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.68f, h * 0.46f),
            end = Offset(w * 0.68f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CmfIconCaseSmartDial(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.White,
    accent: Color = Color(0xFFFF9500)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        // Rounded square case
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.22f, w * 0.22f),
            style = Stroke(width = stroke)
        )

        // Smart Dial wheel top-right
        val dialCenter = Offset(w * 0.68f, h * 0.32f)
        drawCircle(
            color = accent,
            radius = w * 0.14f,
            center = dialCenter,
            style = Stroke(width = stroke * 1.2f)
        )
        drawCircle(
            color = accent,
            radius = w * 0.05f,
            center = dialCenter,
            style = Fill
        )
    }
}

@Composable
fun CmfIconAncShield(
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.85f, h * 0.26f)
            lineTo(w * 0.85f, h * 0.55f)
            cubicTo(w * 0.85f, h * 0.75f, w * 0.5f, h * 0.90f, w * 0.5f, h * 0.90f)
            cubicTo(w * 0.5f, h * 0.90f, w * 0.15f, h * 0.75f, w * 0.15f, h * 0.55f)
            lineTo(w * 0.15f, h * 0.26f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Center cancel line
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, h * 0.50f),
            end = Offset(w * 0.64f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CmfIconTransparencyWaves(
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        val xs = listOf(0.18f, 0.34f, 0.50f, 0.66f, 0.82f)
        val heights = listOf(0.24f, 0.45f, 0.65f, 0.45f, 0.24f)

        xs.forEachIndexed { i, x ->
            val barH = heights[i]
            val topY = (h * (1f - barH)) / 2f
            drawLine(
                color = tint,
                start = Offset(w * x, topY),
                end = Offset(w * x, topY + h * barH),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun CmfIconAncOff(
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        drawCircle(
            color = tint,
            radius = w * 0.35f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun CmfIconGamepad(
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        // Gamepad body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.25f),
            size = Size(w * 0.76f, h * 0.50f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
            style = Stroke(width = stroke)
        )

        // D-pad cross (left)
        drawLine(color = tint, start = Offset(w * 0.26f, h * 0.50f), end = Offset(w * 0.38f, h * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.32f, h * 0.42f), end = Offset(w * 0.32f, h * 0.58f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Action dots (right)
        drawCircle(color = tint, radius = w * 0.04f, center = Offset(w * 0.66f, h * 0.44f), style = Fill)
        drawCircle(color = tint, radius = w * 0.04f, center = Offset(w * 0.74f, h * 0.54f), style = Fill)
    }
}

