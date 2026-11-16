package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.tools.WaterEjectState
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfWaterEjectCard(
    ejectState: WaterEjectState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CmfSurface)
            .border(1.dp, CmfBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            text = AppStrings.waterEjectTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = CmfTextSecondary,
            fontFamily = FontFamily.Default
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (AppStrings.isRussian) "Генерирует резонансную звуковую волну (140–180 Гц), механически раскачивающую 11-мм диафрагму для удаления капель воды и пыли из сетки динамиков:" else "Generates resonant acoustic waves (140–180 Hz) to kinetically displace water droplets and dust from speaker meshes:",
            fontSize = 12.sp,
            color = CmfTextPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (ejectState.isRunning) {
            val fraction = ejectState.progressSeconds.toFloat() / ejectState.totalSeconds
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CmfOrange,
                trackColor = CmfSurfaceSubtle
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (AppStrings.isRussian) "ИДЕТ ОЧИСТКА ДИАФРАГМЫ..." else "EJECTING WATER...",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CmfOrange,
                    fontFamily = FontFamily.Default
                )
                Text(
                    text = "${ejectState.totalSeconds - ejectState.progressSeconds} ${if (AppStrings.isRussian) "сек" else "s"}",
                    fontSize = 11.sp,
                    color = CmfTextPrimary,
                    fontFamily = FontFamily.Default
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Button(
            onClick = { if (ejectState.isRunning) onStop() else onStart() },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (ejectState.isRunning) CmfSurfaceSubtle else CmfOrange
            )
        ) {
            Text(
                text = if (ejectState.isRunning) (if (AppStrings.isRussian) "ПРЕРВАТЬ ОЧИСТКУ" else "STOP EJECTION") else (if (AppStrings.isRussian) "ЗАПУСТИТЬ ОЧИСТКУ ДИНАМИКОВ" else "START WATER EJECTION"),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = if (ejectState.isRunning) CmfOrange else CmfTextPrimary,
                fontFamily = FontFamily.Default
            )
        }
    }
}
