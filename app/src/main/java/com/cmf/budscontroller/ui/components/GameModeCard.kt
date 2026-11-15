package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfGameModeSection(
    isEnabled: Boolean,
    onToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CmfSurface)
            .border(1.5.dp, CmfBorder, RoundedCornerShape(26.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Игровой режим с низкой задержкой",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = CmfTextSecondary,
                fontFamily = FontFamily.Default
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (isEnabled) "Сверхнизкая задержка активна" else "Стандартный кодек",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) vibe.accent else CmfTextPrimary
            )
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CmfTextPrimary,
                checkedTrackColor = vibe.accent,
                uncheckedThumbColor = CmfTextSecondary,
                uncheckedTrackColor = CmfSurfaceSubtle,
                uncheckedBorderColor = CmfBorder
            )
        )
    }
}
