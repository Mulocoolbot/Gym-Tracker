package com.mulocoolbot.gymtracker

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mulocoolbot.gymtracker.resources.Res
import com.mulocoolbot.gymtracker.resources.ic_gymtracker_mark
import com.mulocoolbot.gymtracker.ui.navigation.BottomNavTab
import com.mulocoolbot.gymtracker.ui.navigation.GymBottomBar
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.GymTrackerTheme
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricLarge
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import org.jetbrains.compose.resources.painterResource

@Composable
fun GymTrackerApp() {
    GymTrackerTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: BottomNavTab.HOME.route

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .systemBarsPadding(),
            containerColor = AppBackground,
            bottomBar = {
                GymBottomBar(
                    currentRoute = currentRoute,
                    onSelectTab = { tab -> navigateToShellTab(navController, tab) }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = BottomNavTab.HOME.route,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBackground)
                    .padding(innerPadding),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(140)) }
            ) {
                BottomNavTab.entries.forEach { tab ->
                    composable(tab.route) {
                        TabShellPlaceholder(tab = tab)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabShellPlaceholder(tab: BottomNavTab) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_gymtracker_mark),
                contentDescription = "MFit Logo",
                modifier = Modifier.size(36.dp)
            )
            Column {
                Text(
                    text = "MFit",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoltAccent
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = tab.route.uppercase(),
                    style = MonoMetricLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = shellSummary(tab),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}

private fun shellSummary(tab: BottomNavTab): String = when (tab) {
    BottomNavTab.HOME -> "Daily readiness, weekly strip, and scheduled session."
    BottomNavTab.PLAN -> "Weekly split configuration and training days."
    BottomNavTab.WORKOUT -> "Active workout logger and rest timer."
    BottomNavTab.STATS -> "Estimated 1RM trends and muscle volume."
    BottomNavTab.SETTINGS -> "Profile metrics, account sync, and JSON backup."
}

private fun navigateToShellTab(
    navController: NavHostController,
    tab: BottomNavTab
) {
    navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
