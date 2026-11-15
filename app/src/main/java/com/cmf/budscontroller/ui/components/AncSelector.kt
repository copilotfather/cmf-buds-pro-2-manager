package com.cmf.budscontroller.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                text = "Управление шумом",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = CmfTextPrimary,
                fontFamily = FontFamily.Default
            )
            Text(
                text = currentMode.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (currentMode == AncMode.OFF) CmfTextSecondary else vibe.accent,
                fontFamily = FontFamily.Default
            )
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isSelected) Color(0x60FFFFFF) else Color.Transparent)
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (mode) {
                            AncMode.OFF -> "Выкл"
                            AncMode.TRANSPARENCY -> "Прозрачность"
                            AncMode.NOISE_CANCELLATION -> "Шумоподавление"
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) CmfTextPrimary else CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        // Strength selector (Cupertino buttons)
        AnimatedVisibility(visible = currentMode == AncMode.NOISE_CANCELLATION) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                Text(
                    text = "Интенсивность",
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    0.8.dp,
                                    if (isLevelSelected) vibe.accent else CmfBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .background(if (isLevelSelected) vibe.accent.copy(alpha = 0.2f) else CmfSurfaceSubtle)
                                .clickable { onLevelSelected(level) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level.title,
                                fontSize = 11.sp,
                                fontWeight = if (isLevelSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isLevelSelected) vibe.accent else CmfTextSecondary,
                                fontFamily = FontFamily.Default
                            )
                        }
                    }
                }
            }
        }
    }
}
