package com.mulocoolbot.gymtracker.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingUiState
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.auth.ProfileSetupScreen
import com.mulocoolbot.gymtracker.ui.auth.SignInScreen
import com.mulocoolbot.gymtracker.ui.components.CalendarPlanIcon
import com.mulocoolbot.gymtracker.ui.components.DumbbellIcon
import com.mulocoolbot.gymtracker.ui.components.HomeIcon
import com.mulocoolbot.gymtracker.ui.components.SettingsSlidersIcon
import com.mulocoolbot.gymtracker.ui.components.StatsBarIcon
import com.mulocoolbot.gymtracker.ui.components.SystemPermissionsRequester
import com.mulocoolbot.gymtracker.ui.home.HomeScreen
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanScreen
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.settings.SettingsScreen
import com.mulocoolbot.gymtracker.ui.stats.StatsScreen
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricMedium
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import com.mulocoolbot.gymtracker.ui.workout.WorkoutScreen
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel

@Composable
fun MainScaffold(
    repository: GymRepository,
    timerController: RestTimerController = RestTimerController.instance
) {
    val context = LocalContext.current
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

    SystemPermissionsRequester()

    when (authUiState.destination) {
        AppStartDestination.SIGN_IN -> {
            SignInScreen(
                authStep = authUiState.authStep,
                pendingEmail = authUiState.pendingEmail,
                otpErrorMessage = authUiState.otpErrorMessage,
                resendCountdownSeconds = authUiState.resendCountdownSeconds,
                onGoogleAuthenticated = authViewModel::onGoogleAuthenticated,
                onSendEmailOtp = { email ->
                    authViewModel.requestEmailOtp(context = context, emailInput = email)
                },
                onVerifyEmailOtp = { otpCode ->
                    authViewModel.verifyEmailOtp(otpInput = otpCode)
                },
                onResendEmailOtp = {
                    authViewModel.requestEmailOtp(
                        context = context,
                        emailInput = authUiState.pendingEmail,
                        isResend = true
                    )
                },
                onBackToEmailInput = authViewModel::backToEmailInput,
                onClearError = authViewModel::clearAuthError
            )
        }

        AppStartDestination.PROFILE_SETUP -> {
            val defaultName = authUiState.userProfile?.name
                ?: authUiState.suggestedDisplayName
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
                timerController = timerController,
                authUiState = authUiState,
                authViewModel = authViewModel
            )
        }
    }
}

@Composable
private fun AuthenticatedMainContent(
    repository: GymRepository,
    timerController: RestTimerController,
    authUiState: AuthOnboardingUiState,
    authViewModel: AuthOnboardingViewModel
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val ensureNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (status != PackageManager.PERMISSION_GRANTED) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

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
    val workoutViewModel: WorkoutViewModel = viewModel(
        factory = WorkoutViewModel.factory(repository, timerController)
    )
    val statsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.factory(repository)
    )

    val homeState by homeViewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .systemBarsPadding(),
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
                        runCatching { RestTimerService.stopRestTimer(context) }
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
                    HomeScreen(
                        uiState = homeState,
                        onSelectDay = homeViewModel::selectDay,
                        onStartWorkout = {
                            ensureNotificationPermission()
                            workoutViewModel.startWorkout()
                            navigateToTab(navController, BottomNavTab.WORKOUT)
                        }
                    )
                }

                composable(BottomNavTab.PLAN.route) {
                    val planState by planViewModel.uiState.collectAsState()
                    WorkoutPlanScreen(
                        uiState = planState,
                        onOpenWizard = planViewModel::startCreateOrEditPlan,
                        onToggleDay = planViewModel::toggleDaySelection,
                        onNextStep = planViewModel::goToInputPlanPage,
                        onBackToSelectDays = planViewModel::goToEditDaysPage,
                        onUpdateDraftName = planViewModel::updateDraftPlanName,
                        onRequestCancel = planViewModel::requestCancel,
                        onDismissDiscardDialog = planViewModel::dismissDiscardDialog,
                        onConfirmDiscard = planViewModel::confirmDiscard,
                        onSaveSchedule = planViewModel::saveWorkoutPlan
                    )
                }

                composable(BottomNavTab.WORKOUT.route) {
                    val workoutState by workoutViewModel.uiState.collectAsState()
                    val activePlanName = if (homeState.selectedDayPlan.isRestDay) {
                        workoutState.todayPlanName
                    } else {
                        homeState.selectedDayPlan.planName
                    }

                    WorkoutScreen(
                        uiState = workoutState,
                        onStartWorkout = workoutViewModel::startWorkout,
                        onStopWorkout = {
                            workoutViewModel.stopWorkout(planName = activePlanName) {
                                runCatching { RestTimerService.stopRestTimer(context) }
                            }
                        },
                        onExerciseNameChange = workoutViewModel::onExerciseNameChanged,
                        onWeightChange = workoutViewModel::updateWeightInput,
                        onRepsChange = workoutViewModel::updateRepsInput,
                        onSelectSetType = workoutViewModel::selectSetType,
                        onAddSet = {
                            workoutViewModel.addSet { durationMillis, label ->
                                runCatching {
                                    RestTimerService.startRestTimer(context, durationMillis, label)
                                }
                            }
                        },
                        onSkipOrDismissRest = {
                            workoutViewModel.skipOrDismissRestTimer {
                                runCatching {
                                    RestTimerService.stopRestTimer(context)
                                }
                            }
                        },
                        onSelectExerciseSuggestion = workoutViewModel::selectExerciseSuggestion,
                        onSelectMuscleGroup = workoutViewModel::selectMuscleGroup,
                        onStepWeight = workoutViewModel::stepWeight,
                        onStepReps = workoutViewModel::stepReps,
                        onDeleteSessionSet = workoutViewModel::deleteSessionSet,
                        onAdjustRestTimer = { deltaSeconds ->
                            workoutViewModel.adjustRestTimer(deltaSeconds) { delta ->
                                runCatching {
                                    RestTimerService.adjustRestTimer(context, delta)
                                }
                            }
                        },
                        onRequestFinishWorkout = workoutViewModel::requestFinishWorkout,
                        onDismissFinishDialog = workoutViewModel::dismissFinishDialog,
                        onConfirmFinishWorkout = {
                            workoutViewModel.stopWorkout(planName = activePlanName) {
                                runCatching { RestTimerService.stopRestTimer(context) }
                            }
                        }
                    )
                }

                composable(BottomNavTab.STATS.route) {
                    val statsState by statsViewModel.uiState.collectAsState()
                    StatsScreen(
                        uiState = statsState,
                        onSelectExercise = statsViewModel::selectExercise
                    )
                }

                composable(BottomNavTab.SETTINGS.route) {
                    SettingsScreen(
                        authSession = authUiState.authSession,
                        userProfile = authUiState.userProfile,
                        onUpdateProfile = authViewModel::onUpdateProfile,
                        onToggleGoogleLink = authViewModel::onToggleGoogleLink,
                        onToggleEmailLink = authViewModel::onToggleEmailLink,
                        onExportBackup = authViewModel::exportBackupJson,
                        onImportBackup = authViewModel::importBackupJson
                    )
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
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
