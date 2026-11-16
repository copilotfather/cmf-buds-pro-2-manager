package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.tools.RelaxNoiseType
import com.cmf.budscontroller.tools.SleepTimerState
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfSleepRelaxCard(
    sleepState: SleepTimerState,
    onStartTimer: (Int, RelaxNoiseType) -> Unit,
    onStopTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMinutes by remember { mutableStateOf(30) }
    var selectedNoise by remember { mutableStateOf(RelaxNoiseType.BROWN_NOISE) }

    val timeOptions = listOf(15, 30, 45, 60)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CmfSurface)
            .border(1.dp, CmfBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.sleepTimerTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = CmfTextSecondary,
                fontFamily = FontFamily.Default
            )
            if (sleepState.isActive) {
                Text(
                    text = "${sleepState.minutesRemaining} ${AppStrings.minUnit.uppercase()} ${if (AppStrings.isRussian) "ДО СНА" else "UNTIL SLEEP"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = CmfOrange,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (AppStrings.isRussian) "Время таймера сна:" else "Sleep timer duration:",
            fontSize = 12.sp,
            color = CmfTextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Time selector pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            timeOptions.forEach { mins ->
                val isSelected = if (sleepState.isActive) sleepState.selectedMinutes == mins else selectedMinutes == mins
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .border(1.dp, if (isSelected) CmfOrange else CmfBorder, CircleShape)
                        .background(if (isSelected) CmfOrange else CmfSurfaceSubtle)
                        .clickable { if (!sleepState.isActive) selectedMinutes = mins }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${mins}${AppStrings.minUnit.first()}",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        color = if (isSelected) CmfTextPrimary else CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (AppStrings.isRussian) "Фоновый шум (офлайн):" else "Ambient noise (offline):",
            fontSize = 12.sp,
            color = CmfTextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Noise selector
        RelaxNoiseType.values().forEach { noise ->
            val isNoiseSelected = if (sleepState.isActive) sleepState.noiseType == noise else selectedNoise == noise
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isNoiseSelected) CmfOrangeSubtle else CmfSurfaceSubtle)
                    .border(1.dp, if (isNoiseSelected) CmfOrange else CmfBorder, RoundedCornerShape(12.dp))
                    .clickable { if (!sleepState.isActive) selectedNoise = noise }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = noise.title,
                    fontSize = 12.sp,
                    fontWeight = if (isNoiseSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isNoiseSelected) CmfOrange else CmfTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                if (sleepState.isActive) onStopTimer() else onStartTimer(selectedMinutes, selectedNoise)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (sleepState.isActive) CmfSurfaceSubtle else CmfOrange
            )
        ) {
            Text(
                text = if (sleepState.isActive) (if (AppStrings.isRussian) "ОТМЕНИТЬ ТАЙМЕР" else "CANCEL TIMER") else (if (AppStrings.isRussian) "ЗАПУСТИТЬ ТАЙМЕР СНА" else "START SLEEP TIMER"),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = if (sleepState.isActive) CmfOrange else CmfTextPrimary,
                fontFamily = FontFamily.Default
            )
        }
    }
}
