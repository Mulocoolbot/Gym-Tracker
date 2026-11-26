package com.mulocoolbot.gymtracker.blackbox

import androidx.compose.ui.text.font.FontFamily
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.intro.MFitOpeningIntroTimeline
import com.mulocoolbot.gymtracker.ui.navigation.BottomNavTab
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanSubPage
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.stats.ChartPointUi
import com.mulocoolbot.gymtracker.ui.stats.HeatmapOrientation
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.stats.buildAnatomicalRegions
import com.mulocoolbot.gymtracker.ui.stats.computeChartNodeOffsets
import com.mulocoolbot.gymtracker.ui.stats.computeVisibleChartLabelIndices
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricLarge
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricMedium
import com.mulocoolbot.gymtracker.ui.theme.MonoTimerDisplay
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import com.mulocoolbot.gymtracker.util.parseDecimalInput
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class Sprint4BlackBoxTestSuite {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createEnvironment(
        scope: CoroutineScope,
        prefsName: String
    ): Pair<TestInMemoryGymDao, GymRepository> {
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = { createTestPreferencesPath(prefsName).toPath() }
        )
        val dao = TestInMemoryGymDao()
        val repository = GymRepository(
            dao = dao,
            sessionPreferences = SessionPreferences(dataStore)
        )
        return dao to repository
    }

    @Test
    fun tc_s4_01_opening_intro_animation_timeline_and_routing() = runTest(testDispatcher) {
        val cracks = MFitOpeningIntroTimeline.buildDeterministicCracks()
        val sparks = MFitOpeningIntroTimeline.buildDeterministicSparks()
        assertEquals(12, cracks.size)
        assertEquals(14, sparks.size)
        assertTrue(cracks.all { it.normalizedPoints.size >= 4 && it.strokeWidthDp > 0f })

        val sampleTimes = listOf(0L, 120L, 420L, 640L, 1080L, 1220L, 1650L, 1750L, 2250L, 2850L)
        sampleTimes.forEach { t ->
            val shake = MFitOpeningIntroTimeline.computeCameraShakeOffset(t)
            assertTrue(shake.x.isFinite() && shake.y.isFinite())
            assertTrue(MFitOpeningIntroTimeline.computeRodProgress(t) in 0f..1f)
            assertTrue(MFitOpeningIntroTimeline.computeLevitationProgress(t) in 0f..1f)
            assertTrue(MFitOpeningIntroTimeline.computeTextRevealProgress(t) in 0f..1f)
            assertTrue(MFitOpeningIntroTimeline.computeCrackAlpha(t) in 0f..1f)
        }

        assertEquals(0f, MFitOpeningIntroTimeline.computeGravityDropProgress(120L, 120L, 420L), 0.001f)
        assertTrue(MFitOpeningIntroTimeline.computeGravityDropProgress(270L, 120L, 420L) in 0.2f..0.3f)
        assertEquals(1f, MFitOpeningIntroTimeline.computeGravityDropProgress(420L, 120L, 420L), 0.001f)
        assertEquals(0f, MFitOpeningIntroTimeline.computeCrackAlpha(2850L), 0.001f)
        assertEquals(1f, MFitOpeningIntroTimeline.computeTextRevealProgress(2850L), 0.001f)

        val (_, freshRepo) = createEnvironment(backgroundScope, "s4_01_fresh.preferences_pb")
        val freshVm = AuthOnboardingViewModel(freshRepo)
        assertTrue(freshVm.uiState.value.isOpeningPlaying)
        freshVm.onOpeningAnimationFinished()
        assertFalse(freshVm.uiState.value.isOpeningPlaying)
        assertEquals(AppStartDestination.WELCOME, freshVm.uiState.value.destination)

        val (_, authRepo) = createEnvironment(backgroundScope, "s4_01_auth.preferences_pb")
        authRepo.signInWithEmail("alex@mfit.app", AuthEntryMode.SIGN_IN)
        authRepo.completeOnboardingProfile("Alex", 946684800000L, 74.0, 178.0)
        val authVm = AuthOnboardingViewModel(authRepo)
        assertTrue(authVm.uiState.value.isOpeningPlaying)
        authVm.onOpeningAnimationFinished()
        assertFalse(authVm.uiState.value.isOpeningPlaying)
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)
    }

    @Test
    fun tc_s4_02_plan_and_workout_flow_with_numeric_inputs() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s4_02_flow.preferences_pb")
        repository.signInWithEmail("alex@mfit.app", AuthEntryMode.SIGN_IN)
        repository.completeOnboardingProfile("Alex", 946684800000L, 75.0, 180.0)

        val mondayDate = LocalDate(2026, 11, 2)
        val planVm = WorkoutPlanViewModel(repository)
        val homeVm = HomeViewModel(repository = repository, currentDate = mondayDate)
        val timerController = RestTimerController()
        val workoutVm = WorkoutViewModel(
            repository = repository,
            timerController = timerController,
            todayEpochDay = mondayDate.toEpochDays().toLong()
        )

        assertEquals(WorkoutPlanSubPage.EMPTY, planVm.uiState.value.subPage)
        planVm.startCreateOrEditPlan()
        assertEquals(WorkoutPlanSubPage.SELECT_DAYS, planVm.uiState.value.subPage)

        planVm.toggleDaySelection(1)
        planVm.toggleDaySelection(3)
        planVm.goToInputPlanPage()
        assertEquals(WorkoutPlanSubPage.INPUT_PLAN, planVm.uiState.value.subPage)

        planVm.updateDraftPlanName(1, "Pull Day")
        planVm.updateDraftPlanName(3, "Push Day")
        planVm.saveWorkoutPlan()

        assertEquals(WorkoutPlanSubPage.SAVED, planVm.uiState.value.subPage)
        assertEquals("Pull Day", homeVm.uiState.value.selectedDayPlan.planName)
        assertFalse(homeVm.uiState.value.selectedDayPlan.isRestDay)

        val triggeredDurations = mutableListOf<Long>()
        workoutVm.startWorkout(currentMillis = 1_000_000L)
        assertTrue(workoutVm.uiState.value.isWorkoutActive)

        workoutVm.onExerciseNameChanged("  lat   pulldown ")
        workoutVm.selectMuscleGroup("LATS")
        workoutVm.updateWeightInput("45")
        workoutVm.updateRepsInput("12")
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        workoutVm.addSet { duration, _ -> triggeredDurations.add(duration) }
        assertEquals(60_000L, triggeredDurations.last())
        assertEquals("01:00", RestTimerController.formatMMSS(timerController.state.value.totalSeconds))

        workoutVm.updateWeightInput("62,5")
        workoutVm.updateRepsInput("8")
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        workoutVm.addSet { duration, _ -> triggeredDurations.add(duration) }
        assertEquals(120_000L, triggeredDurations.last())
        assertEquals(62.5, workoutVm.uiState.value.activeSessionSets.last().weightKg, 0.001)
        assertEquals("Set 1", workoutVm.uiState.value.activeSessionSets.last().setLabel)

        workoutVm.adjustRestTimer(-15)
        assertEquals(105, timerController.state.value.remainingSeconds)
        workoutVm.adjustRestTimer(15)
        assertEquals(120, timerController.state.value.remainingSeconds)

        workoutVm.stepWeight(2.5)
        assertEquals("65", workoutVm.uiState.value.weightInput)
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_FINAL)
        workoutVm.addSet { duration, _ -> triggeredDurations.add(duration) }
        assertEquals(180_000L, triggeredDurations.last())
        assertEquals("Final Set", workoutVm.uiState.value.activeSessionSets.last().setLabel)

        workoutVm.stopWorkout(planName = homeVm.uiState.value.selectedDayPlan.planName)

        assertFalse(workoutVm.uiState.value.isWorkoutActive)
        assertFalse(timerController.state.value.isRunning)
        assertEquals(3, workoutVm.uiState.value.todayCompletedSets.size)
        assertTrue(homeVm.uiState.value.todayCalories > 0)
        assertEquals(1, homeVm.uiState.value.currentStreak)
    }

    @Test
    fun tc_s4_03_muscle_heatmap_and_e1rm_chart_iphone_proportions() = runTest(testDispatcher) {
        assertEquals(0xFF0B0C0EUL, AppBackground.value shr 32)
        assertEquals(0xFF13151BUL, CardSurface.value shr 32)
        assertEquals(0xFF222530UL, CardBorder.value shr 32)
        assertEquals(0xFFD4FF00UL, VoltAccent.value shr 32)
        assertEquals(FontFamily.Monospace, MonoMetricLarge.fontFamily)
        assertEquals(FontFamily.Monospace, MonoMetricMedium.fontFamily)
        assertEquals(FontFamily.Monospace, MonoTimerDisplay.fontFamily)

        val (_, repository) = createEnvironment(backgroundScope, "s4_03_stats.preferences_pb")
        val sets = (0 until 10).map { i ->
            WorkoutSetEntity(
                sessionId = 0L,
                exerciseName = "Bench Press",
                setType = "NORMAL",
                setOrder = i + 1,
                weightKg = 60.0 + i * 2.5,
                reps = 8,
                e1rm = 0.0,
                timestampMillis = 1_700_000_000_000L + i * 86_400_000L
            )
        }
        repository.recordWorkoutSession(
            planName = "Push Day",
            startMillis = 1_000L,
            endMillis = 1_801_000L,
            sets = sets,
            todayEpochDay = 20760L,
            muscleGroupsByExercise = mapOf("Bench Press" to "CHEST")
        )

        val statsVm = StatsViewModel(repository)
        val statsState = statsVm.uiState.value
        assertTrue(statsState.hasStatisticsData)
        assertEquals("Bench Press", statsState.selectedExerciseName)
        assertEquals(10, statsState.selectedExerciseOption!!.points.size)
        assertEquals(10, statsState.muscleVolumes["CHEST"]?.weeklySets)

        statsVm.switchHeatmapOrientation(HeatmapOrientation.BACK)
        assertEquals("LATS", statsVm.uiState.value.selectedMuscleId)
        statsVm.selectMuscleRegion("TRAPS")
        assertEquals("TRAPS", statsVm.uiState.value.selectedMuscleId)

        val iphoneCanvasSizes = listOf(
            263f to 228f, // iPhone SE (375pt width)
            281f to 228f, // iPhone 15 / 16 Pro (393pt width)
            318f to 228f  // iPhone 16 Pro Max (430pt width)
        )
        iphoneCanvasSizes.forEach { (w, h) ->
            val centerX = w / 2f
            HeatmapOrientation.entries.forEach { orientation ->
                val regions = buildAnatomicalRegions(orientation, w, h)
                assertTrue(regions.isNotEmpty())
                regions.forEach { (_, rect) ->
                    assertTrue(rect.width > 0f && rect.height > 0f)
                    assertTrue(rect.left >= 0f && rect.right <= w)
                    assertTrue(rect.top >= 0f && rect.bottom <= h)
                    assertTrue(abs(rect.center.x - centerX) <= w * 0.25f)
                }
            }
        }

        val sePoints = statsState.selectedExerciseOption!!.points
        val offsets = computeChartNodeOffsets(
            points = sePoints,
            width = 271f,
            height = 196f,
            horizontalPadding = 28f,
            topPadding = 28f,
            bottomPadding = 26f
        )
        assertEquals(10, offsets.size)
        offsets.forEach { pt ->
            assertTrue(pt.x in 28f..243f)
            assertTrue(pt.y in 28f..170f)
        }

        val visibleOnSe = computeVisibleChartLabelIndices(
            pointCount = 10,
            selectedIndex = 4,
            chartWidthPx = 215f,
            minSpacingPx = 44f
        )
        assertTrue(0 in visibleOnSe)
        assertTrue(9 in visibleOnSe)
        assertTrue(4 in visibleOnSe)
        assertTrue(visibleOnSe.size < 10)

        val visibleFourPoints = computeVisibleChartLabelIndices(
            pointCount = 4,
            selectedIndex = 3,
            chartWidthPx = 215f,
            minSpacingPx = 44f
        )
        assertEquals(setOf(0, 1, 2, 3), visibleFourPoints)
    }

    @Test
    fun tc_s4_04_safe_area_and_keyboard_insets_math() {
        assertEquals(62.5, parseDecimalInput("62,5")!!, 0.001)
        assertEquals(62.5, parseDecimalInput(" 62.5 ")!!, 0.001)
        assertEquals(175.0, parseDecimalInput("175")!!, 0.001)

        val devices = listOf(
            Triple("iPhone SE", 667f, 20f to 0f),
            Triple("iPhone 15 Pro", 852f, 59f to 34f),
            Triple("iPhone 16 Pro Max", 932f, 59f to 34f)
        )
        val bottomBarVisualHeight = 72f + 24f
        val keyboardHeight = 300f

        devices.forEach { (_, screenHeight, insets) ->
            val (topSafe, bottomSafe) = insets
            val bottomBarTotal = bottomBarVisualHeight + bottomSafe
            val remainingKeyboardAboveBottomBar = (keyboardHeight - bottomBarTotal).coerceAtLeast(0f)
            val usableContentHeight = screenHeight - topSafe - bottomBarTotal - remainingKeyboardAboveBottomBar
            assertTrue(usableContentHeight >= 280f)
        }
    }

    @Test
    fun tc_s4_05_android_v2_regression_parity() = runTest(testDispatcher) {
        assertEquals(
            listOf("home", "plan", "workout", "stats", "settings"),
            BottomNavTab.entries.map { it.route }
        )
        assertEquals("01:00", RestTimerController.formatMMSS(60))
        assertEquals("02:00", RestTimerController.formatMMSS(120))
        assertEquals("03:00", RestTimerController.formatMMSS(180))
        assertEquals(116.7, WorkoutCalculator.calculateE1RM(100.0, 5), 0.001)
        assertEquals(189, WorkoutCalculator.calculateCalories(72.0, 0L, 30 * 60_000L))

        val (dao, repo) = createEnvironment(backgroundScope, "s4_05_reg.preferences_pb")
        val authVm = AuthOnboardingViewModel(repo)
        authVm.onOpeningAnimationFinished()
        authVm.onBeginFromWelcome()
        authVm.onCreateAccount("v2@mfit.app", "secret123")
        authVm.onSubmitProfile("Jordan", 915148800000L, "72.0", "175")
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)

        val originalUid = repo.authSession.first().uid
        authVm.onToggleGoogleLink("jordan@gmail.com")
        assertTrue(repo.authSession.first().isGoogleLinked)
        assertEquals(originalUid, repo.authSession.first().uid)

        val backupJson = authVm.exportBackupJson()
        assertTrue(backupJson.contains("\"schemaVersion\": 1"))
        val (dstDao, dstRepo) = createEnvironment(backgroundScope, "s4_05_dst.preferences_pb")
        assertTrue(dstRepo.importBackupJson(backupJson))
        assertEquals(dao.getUserProfile(), dstDao.getUserProfile())
        assertNotEquals("", dstDao.getUserProfile()?.uid.orEmpty())
    }
}
