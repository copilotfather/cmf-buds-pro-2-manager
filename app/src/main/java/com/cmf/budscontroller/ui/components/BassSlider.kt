package com.cmf.budscontroller.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfUltraBassSection(
    currentLevel: Int,
    onLevelChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current
    val dimens = LocalCmfDimens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(CmfGlassCardBrush)
            .border(0.8.dp, CmfGlassBorderBrush, RoundedCornerShape(dimens.cardCorner))
            .padding(dimens.cardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.headerUltraBass,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = CmfTextPrimary,
                fontFamily = FontFamily.Default
            )
            Text(
                text = if (currentLevel == 0) AppStrings.off else "${AppStrings.ultraBassLevel} $currentLevel",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (currentLevel > 0) vibe.accent else CmfTextSecondary,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 6 Apple-style Stepper Buttons (0 to 5)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (0..5).forEach { level ->
                val isSelected = currentLevel == level
                val interaction = androidx.compose.runtime.remember(level) { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val pressScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isPressed) 0.92f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 600f),
                    label = "bass_level_press"
                )
                val levelBorder by animateColorAsState(
                    targetValue = if (isSelected) vibe.accent else CmfBorder,
                    animationSpec = tween(180),
                    label = "bass_level_border"
                )
                val levelBackground by animateColorAsState(
                    targetValue = if (isSelected) vibe.accent else CmfSurfaceSubtle,
                    animationSpec = tween(180),
                    label = "bass_level_background"
                )
                val levelText by animateColorAsState(
                    targetValue = if (isSelected) CmfTextPrimary else CmfTextSecondary,
                    animationSpec = tween(180),
                    label = "bass_level_text"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            0.8.dp,
                            levelBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .background(levelBackground)
                        .scale(pressScale)
                        .clickable(interactionSource = interaction, indication = null) { onLevelChanged(level) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$level",
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = levelText,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }
    }
}
