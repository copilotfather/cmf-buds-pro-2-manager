package com.cmf.budscontroller.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Premium Nothing Glassmorphic card modifier.
 * Translucent frosted glass background with specular top reflection rim.
 */
fun Modifier.cmfGlassCard(
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
    borderBrush: Brush = CmfGlassBorderBrush,
    backgroundBrush: Brush = CmfGlassCardBrush
): Modifier = this
    .clip(shape)
    .background(backgroundBrush)
    .border(borderWidth, borderBrush, shape)

/**
 * Frosted translucent glass pill/control modifier for buttons and insets.
 */
fun Modifier.cmfGlassPill(
    shape: Shape = RoundedCornerShape(14.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color(0x30FFFFFF),
    backgroundColor: Color = Color(0x22FFFFFF)
): Modifier = this
    .clip(shape)
    .background(backgroundColor)
    .border(borderWidth, borderColor, shape)
