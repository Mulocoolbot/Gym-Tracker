package com.mulocoolbot.gymtracker.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mulocoolbot.gymtracker.platform.PlatformPermissionsRequester
import com.mulocoolbot.gymtracker.platform.createPlatformRestTimerBridge
import com.mulocoolbot.gymtracker.platform.rememberNotificationPermissionRequester
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingUiState
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.auth.ProfileSetupScreen
import com.mulocoolbot.gymtracker.ui.auth.SignInScreen
import com.mulocoolbot.gymtracker.ui.auth.WelcomeScreen
import com.mulocoolbot.gymtracker.ui.home.HomeScreen
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.intro.MFitOpeningIntro
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanScreen
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.settings.SettingsScreen
import com.mulocoolbot.gymtracker.ui.stats.StatsScreen
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricMedium
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import com.mulocoolbot.gymtracker.ui.workout.WorkoutScreen
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel

@Composable
fun MainScaffold(
    repository: GymRepository,
    timerController: RestTimerController = RestTimerController.instance
) {
    val authViewModel: AuthOnboardingViewModel = viewModel(
        factory = AuthOnboardingViewModel.factory(repository)
    )
    val authUiState by authViewModel.uiState.collectAsState()

    if (authUiState.isOpeningPlaying) {
        MFitOpeningIntro(
            onOpeningFinished = { authViewModel.onOpeningAnimationFinished() }
        )
        return
    }

    if (authUiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
        )
        return
    }

    PlatformPermissionsRequester()

    Crossfade(
        targetState = authUiState.destination,
        animationSpec = tween(260),
        label = "AuthStartDestinationCrossfade"
    ) { destination ->
        when (destination) {
            AppStartDestination.WELCOME -> {
                WelcomeScreen(
                    onBeginClick = { authViewModel.onBeginFromWelcome() }
                )
            }

            AppStartDestination.SIGN_IN -> {
                SignInScreen(
                    authStep = authUiState.authStep,
                    pendingEmail = authUiState.pendingEmail,
                    otpErrorMessage = authUiState.otpErrorMessage,
                    resendCountdownSeconds = authUiState.resendCountdownSeconds,
                    onGoogleAuthenticated = authViewModel::onGoogleAuthenticated,
                    onSendEmailOtp = { email ->
                        authViewModel.requestEmailOtp(emailInput = email)
                    },
                    onVerifyEmailOtp = { otpCode ->
                        authViewModel.verifyEmailOtp(otpInput = otpCode)
                    },
                    onResendEmailOtp = {
                        authViewModel.requestEmailOtp(
                            emailInput = authUiState.pendingEmail,
                            isResend = true
                        )
                    },
                    onBackToEmailInput = authViewModel::backToEmailInput,
                    onClearError = authViewModel::clearAuthError,
                    authEntryMode = authUiState.authEntryMode,
                    onSwitchAuthMode = authViewModel::setAuthEntryMode,
                    onBackToWelcome = authViewModel::onBackToWelcome,
                    onAuthError = authViewModel::setAuthErrorMessage,
                    onContinueOffline = authViewModel::onContinueOffline
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
}

@Composable
private fun AuthenticatedMainContent(
    repository: GymRepository,
    timerController: RestTimerController,
    authUiState: AuthOnboardingUiState,
    authViewModel: AuthOnboardingViewModel
) {
    val ensureNotificationPermission = rememberNotificationPermissionRequester()
    val timerBridge = remember { createPlatformRestTimerBridge() }

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
            .background(AppBackground),
        containerColor = AppBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                .consumeWindowInsets(innerPadding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
        ) {
            if (timerState.alarmRinging) {
                AlarmFinishedBanner(
                    onDismiss = {
                        timerBridge.stopBackgroundTimer()
                        timerController.dismissOrSkip()
                    }
                )
            }

            NavHost(
                navController = navController,
                startDestination = BottomNavTab.HOME.route,
                modifier = Modifier.weight(1f),
                enterTransition = {
                    fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 18 }
                },
                exitTransition = {
                    fadeOut(tween(150))
                },
                popEnterTransition = {
                    fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 18 }
                },
                popExitTransition = {
                    fadeOut(tween(150))
                }
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
                                timerBridge.stopBackgroundTimer()
                            }
                        },
                        onExerciseNameChange = workoutViewModel::onExerciseNameChanged,
                        onWeightChange = workoutViewModel::updateWeightInput,
                        onRepsChange = workoutViewModel::updateRepsInput,
                        onSelectSetType = workoutViewModel::selectSetType,
                        onAddSet = {
                            workoutViewModel.addSet { durationMillis, label ->
                                timerBridge.startBackgroundTimer(durationMillis, label)
                            }
                        },
                        onSkipOrDismissRest = {
                            workoutViewModel.skipOrDismissRestTimer {
                                timerBridge.stopBackgroundTimer()
                            }
                        },
                        onSelectExerciseSuggestion = workoutViewModel::selectExerciseSuggestion,
                        onSelectMuscleGroup = workoutViewModel::selectMuscleGroup,
                        onStepWeight = workoutViewModel::stepWeight,
                        onStepReps = workoutViewModel::stepReps,
                        onDeleteSessionSet = workoutViewModel::deleteSessionSet,
                        onAdjustRestTimer = { deltaSeconds ->
                            workoutViewModel.adjustRestTimer(deltaSeconds) { delta ->
                                timerBridge.adjustBackgroundTimer(delta)
                            }
                        },
                        onRequestFinishWorkout = workoutViewModel::requestFinishWorkout,
                        onDismissFinishDialog = workoutViewModel::dismissFinishDialog,
                        onConfirmFinishWorkout = {
                            workoutViewModel.stopWorkout(planName = activePlanName) {
                                timerBridge.stopBackgroundTimer()
                            }
                        }
                    )
                }

                composable(BottomNavTab.STATS.route) {
                    val statsState by statsViewModel.uiState.collectAsState()
                    StatsScreen(
                        uiState = statsState,
                        onSelectExercise = statsViewModel::selectExercise,
                        onToggleExerciseDropdown = statsViewModel::toggleExerciseDropdown,
                        onDismissExerciseDropdown = statsViewModel::dismissExerciseDropdown,
                        onSelectChartPoint = statsViewModel::selectChartPoint,
                        onSwitchHeatmapOrientation = statsViewModel::switchHeatmapOrientation,
                        onSelectMuscleRegion = statsViewModel::selectMuscleRegion,
                        onNavigateToLogWorkout = {
                            ensureNotificationPermission()
                            workoutViewModel.startWorkout()
                            navigateToTab(navController, BottomNavTab.WORKOUT)
                        }
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
