package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.ui.theme.*

@Composable
fun AutomationSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CmfSurface)
            .border(1.dp, CmfBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "ТАСКЕР / ИНТЕНТЫ",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = CmfTextSecondary,
            fontFamily = FontFamily.Default
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Широковещательные команды для управления из других программ:",
            fontSize = 12.sp,
            color = CmfTextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CmfSurfaceSubtle)
                .border(1.dp, CmfBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "ACTION: com.cmf.budscontroller.SET_ANC_MODE",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    color = CmfOrange
                )
                Text(
                    text = "EXTRA: mode=\"off\"|\"transparency\"|\"anc\"",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default,
                    color = CmfTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ACTION: com.cmf.budscontroller.SET_GAME_MODE",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    color = CmfOrange
                )
                Text(
                    text = "EXTRA: enabled=true|false",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default,
                    color = CmfTextSecondary
                )
            }
        }
    }
}
