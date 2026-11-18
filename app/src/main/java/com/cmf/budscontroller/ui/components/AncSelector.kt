package com.cmf.budscontroller.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.protocol.AncLevel
import com.cmf.budscontroller.protocol.AncMode
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfAncSection(
    currentMode: AncMode,
    currentLevel: AncLevel,
    onModeSelected: (AncMode) -> Unit,
    onLevelSelected: (AncLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current
    val dimens = LocalCmfDimens.current
    val modeTint by animateColorAsState(
        targetValue = if (currentMode == AncMode.OFF) CmfTextSecondary else vibe.accent,
        animationSpec = tween(180),
        label = "anc_current_mode_tint"
    )

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
                text = AppStrings.headerNoiseControl,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = CmfTextPrimary,
                fontFamily = FontFamily.Default
            )
            AnimatedContent(
                targetState = currentMode.title,
                transitionSpec = {
                    (fadeIn(tween(150)) + slideInVertically(tween(170)) { it / 3 }) togetherWith
                        (fadeOut(tween(100)) + slideOutVertically(tween(120)) { -it / 3 })
                },
                label = "anc_current_mode"
            ) { modeTitle ->
                Text(
                    text = modeTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = modeTint,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Apple-style iOS Segmented Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x28FFFFFF))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            AncMode.values().forEach { mode ->
                val isSelected = currentMode == mode
                val interaction = androidx.compose.runtime.remember(mode) { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val pressScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isPressed) 0.96f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 600f),
                    label = "anc_mode_press"
                )
                val modeBackground by animateColorAsState(
                    targetValue = if (isSelected) Color(0x60FFFFFF) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "anc_mode_background"
                )
                val modeTextColor by animateColorAsState(
                    targetValue = if (isSelected) CmfTextPrimary else CmfTextSecondary,
                    animationSpec = tween(180),
                    label = "anc_mode_text"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = dimens.controlTouchTarget)
                        .clip(RoundedCornerShape(11.dp))
                        .background(modeBackground)
                        .scale(pressScale)
                        .clickable(interactionSource = interaction, indication = null) { onModeSelected(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = modeTextColor,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        // Strength selector (Cupertino buttons)
        AnimatedVisibility(visible = currentMode == AncMode.NOISE_CANCELLATION) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                Text(
                    text = if (AppStrings.isRussian) "Интенсивность" else "Intensity",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AncLevel.values().forEach { level ->
                        val isLevelSelected = currentLevel == level
                        val interaction = androidx.compose.runtime.remember(level) { MutableInteractionSource() }
                        val isPressed by interaction.collectIsPressedAsState()
                        val pressScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isPressed) 0.96f else 1f,
                            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 600f),
                            label = "anc_level_press"
                        )
                        val levelBorder by animateColorAsState(
                            targetValue = if (isLevelSelected) vibe.accent else CmfBorder,
                            animationSpec = tween(180),
                            label = "anc_level_border"
                        )
                        val levelBackground by animateColorAsState(
                            targetValue = if (isLevelSelected) vibe.accent.copy(alpha = 0.2f) else CmfSurfaceSubtle,
                            animationSpec = tween(180),
                            label = "anc_level_background"
                        )
                        val levelTextColor by animateColorAsState(
                            targetValue = if (isLevelSelected) vibe.accent else CmfTextSecondary,
                            animationSpec = tween(180),
                            label = "anc_level_text"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = dimens.controlTouchTarget)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    0.8.dp,
                                    levelBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .background(levelBackground)
                                .scale(pressScale)
                                .clickable(interactionSource = interaction, indication = null) { onLevelSelected(level) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level.title,
                                fontSize = 11.sp,
                                fontWeight = if (isLevelSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = levelTextColor,
                                fontFamily = FontFamily.Default
                            )
                        }
                    }
                }
            }
        }
    }
}
