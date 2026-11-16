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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.tools.DialClickAction
import com.cmf.budscontroller.tools.DialRotateAction
import com.cmf.budscontroller.tools.SmartDialConfig
import com.cmf.budscontroller.ui.theme.*

@Composable
fun CmfSmartDialCard(
    dialConfig: SmartDialConfig,
    onRotateActionChanged: (DialRotateAction) -> Unit,
    onClickActionChanged: (DialClickAction) -> Unit,
    onSimulateRotate: (Boolean) -> Unit,
    onSimulateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current
    val dimens = LocalCmfDimens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCorner))
            .background(CmfGlassCardBrush)
            .border(1.2.dp, CmfGlassBorderBrush, RoundedCornerShape(dimens.cardCorner))
            .padding(dimens.cardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppStrings.headerSmartDial,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = CmfTextSecondary,
                fontFamily = FontFamily.Default
            )
            Text(
                text = "CASE WHEEL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = vibe.accent,
                fontFamily = FontFamily.Default
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Virtual Dial Simulator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CmfSurfaceSubtle)
                .border(1.dp, CmfBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CmfSurface)
                    .border(1.5.dp, CmfBorder, CircleShape)
                    .clickable { onSimulateRotate(false) },
                contentAlignment = Alignment.Center
            ) {
                CmfIconRotateCcw(
                    modifier = Modifier.size(22.dp),
                    tint = vibe.accent
                )
            }

            // Big Central Knob Simulator (VibeCMF Textured Knurled Dial)
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(CmfSurface)
                    .border(2.5.dp, vibe.accent, CircleShape)
                    .clickable { onSimulateClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(vibe.accent)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CLICK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = CmfTextPrimary,
                        fontFamily = FontFamily.Default
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CmfSurface)
                    .border(1.5.dp, CmfBorder, CircleShape)
                    .clickable { onSimulateRotate(true) },
                contentAlignment = Alignment.Center
            ) {
                CmfIconRotateCw(
                    modifier = Modifier.size(22.dp),
                    tint = vibe.accent
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = AppStrings.smartDialRotateHeader,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = CmfTextSecondary,
            fontFamily = FontFamily.Default
        )
        Spacer(modifier = Modifier.height(8.dp))

        DialRotateAction.values().forEach { action ->
            val isSelected = dialConfig.rotateAction == action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) vibe.accentSubtle else CmfSurfaceSubtle)
                    .border(1.dp, if (isSelected) vibe.accent else CmfBorder, RoundedCornerShape(12.dp))
                    .clickable { onRotateActionChanged(action) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = action.title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) vibe.accent else CmfTextPrimary,
                    fontFamily = FontFamily.Default
                )
            }
        }
    }
}
