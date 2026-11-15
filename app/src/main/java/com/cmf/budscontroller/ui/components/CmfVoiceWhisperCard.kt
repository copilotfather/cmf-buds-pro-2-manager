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
            text = "ГОЛОСОВОЙ ШЕПОТ И АССИСТЕНТ",
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
                Text(text = "Озвучивать заряд при подключении", fontSize = 12.sp, color = CmfTextPrimary)
                Text(text = "Голосовое приветствие с текущим % батареи", fontSize = 10.sp, color = CmfTextSecondary)
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
                Text(text = "Озвучивать прогноз оставшихся часов", fontSize = 12.sp, color = CmfTextPrimary)
                Text(text = "Например: «Осталось слушать 5 часов 40 минут»", fontSize = 10.sp, color = CmfTextSecondary)
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
                text = "ПРОТЕСТИРОВАТЬ ГОЛОС",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = CmfOrange,
                fontFamily = FontFamily.Default
            )
        }
    }
}
