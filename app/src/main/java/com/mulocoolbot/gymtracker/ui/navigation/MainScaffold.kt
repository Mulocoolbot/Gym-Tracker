package com.mulocoolbot.gymtracker.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.RestTimerService
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.auth.ProfileSetupScreen
import com.mulocoolbot.gymtracker.ui.auth.SignInScreen
import com.mulocoolbot.gymtracker.ui.components.DumbbellIcon
import com.mulocoolbot.gymtracker.ui.home.HomeScreen
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanScreen
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricMedium
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent

@Composable
fun MainScaffold(
    repository: GymRepository,
    timerController: RestTimerController = RestTimerController.instance
) {
    val authViewModel: AuthOnboardingViewModel = viewModel(
        factory = AuthOnboardingViewModel.factory(repository)
    )
    val authUiState by authViewModel.uiState.collectAsState()

    if (authUiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
        )
        return
    }

    when (authUiState.destination) {
        AppStartDestination.SIGN_IN -> {
            SignInScreen(
                onGoogleSignIn = { authViewModel.onGoogleSignIn("athlete@gmail.com") },
                onEmailSignIn = authViewModel::onEmailSignIn
            )
        }
        AppStartDestination.PROFILE_SETUP -> {
            val defaultName = authUiState.userProfile?.name
                ?: authUiState.authSession.userEmail?.substringBefore("@")
                ?: authUiState.authSession.googleEmail?.substringBefore("@")
                ?: ""
            ProfileSetupScreen(
                initialName = defaultName,
                onCompleteProfile = authViewModel::onSubmitProfile
            )
        }
        AppStartDestination.MAIN_HOME -> {
            AuthenticatedMainContent(
                repository = repository,
                timerController = timerController
            )
        }
    }
}

@Composable
private fun AuthenticatedMainContent(
    repository: GymRepository,
    timerController: RestTimerController
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: BottomNavTab.HOME.route
    val timerState by timerController.state.collectAsState()

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(repository)
    )
    val planViewModel: WorkoutPlanViewModel = viewModel(
        factory = WorkoutPlanViewModel.factory(repository)
    )

    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            GymBottomBar(
                currentRoute = currentRoute,
                onSelectTab = { tab -> navigateToTab(navController, tab) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(innerPadding)
        ) {
            if (timerState.alarmRinging) {
                AlarmFinishedBanner(
                    onDismiss = {
                        runCatching { RestTimerService.dismissAlarm(context) }
                        timerController.dismissOrSkip()
                    }
                )
            }

            NavHost(
                navController = navController,
                startDestination = BottomNavTab.HOME.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(BottomNavTab.HOME.route) {
                    val homeState by homeViewModel.uiState.collectAsState()
                    HomeScreen(
                        uiState = homeState,
                        onSelectDay = homeViewModel::selectDay,
                        onStartWorkout = { navigateToTab(navController, BottomNavTab.WORKOUT) }
                    )
                }

                composable(BottomNavTab.PLAN.route) {
                    val planState by planViewModel.uiState.collectAsState()
                    WorkoutPlanScreen(
                        uiState = planState,
                        onOpenWizard = planViewModel::openWizard,
                        onToggleDay = planViewModel::toggleDaySelection,
                        onNextStep = planViewModel::proceedToScheduleStep,
                        onBackToSelectDays = planViewModel::returnToSelectDaysStep,
                        onUpdateDraftName = planViewModel::updateDraftPlanName,
                        onRequestCancel = planViewModel::requestCancelWizard,
                        onDismissDiscardDialog = planViewModel::dismissDiscardDialog,
                        onConfirmDiscard = planViewModel::confirmDiscardWizard,
                        onSaveSchedule = planViewModel::saveSchedule
                    )
                }

                composable(BottomNavTab.WORKOUT.route) {
                    PlaceholderTabScreen(title = "Workout")
                }

                composable(BottomNavTab.STATS.route) {
                    PlaceholderTabScreen(title = "Stats")
                }

                composable(BottomNavTab.SETTINGS.route) {
                    PlaceholderTabScreen(title = "Settings")
                }
            }
        }
    }
}

@Composable
private fun AlarmFinishedBanner(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, VoltAccent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Rest Finished",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "00:00",
                    style = MonoMetricMedium,
                    color = VoltAccent
                )
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltAccent,
                    contentColor = OnVolt
                )
            ) {
                Text(
                    text = "Dismiss",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnVolt,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GymBottomBar(
    currentRoute: String,
    onSelectTab: (BottomNavTab) -> Unit
) {
    Surface(
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.entries.forEach { tab ->
                val isSelected = currentRoute == tab.route
                if (tab == BottomNavTab.WORKOUT) {
                    WorkoutCenterTabButton(
                        isSelected = isSelected,
                        onClick = { onSelectTab(tab) }
                    )
                } else {
                    StandardTabItem(
                        label = tab.label,
                        isSelected = isSelected,
                        onClick = { onSelectTab(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) VoltAccent else TextMuted,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun WorkoutCenterTabButton(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) VoltAccent else InputSurface
    val contentColor = if (isSelected) OnVolt else VoltAccent

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (isSelected) VoltAccent else CardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = DumbbellIcon,
            contentDescription = BottomNavTab.WORKOUT.label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlaceholderTabScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
    }
}

private fun navigateToTab(
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

