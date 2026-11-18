package com.cmf.budscontroller.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
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
    onClearLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalCmfDimens.current
    var chirpLeft by remember { mutableStateOf(true) }
    var chirpRight by remember { mutableStateOf(true) }
    var confirmClearLocation by remember { mutableStateOf(false) }
    val chirpButtonColor by animateColorAsState(
        targetValue = if (isChirping) CmfGreen else CmfOrange,
        animationSpec = tween(220),
        label = "chirp_button_color"
    )
    val leftBackground by animateColorAsState(
        targetValue = if (chirpLeft) CmfOrange else CmfSurfaceSubtle,
        animationSpec = tween(180),
        label = "left_chirp_background"
    )
    val rightBackground by animateColorAsState(
        targetValue = if (chirpRight) CmfOrange else CmfSurfaceSubtle,
        animationSpec = tween(180),
        label = "right_chirp_background"
    )
    val leftTextColor by animateColorAsState(
        targetValue = if (chirpLeft) CmfTextPrimary else CmfTextSecondary,
        animationSpec = tween(180),
        label = "left_chirp_text"
    )
    val rightTextColor by animateColorAsState(
        targetValue = if (chirpRight) CmfTextPrimary else CmfTextSecondary,
        animationSpec = tween(180),
        label = "right_chirp_text"
    )

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
            val leftInteraction = remember { MutableInteractionSource() }
            val leftPressed by leftInteraction.collectIsPressedAsState()
            val leftScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (leftPressed) 0.96f else 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.74f, stiffness = 560f),
                label = "left_chirp_press"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = dimens.controlTouchTarget)
                    .scale(leftScale)
                    .clip(CircleShape)
                    .border(1.dp, if (chirpLeft) CmfOrange else CmfBorder, CircleShape)
                    .background(leftBackground)
                    .clickable(interactionSource = leftInteraction, indication = null) { chirpLeft = !chirpLeft }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${AppStrings.batteryLeft} (L)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = leftTextColor,
                    fontFamily = FontFamily.Default
                )
            }

            val rightInteraction = remember { MutableInteractionSource() }
            val rightPressed by rightInteraction.collectIsPressedAsState()
            val rightScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (rightPressed) 0.96f else 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.74f, stiffness = 560f),
                label = "right_chirp_press"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = dimens.controlTouchTarget)
                    .scale(rightScale)
                    .clip(CircleShape)
                    .border(1.dp, if (chirpRight) CmfOrange else CmfBorder, CircleShape)
                    .background(rightBackground)
                    .clickable(interactionSource = rightInteraction, indication = null) { chirpRight = !chirpRight }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${AppStrings.batteryRight} (R)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = rightTextColor,
                    fontFamily = FontFamily.Default
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = isChirping || chirpLeft || chirpRight,
            onClick = {
                if (isChirping) onStopChirp() else onStartChirp(chirpLeft, chirpRight)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = chirpButtonColor
            )
        ) {
            Text(
                text = if (isChirping) (if (AppStrings.isRussian) "ОСТАНОВИТЬ" else "STOP") else (if (AppStrings.isRussian) "ПОДАТЬ СИГНАЛ" else "PLAY SOUND"),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Default
            )
        }

        AnimatedVisibility(
            visible = lastLocation != null,
            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
            label = "last_location_visibility"
        ) {
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
                        text = if (AppStrings.isRussian) "ПОСЛЕДНЯЯ ИЗВЕСТНАЯ ПОЗИЦИЯ ТЕЛЕФОНА: ${lastLocation?.formattedTime.orEmpty()}" else "LAST KNOWN PHONE LOCATION: ${lastLocation?.formattedTime.orEmpty()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CmfTextPrimary,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = "GPS: ${lastLocation?.latitude}, ${lastLocation?.longitude}",
                        fontSize = 10.sp,
                        color = CmfTextSecondary,
                        fontFamily = FontFamily.Default
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val context = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                lastLocation?.let { location ->
                                    val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(CMF Buds)")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) {
                            Text(if (AppStrings.isRussian) "ОТКРЫТЬ НА КАРТЕ" else "OPEN IN MAPS", fontFamily = FontFamily.Default, fontSize = 11.sp)
                        }
                        TextButton(onClick = { confirmClearLocation = true }) {
                            Text(if (AppStrings.isRussian) "УДАЛИТЬ" else "DELETE", fontFamily = FontFamily.Default, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmClearLocation) {
        AlertDialog(
            onDismissRequest = { confirmClearLocation = false },
            title = { Text(if (AppStrings.isRussian) "Удалить позицию?" else "Delete saved location?") },
            text = { Text(if (AppStrings.isRussian) "Сохранённые координаты телефона будут удалены." else "The saved phone coordinates will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearLocation()
                    confirmClearLocation = false
                }) { Text(if (AppStrings.isRussian) "Удалить" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLocation = false }) { Text(AppStrings.btnCancel) }
            }
        )
    }
}
