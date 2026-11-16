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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cmf.budscontroller.i18n.AppStrings
import com.cmf.budscontroller.ui.theme.*

enum class NothingXTab {
    DEVICE,
    EQUALIZER,
    PC_BRIDGE,
    FEATURES;

    val title: String
        get() = when (this) {
            DEVICE -> if (AppStrings.isRussian) "ДЕВАЙС" else "DEVICE"
            EQUALIZER -> if (AppStrings.isRussian) "ЗВУК" else "SOUND"
            PC_BRIDGE -> if (AppStrings.isRussian) "МОСТ" else "BRIDGE"
            FEATURES -> if (AppStrings.isRussian) "ФУНКЦИИ" else "FEATURES"
        }
}

private val bottomNavShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

private val bottomNavBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xF018181D),
        Color(0xF80E0E12)
    )
)

private val bottomNavBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0x35FFFFFF),
        Color(0x0EFFFFFF)
    )
)

@Composable
fun CmfBottomNav(
    activeTab: NothingXTab,
    onTabSelected: (NothingXTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val vibe = LocalVibeTheme.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val isSmall = config.screenWidthDp < 360
    val iconSize = if (isSmall) 18.dp else 21.dp
    val textSp = if (isSmall) 9.sp else 10.sp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(bottomNavShape)
            .background(bottomNavBackgroundBrush)
            .border(
                width = 1.dp,
                brush = bottomNavBorderBrush,
                shape = bottomNavShape
            )
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = if (isSmall) 6.dp else 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NothingXTab.values().forEach { tab ->
            val isSelected = activeTab == tab
            val tabTint = if (isSelected) vibe.accent else CmfTextSecondary

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Vector Tab Icon
                when (tab) {
                    NothingXTab.DEVICE -> CmfIconNavDevice(modifier = Modifier.size(iconSize), tint = tabTint)
                    NothingXTab.EQUALIZER -> CmfIconNavEqualizer(modifier = Modifier.size(iconSize), tint = tabTint)
                    NothingXTab.PC_BRIDGE -> CmfIconNavBridge(modifier = Modifier.size(iconSize), tint = tabTint)
                    NothingXTab.FEATURES -> CmfIconNavTools(modifier = Modifier.size(iconSize), tint = tabTint)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = tab.title,
                    fontSize = textSp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    color = tabTint,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    fontFamily = FontFamily.Default
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Active glowing LED dot indicator
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) vibe.accent else Color.Transparent)
                )
            }
        }
    }
}
