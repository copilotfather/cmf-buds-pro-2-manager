package com.cmf.budscontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.tools.DisconnectLocation
import com.cmf.budscontroller.ui.theme.*

@Composable
fun FindMyBudsCard(
    isChirping: Boolean,
    lastLocation: DisconnectLocation?,
    onStartChirp: (left: Boolean, right: Boolean) -> Unit,
    onStopChirp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chirpLeft by remember { mutableStateOf(true) }
    var chirpRight by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CmfSurface)
            .border(1.dp, CmfBorder, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            text = AppStrings.headerSonar,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = CmfTextSecondary,
            fontFamily = FontFamily.Default
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (AppStrings.isRussian) "Подача громкого акустического сигнала через динамики:" else "Play loud acoustic locator tone through earbud drivers:",
            fontSize = 12.sp,
            color = CmfTextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .border(1.dp, if (chirpLeft) CmfOrange else CmfBorder, CircleShape)
                    .background(if (chirpLeft) CmfOrange else CmfSurfaceSubtle)
                    .clickable { chirpLeft = !chirpLeft }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${AppStrings.batteryLeft} (L)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (chirpLeft) CmfTextPrimary else CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .border(1.dp, if (chirpRight) CmfOrange else CmfBorder, CircleShape)
                    .background(if (chirpRight) CmfOrange else CmfSurfaceSubtle)
                    .clickable { chirpRight = !chirpRight }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${AppStrings.batteryRight} (R)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (chirpRight) CmfTextPrimary else CmfTextSecondary,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (isChirping) onStopChirp() else onStartChirp(chirpLeft, chirpRight)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isChirping) CmfGreen else CmfOrange
            )
        ) {
            Text(
                text = if (isChirping) (if (AppStrings.isRussian) "ОСТАНОВИТЬ" else "STOP") else (if (AppStrings.isRussian) "ПОДАТЬ СИГНАЛ" else "PLAY SOUND"),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Default
            )
        }

        if (lastLocation != null) {
            Spacer(modifier = Modifier.height(14.dp))
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
                        text = if (AppStrings.isRussian) "ТОЧКА ОТКЛЮЧЕНИЯ: ${lastLocation.formattedTime}" else "DISCONNECT LOCATION: ${lastLocation.formattedTime}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CmfTextPrimary,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = "GPS: ${lastLocation.latitude}, ${lastLocation.longitude}",
                        fontSize = 10.sp,
                        color = CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val context = LocalContext.current
                    OutlinedButton(
                        onClick = {
                            val uri = Uri.parse("geo:${lastLocation.latitude},${lastLocation.longitude}?q=${lastLocation.latitude},${lastLocation.longitude}(CMF Buds)")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text(if (AppStrings.isRussian) "ОТКРЫТЬ НА КАРТЕ" else "OPEN IN MAPS", fontFamily = FontFamily.Default, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
