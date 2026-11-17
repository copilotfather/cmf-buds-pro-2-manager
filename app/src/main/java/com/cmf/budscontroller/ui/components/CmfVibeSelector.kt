package com.cmf.budscontroller.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfVibeSelector(
    activeTheme: VibeColorTheme,
    onThemeSelected: (VibeColorTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalCmfDimens.current
    val themeTitleColor by animateColorAsState(
        targetValue = activeTheme.accent,
        animationSpec = tween(220),
        label = "theme_title_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(CmfGlassCardBrush)
            .border(0.8.dp, CmfGlassBorderBrush, RoundedCornerShape(dimens.cardCorner))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            Text(
                text = if (AppStrings.isRussian) "Цветовая тема" else "Color theme",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CmfTextPrimary,
                fontFamily = FontFamily.Default
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = activeTheme.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = themeTitleColor,
                fontFamily = FontFamily.Default
            )
        }

        // 4 Circular Color Swatches (Apple Style)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VibeColorTheme.values().forEach { theme ->
                val isSelected = activeTheme == theme
                val interaction = androidx.compose.runtime.remember(theme) { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val swatchScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isPressed) 0.88f else if (isSelected) 1.08f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 520f),
                    label = "theme_swatch_press"
                )
                val selectionBorder by animateColorAsState(
                    targetValue = if (isSelected) CmfTextPrimary else Color.Transparent,
                    animationSpec = tween(180),
                    label = "theme_swatch_border"
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = theme.title
                            selected = isSelected
                            role = Role.RadioButton
                        }
                        .clickable(interactionSource = interaction, indication = null) { onThemeSelected(theme) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(theme.accent)
                            .scale(swatchScale)
                            .border(2.dp, selectionBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(CmfBlack)
                            )
                        }
                    }
                }
            }
        }
    }
}
