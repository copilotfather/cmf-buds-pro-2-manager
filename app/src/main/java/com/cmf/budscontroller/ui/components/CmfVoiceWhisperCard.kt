package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.tools.WhisperSettings
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfVoiceWhisperCard(
    settings: WhisperSettings,
    onToggleConnectAnnounce: (Boolean) -> Unit,
    onToggleSpeakHours: (Boolean) -> Unit,
    onTestVoice: () -> Unit,
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
            text = AppStrings.voiceWhisperTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = CmfTextSecondary,
            fontFamily = FontFamily.Default
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = if (AppStrings.isRussian) "Озвучивать заряд при подключении" else "Announce battery on connect", fontSize = 12.sp, color = CmfTextPrimary)
                Text(text = if (AppStrings.isRussian) "Голосовое приветствие с текущим % батареи" else "Voice prompt with current battery %", fontSize = 10.sp, color = CmfTextSecondary)
            }
            Switch(
                checked = settings.announceOnConnect,
                onCheckedChange = onToggleConnectAnnounce,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CmfTextPrimary,
                    checkedTrackColor = CmfOrange,
                    uncheckedThumbColor = CmfTextSecondary,
                    uncheckedTrackColor = CmfSurfaceSubtle,
                    uncheckedBorderColor = CmfBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = if (AppStrings.isRussian) "Озвучивать прогноз оставшихся часов" else "Announce remaining listening hours", fontSize = 12.sp, color = CmfTextPrimary)
                Text(text = if (AppStrings.isRussian) "Например: «Осталось слушать 5 часов 40 минут»" else "e.g., 'Remaining time: 5 hours 40 minutes'", fontSize = 10.sp, color = CmfTextSecondary)
            }
            Switch(
                checked = settings.speakRemainingHours,
                onCheckedChange = onToggleSpeakHours,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CmfTextPrimary,
                    checkedTrackColor = CmfOrange,
                    uncheckedThumbColor = CmfTextSecondary,
                    uncheckedTrackColor = CmfSurfaceSubtle,
                    uncheckedBorderColor = CmfBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onTestVoice,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = CmfSurfaceSubtle)
        ) {
            Text(
                text = if (AppStrings.isRussian) "ПРОТЕСТИРОВАТЬ ГОЛОС" else "TEST VOICE PROMPT",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = CmfOrange,
                fontFamily = FontFamily.Default
            )
        }
    }
}
