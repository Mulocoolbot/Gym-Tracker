package com.mulocoolbot.gymtracker.blackbox

import androidx.compose.runtime.saveable.SaverScope
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.GymDao
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AuthBridge
import com.mulocoolbot.gymtracker.platform.LiveIslandActionDispatcher
import com.mulocoolbot.gymtracker.platform.LiveIslandActionHandler
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.timer.BackgroundTimerCoordinator
import com.mulocoolbot.gymtracker.timer.LocalTimerNotificationScheduler
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.TimerAudioDuckingPort
import com.mulocoolbot.gymtracker.timer.WorkoutLiveIslandState
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.stats.ChartPointUi
import com.mulocoolbot.gymtracker.ui.stats.HeatmapOrientation
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.stats.computeChartNodeOffsets
import com.mulocoolbot.gymtracker.ui.stats.computeVisibleChartLabelIndices
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialController
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel
import com.mulocoolbot.gymtracker.util.StreakEvaluator
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import com.mulocoolbot.gymtracker.util.formatCompactNumber
import com.mulocoolbot.gymtracker.util.formatDecimal1
import com.mulocoolbot.gymtracker.util.parseDecimalInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class Sprint11BlackBoxTestSuite {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LiveIslandActionDispatcher.bind(null)
        RestTimerController.instance.dismissOrSkip()
    }

    @AfterTest
    fun tearDown() {
        LiveIslandActionDispatcher.bind(null)
        RestTimerController.instance.dismissOrSkip()
        Dispatchers.resetMain()
    }

    private fun testAuthBridge(): AuthBridge = object : AuthBridge {
        override val isGoogleSignInSupported: Boolean = true
        override val isCloudAuthConfigured: Boolean = true
        override fun syncEmailCredentials(email: String, password: String, isRegister: Boolean) = Unit
        override suspend fun deliverOtp(email: String, otpCode: String) = Unit
    }

    private fun createEnv(
        scope: CoroutineScope,
        prefsName: String
    ): Pair<TestInMemoryGymDao, GymRepository> {
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = { createTestPreferencesPath(prefsName).toPath() }
        )
        val dao = TestInMemoryGymDao()
        return dao to GymRepository(dao, SessionPreferences(dataStore))
    }

    @Test
    fun tc_s11_01_critical_and_high_bug_reproductions_all_resolved() = runTest(testDispatcher) {
        val (dao, repo) = createEnv(backgroundScope, "s11_01_workout.preferences_pb")
        val timer = RestTimerController()
        val vm = WorkoutViewModel(repo, timerController = timer, todayEpochDay = 20_800L)

        var stopCalls = 0
        vm.stopWorkout { stopCalls++ }
        vm.updateExerciseInput("Bench Press")
        vm.updateWeightInput("80")
        vm.updateRepsInput("8")
        vm.addSet()
        assertEquals(0, stopCalls)
        assertTrue(vm.uiState.value.activeSessionSets.isEmpty())
        assertFalse(timer.state.value.isRunning)

        vm.startWorkout(currentMillis = 10_000L)
        vm.addSet()
        vm.startWorkout(currentMillis = 20_000L)
        assertEquals(1, vm.uiState.value.activeSessionSets.size)
        assertEquals(10_000L, vm.uiState.value.sessionStartMillis)

        vm.confirmFinishWorkout { stopCalls++ }
        vm.stopWorkout { stopCalls++ }
        vm.onStopWorkoutFromIsland { stopCalls++ }
        assertEquals(1, stopCalls)
        assertEquals(1, dao.getAllSessions().size)
        assertEquals(1, dao.getAllSets().size)

        dao.upsertUserProfile(
            UserProfileEntity(
                uid = "s11-u1",
                name = "Regression User",
                birthDateMillis = 946684800000L,
                heightCm = 175.0,
                weightKg = 75.0,
                currentStreak = 4,
                lastWorkoutEpochDay = 20_800L
            )
        )
        repo.recordWorkoutSession(
            planName = "Push",
            startMillis = 30_000L,
            endMillis = 60_000L,
            sets = listOf(
                WorkoutSetEntity(0L, 0L, "   ", "NORMAL", 1, 60.0, 8, 0.0, 35_000L),
                WorkoutSetEntity(0L, 0L, "Squat", "NORMAL", 2, -25.0, 8, 0.0, 40_000L),
                WorkoutSetEntity(0L, 0L, "Squat", "NORMAL", 3, Double.NaN, 0, 0.0, 45_000L)
            ),
            todayEpochDay = 20_801L
        )
        assertEquals(1, dao.getAllSessions().size)
        assertEquals(1, dao.getAllSets().size)
        assertEquals(4, dao.getUserProfile()?.currentStreak)

        var nowMs = -10_000L
        var playAlerts = 0
        val coordTimer = RestTimerController()
        val coordinator = BackgroundTimerCoordinator(
            controller = coordTimer,
            notifications = object : LocalTimerNotificationScheduler {
                override fun scheduleCompletion(delayMillis: Long, label: String) = Unit
                override fun cancel(clearDelivered: Boolean) = Unit
            },
            audio = object : TimerAudioDuckingPort {
                override fun playDuckedCompletionAlert() {
                    playAlerts++
                }
                override fun stopAndUnduckAudio() = Unit
            },
            clockMillis = { nowMs }
        )
        coordTimer.startTimer(durationMillis = 10_000L, label = "Set 1", currentMillis = nowMs)
        nowMs = 1_000L
        coordTimer.tick(nowMs)
        assertTrue(coordTimer.state.value.alarmRinging)
        coordinator.onAdjustTimer(15)
        assertEquals(0, playAlerts)

        RestTimerController.instance.startTimer(durationMillis = 60_000L, label = "Set 1", currentMillis = 1_000L)
        LiveIslandActionDispatcher.onStopRestTimerFromIsland()
        assertFalse(RestTimerController.instance.state.value.isRunning)
        assertFalse(RestTimerController.instance.state.value.alarmRinging)

        val (_, authRepo) = createEnv(backgroundScope, "s11_01_auth.preferences_pb")
        val authVm = AuthOnboardingViewModel(repository = authRepo, authBridge = testAuthBridge())
        authVm.onOpeningAnimationFinished()
        authVm.onBeginFromWelcome()
        authVm.requestEmailOtp("sprint11@mfit.app", currentMillis = 10_000L)
        assertTrue(authVm.uiState.value.resendCountdownSeconds > 0)
        authVm.onGoogleAuthenticated("sprint11@mfit.app", "Sprint Eleven")
        authVm.onContinueOffline()
        assertTrue(authVm.uiState.value.authSession.isAuthenticated)

        val delegate = TestInMemoryGymDao()
        val uidProfiles = MutableStateFlow(linkedMapOf<String, UserProfileEntity>())
        val uidKeyedDao = object : GymDao by delegate {
            override fun observeUserProfile(): Flow<UserProfileEntity?> =
                uidProfiles.map { it.values.firstOrNull() }
            override suspend fun getUserProfile(): UserProfileEntity? =
                uidProfiles.value.values.firstOrNull()
            override suspend fun upsertUserProfile(profile: UserProfileEntity) {
                uidProfiles.value = linkedMapOf<String, UserProfileEntity>().apply {
                    putAll(uidProfiles.value)
                    put(profile.uid, profile)
                }
            }
            override suspend fun clearUserProfile() {
                uidProfiles.value = linkedMapOf()
            }
            override suspend fun replaceSingleUserProfile(profile: UserProfileEntity) {
                val existing = getUserProfile()
                if (existing != null && existing.uid != profile.uid) {
                    clearUserProfile()
                }
                upsertUserProfile(profile)
            }
        }
        uidKeyedDao.upsertUserProfile(
            UserProfileEntity("legacy-uid", "Old", 946684800000L, 175.0, 70.0)
        )
        val uidPrefs = SessionPreferences(
            PreferenceDataStoreFactory.createWithPath(
                scope = backgroundScope,
                produceFile = { createTestPreferencesPath("s11_01_uid_store.preferences_pb").toPath() }
            )
        )
        val uidRepo = GymRepository(uidKeyedDao, uidPrefs)
        uidRepo.signInWithGoogle("veteran@mfit.app", AuthEntryMode.LOG_IN)
        assertEquals(1, uidProfiles.value.size)
        assertEquals(SessionPreferences.computeUidForEmail("veteran@mfit.app"), uidKeyedDao.getUserProfile()?.uid)

        assertNull(parseDecimalInput("NaN"))
        assertNull(parseDecimalInput("Infinity"))
        assertFalse(formatDecimal1(-1e20).contains("--"))

        val (rolloverDao, rolloverRepo) = createEnv(backgroundScope, "s11_01_rollover.preferences_pb")
        val mon = LocalDate(2026, 6, 1)
        val tue = LocalDate(2026, 6, 2)
        rolloverDao.upsertWorkoutPlans(
            listOf(
                WorkoutPlanEntity(1, false, "Push"),
                WorkoutPlanEntity(2, false, "Pull"),
                WorkoutPlanEntity(3, false, "Legs")
            )
        )
        rolloverDao.upsertUserProfile(
            UserProfileEntity(
                uid = "roll-u1",
                name = "Athlete",
                birthDateMillis = 946684800000L,
                heightCm = 175.0,
                weightKg = 75.0,
                currentStreak = 5,
                lastWorkoutEpochDay = mon.toEpochDays().toLong()
            )
        )
        val thu = LocalDate(2026, 6, 4)
        rolloverRepo.recordWorkoutSession(
            planName = "Legs",
            startMillis = 1_000L,
            endMillis = 61_000L,
            sets = listOf(WorkoutSetEntity(0L, 0L, "Squat", "NORMAL", 1, 100.0, 5, 116.7, 2_000L)),
            todayEpochDay = thu.toEpochDays().toLong()
        )
        assertEquals(1, rolloverDao.getUserProfile()?.currentStreak)

        val westward = StreakEvaluator.onWorkoutCompleted(
            rolloverDao.getUserProfile()!!.copy(currentStreak = 3, lastWorkoutEpochDay = tue.toEpochDays().toLong()),
            todayEpochDay = mon.toEpochDays().toLong()
        )
        assertEquals(3, westward.currentStreak)
        assertEquals(tue.toEpochDays().toLong(), westward.lastWorkoutEpochDay)

        var todayDate = mon
        val homeVm = HomeViewModel(rolloverRepo, currentDateProvider = { todayDate })
        val rollWorkoutVm = WorkoutViewModel(
            repository = rolloverRepo,
            timerController = RestTimerController(),
            todayEpochDayProvider = { todayDate.toEpochDays().toLong() }
        )
        assertEquals(1, homeVm.uiState.value.selectedDayOfWeek)
        assertEquals("Push", rollWorkoutVm.uiState.value.todayPlanName)
        todayDate = tue
        homeVm.refreshDateIfNeeded()
        rollWorkoutVm.refreshCurrentDayIfNeeded()
        assertEquals(2, homeVm.uiState.value.selectedDayOfWeek)
        assertEquals("Pull", rollWorkoutVm.uiState.value.todayPlanName)
    }

    @Test
    fun tc_s11_02_rapid_interaction_stress_test() = runTest(testDispatcher) {
        val (dao, repo) = createEnv(backgroundScope, "s11_02_stress.preferences_pb")
        val timer = RestTimerController()
        val vm = WorkoutViewModel(repo, timerController = timer, todayEpochDay = 20_810L)

        var bgStartCalls = 0
        var bgAdjustCalls = 0
        var bgStopRestCalls = 0
        var bgStopWorkoutCalls = 0

        LiveIslandActionDispatcher.bind(
            object : LiveIslandActionHandler {
                override fun onStopRestTimerFromIsland() {
                    vm.onStopRestTimerFromIsland { bgStopRestCalls++ }
                }

                override fun onStopWorkoutFromIsland() {
                    vm.onStopWorkoutFromIsland("Stress Day") { bgStopWorkoutCalls++ }
                }
            }
        )

        vm.startWorkout(currentMillis = 1_000_000L)
        repeat(50) { idx ->
            val setType = when (idx % 3) {
                0 -> WorkoutUiState.SET_TYPE_WARMUP
                1 -> WorkoutUiState.SET_TYPE_NORMAL
                else -> WorkoutUiState.SET_TYPE_FINAL
            }
            vm.updateExerciseInput("Bench Press")
            vm.selectMuscleGroup("CHEST")
            vm.selectSetType(setType)
            vm.updateWeightInput("${60 + idx}")
            vm.updateRepsInput("${5 + (idx % 5)}")
            vm.addSet { _, _ -> bgStartCalls++ }
        }
        val activeSets = vm.uiState.value.activeSessionSets
        assertEquals(50, activeSets.size)
        assertEquals((1L..50L).toList(), activeSets.map { it.id })
        assertEquals((1..50).toList(), activeSets.map { it.setOrder })
        assertEquals(50, bgStartCalls)
        assertTrue(vm.liveIslandState.value is WorkoutLiveIslandState.RestTimerActive)

        repeat(10) { vm.adjustRestTimer(15) { bgAdjustCalls++ } }
        val callsBeforeDrain = bgAdjustCalls
        assertEquals(10, callsBeforeDrain)
        repeat(30) { vm.adjustRestTimer(-15) { bgAdjustCalls++ } }
        assertTrue(timer.state.value.remainingSeconds >= 0)
        assertTrue(timer.state.value.alarmRinging)
        val callsAtAlarm = bgAdjustCalls
        repeat(10) { vm.adjustRestTimer(15) { bgAdjustCalls++ } }
        repeat(10) { vm.adjustRestTimer(-15) { bgAdjustCalls++ } }
        assertEquals(callsAtAlarm, bgAdjustCalls)

        vm.confirmFinishWorkout()
        assertEquals(1, dao.getAllSessions().size)

        repeat(10) { cycle ->
            vm.startWorkout(currentMillis = 2_000_000L + cycle * 120_000L)
            vm.updateExerciseInput("Barbell Row")
            vm.updateWeightInput("70")
            vm.updateRepsInput("8")
            vm.addSet()
            assertTrue(vm.liveIslandState.value is WorkoutLiveIslandState.RestTimerActive)

            LiveIslandActionDispatcher.onStopRestTimerFromIsland()
            assertTrue(vm.uiState.value.isWorkoutActive)
            assertTrue(vm.liveIslandState.value is WorkoutLiveIslandState.WorkoutOngoing)

            LiveIslandActionDispatcher.onStopWorkoutFromIsland()
            LiveIslandActionDispatcher.onStopWorkoutFromIsland()
            assertFalse(vm.uiState.value.isWorkoutActive)
            assertEquals(WorkoutLiveIslandState.Inactive, vm.liveIslandState.value)
        }
        assertEquals(10, bgStopRestCalls)
        assertEquals(10, bgStopWorkoutCalls)
        assertEquals(11, dao.getAllSessions().size)

        val controller = TutorialController()
        repeat(25) {
            controller.onNext()
            assertTrue(controller.stepIndex in 0..9)
        }
        assertEquals(9, controller.stepIndex)
        repeat(25) {
            controller.onPrevious()
            assertTrue(controller.stepIndex in 0..9)
        }
        assertEquals(0, controller.stepIndex)

        var persistCount = 0
        var navHomeCount = 0
        repeat(5) {
            controller.onSkip(
                onPersistCompleted = { persistCount++ },
                onNavigateHome = { navHomeCount++ }
            )
            controller.onDone(
                onPersistCompleted = { persistCount++ },
                onNavigateHome = { navHomeCount++ }
            )
        }
        assertEquals(1, persistCount)
        assertEquals(1, navHomeCount)

        val saved = with(TutorialController.Saver) { SaverScope { true }.save(controller) }
        assertNotNull(saved)
        val restored = TutorialController.Saver.restore(saved)
        assertNotNull(restored)
        assertTrue(restored.isDismissedLocally)
        assertEquals(0, restored.stepIndex)
    }

    @Test
    fun tc_s11_03_extreme_inputs_corrupt_json_empty_and_single_point_stats() = runTest(testDispatcher) {
        val (dao, repo) = createEnv(backgroundScope, "s11_03_edge.preferences_pb")
        val vm = WorkoutViewModel(repo, timerController = RestTimerController(), todayEpochDay = 20_820L)
        vm.startWorkout(currentMillis = 1_000L)

        val badPairs = listOf(
            "0" to "10",
            "-100" to "10",
            "999999" to "10",
            "1e308" to "10",
            "NaN" to "10",
            "Infinity" to "10",
            "80" to "0",
            "80" to "-5",
            "80" to "99999",
            "80" to "abc",
            "" to ""
        )
        vm.updateExerciseInput("Bench Press")
        badPairs.forEach { (w, r) ->
            vm.updateWeightInput(w)
            vm.updateRepsInput(r)
            vm.addSet()
        }
        assertTrue(vm.uiState.value.activeSessionSets.isEmpty())

        vm.updateWeightInput("50")
        vm.stepWeight(-50_000.0)
        assertEquals("0", vm.uiState.value.weightInput)
        vm.stepWeight(50_000.0)
        assertEquals("10000", vm.uiState.value.weightInput)

        vm.updateRepsInput("10")
        vm.stepReps(-50_000)
        assertEquals("1", vm.uiState.value.repsInput)
        vm.updateRepsInput(Int.MAX_VALUE.toString())
        vm.stepReps(1)
        assertEquals("10000", vm.uiState.value.repsInput)

        vm.updateWeightInput("62,5")
        vm.updateRepsInput("1")
        vm.addSet()
        assertEquals(1, vm.uiState.value.activeSessionSets.size)
        assertEquals(62.5, vm.uiState.value.activeSessionSets.first().weightKg)
        assertEquals(62.5, vm.uiState.value.activeSessionSets.first().e1rm)
        vm.confirmFinishWorkout()

        val (statsDao, statsRepo) = createEnv(backgroundScope, "s11_03_stats.preferences_pb")
        val statsVm = StatsViewModel(statsRepo)
        val emptyState = statsVm.uiState.value
        assertFalse(emptyState.hasStatisticsData)
        assertTrue(emptyState.exerciseOptions.isEmpty())
        assertEquals("", emptyState.selectedExerciseName)
        assertEquals(0.0, emptyState.percentageChange)
        assertTrue(emptyState.muscleVolumes.values.all { it.weeklySets == 0 })
        assertTrue(computeChartNodeOffsets(emptyList(), 300f, 180f, 16f, 16f, 24f).isEmpty())
        assertTrue(computeVisibleChartLabelIndices(0, 0, 240f, 44f).isEmpty())

        statsDao.upsertUserProfile(
            UserProfileEntity("edge-u1", "Edge User", 946684800000L, 175.0, 75.0)
        )
        statsRepo.recordWorkoutSession(
            planName = "Push",
            startMillis = 1_700_000_000_000L,
            endMillis = 1_700_000_600_000L,
            sets = listOf(
                WorkoutSetEntity(0L, 0L, "Bench Press", "NORMAL", 1, 80.0, 5, 0.0, 1_700_000_100_000L)
            ),
            todayEpochDay = 20_820L,
            muscleGroupsByExercise = mapOf("Bench Press" to "CHEST")
        )
        val singleState = statsVm.uiState.value
        assertTrue(singleState.hasStatisticsData)
        assertEquals(1, singleState.exerciseOptions.size)
        assertEquals(1, singleState.selectedExerciseOption?.points?.size)
        assertEquals(0, singleState.selectedPointIndex)
        assertEquals("+0 kg", singleState.selectedExerciseOption?.deltaKgText)
        assertEquals(0.0, singleState.percentageChange)
        assertEquals(setOf(0), computeVisibleChartLabelIndices(1, 0, 215f, 44f))

        val singleOffsets = computeChartNodeOffsets(
            points = singleState.selectedExerciseOption!!.points,
            width = 300f,
            height = 180f,
            horizontalPadding = 16f,
            topPadding = 16f,
            bottomPadding = 24f
        )
        assertEquals(1, singleOffsets.size)
        assertEquals(150f, singleOffsets.first().x)
        assertFalse(singleOffsets.first().x.isNaN())
        assertFalse(singleOffsets.first().y.isNaN())

        statsVm.selectMuscleRegion("GLUTES")
        assertEquals(HeatmapOrientation.BACK, statsVm.uiState.value.heatmapOrientation)

        val nanOffsets = computeChartNodeOffsets(
            points = listOf(
                ChartPointUi(0, "D1", "80 kg × 5", 80.0, 5, Double.NaN),
                ChartPointUi(1, "D2", "85 kg × 5", 85.0, 5, Double.POSITIVE_INFINITY)
            ),
            width = 240f,
            height = 140f,
            horizontalPadding = 14f,
            topPadding = 14f,
            bottomPadding = 20f
        )
        assertTrue(nanOffsets.all { it.x.isFinite() && it.y.isFinite() })

        val corruptInputs = listOf(
            "",
            "   ",
            "{corrupt",
            "[]",
            """{"schemaVersion":99,"userProfile":null,"workoutPlans":[],"exerciseHistory":[],"workoutSessions":[],"workoutSets":[]}""",
            """{"schemaVersion":1,"userProfile":null,"workoutPlans":[],"exerciseHistory":[],"workoutSessions":[],"workoutSets":[{"setId":1,"sessionId":999,"exerciseName":"Squat","setType":"NORMAL","setOrder":1,"weightKg":100.0,"reps":5,"e1rm":116.7,"timestampMillis":1000}]}"""
        )
        corruptInputs.forEach { badJson ->
            assertNull(GymBackupManager.validateBackupJson(badJson))
            assertFalse(statsRepo.importBackupJson(badJson))
        }
        assertEquals(1, statsDao.getAllSessions().size)
        assertEquals(1, statsDao.getAllSets().size)
        assertEquals("Edge User", statsDao.getUserProfile()?.name)
        assertFalse(formatCompactNumber(Double.NaN).contains("NaN"))
        assertFalse(formatDecimal1(Double.NEGATIVE_INFINITY).contains("Infinity"))
    }
}
