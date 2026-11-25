package com.mulocoolbot.gymtracker.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mulocoolbot.gymtracker.ui.components.CalendarPlanIcon
import com.mulocoolbot.gymtracker.ui.components.DumbbellIcon
import com.mulocoolbot.gymtracker.ui.components.HomeIcon
import com.mulocoolbot.gymtracker.ui.components.SettingsSlidersIcon
import com.mulocoolbot.gymtracker.ui.components.StatsBarIcon
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent

@Composable
fun GymBottomBar(
    currentRoute: String,
    onSelectTab: (BottomNavTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(36.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val isSelected = currentRoute == tab.route
                    when (tab) {
                        BottomNavTab.HOME -> StandardTabItem(
                            icon = HomeIcon,
                            contentDescription = tab.label,
                            isSelected = isSelected,
                            onClick = { onSelectTab(tab) }
                        )
                        BottomNavTab.PLAN -> StandardTabItem(
                            icon = CalendarPlanIcon,
                            contentDescription = tab.label,
                            isSelected = isSelected,
                            onClick = { onSelectTab(tab) }
                        )
                        BottomNavTab.WORKOUT -> WorkoutCenterTabButton(
                            onClick = { onSelectTab(tab) }
                        )
                        BottomNavTab.STATS -> StandardTabItem(
                            icon = StatsBarIcon,
                            contentDescription = tab.label,
                            isSelected = isSelected,
                            onClick = { onSelectTab(tab) }
                        )
                        BottomNavTab.SETTINGS -> StandardTabItem(
                            icon = SettingsSlidersIcon,
                            contentDescription = tab.label,
                            isSelected = isSelected,
                            onClick = { onSelectTab(tab) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardTabItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF232733) else Color.Transparent
    val iconTint = if (isSelected) VoltAccent else TextMuted

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun WorkoutCenterTabButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(VoltAccent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = DumbbellIcon,
            contentDescription = BottomNavTab.WORKOUT.label,
            tint = OnVolt,
            modifier = Modifier.size(24.dp)
        )
    }
}
