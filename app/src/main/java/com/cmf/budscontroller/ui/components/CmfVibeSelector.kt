package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfVibeSelector(
    activeTheme: VibeColorTheme,
    onThemeSelected: (VibeColorTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalCmfDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(CmfGlassCardBrush)
            .border(0.8.dp, CmfGlassBorderBrush, RoundedCornerShape(dimens.cardCorner))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
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
                color = activeTheme.accent,
                fontFamily = FontFamily.Default
            )
        }

        // 4 Circular Color Swatches (Apple Style)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VibeColorTheme.values().forEach { theme ->
                val isSelected = activeTheme == theme
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(theme.accent)
                        .border(
                            2.dp,
                            if (isSelected) CmfTextPrimary else Color.Transparent,
                            CircleShape
                        )
                        .clickable { onThemeSelected(theme) },
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
