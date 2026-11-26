package com.mulocoolbot.gymtracker.bugfix

import androidx.compose.runtime.saveable.SaverScope
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.GymDao
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.timer.BackgroundTimerCoordinator
import com.mulocoolbot.gymtracker.timer.LocalTimerNotificationScheduler
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.TimerAudioDuckingPort
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.stats.HeatmapOrientation
import com.mulocoolbot.gymtracker.ui.stats.MuscleRegion
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.stats.computeVisibleChartLabelIndices
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialController
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
class BugHunterReproductionTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        RestTimerController.instance.dismissOrSkip()
        Dispatchers.resetMain()
    }

    private fun createPrefs(scope: kotlinx.coroutines.CoroutineScope, name: String): SessionPreferences {
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            produceFile = { createTestPreferencesPath("$name.preferences_pb").toPath() }
        )
        return SessionPreferences(dataStore)
    }

    @Test
    fun bug01_workoutDoubleStopAddSetWhenInactiveAndExtremeReps() = runTest {
        val dao = TestInMemoryGymDao()
        val repo = GymRepository(dao, createPrefs(backgroundScope, "bug01"))
        val timer = RestTimerController()
        val vm = WorkoutViewModel(repo, timerController = timer, todayEpochDay = 20_000L)

        vm.updateExerciseInput("Bench Press")
        vm.updateWeightInput("80")
        vm.updateRepsInput("8")
        vm.addSet()
        assertTrue(
            vm.uiState.value.activeSessionSets.isEmpty(),
            "addSet should not add sets before startWorkout() is called"
        )
        assertFalse(timer.state.value.isRunning, "Rest timer should not start when workout is inactive")

        vm.startWorkout()
        vm.addSet()
        var stopTimerCalls = 0
        vm.confirmFinishWorkout { stopTimerCalls++ }
        vm.confirmFinishWorkout { stopTimerCalls++ }
        assertEquals(1, stopTimerCalls, "Duplicate confirmFinishWorkout must be ignored")

        vm.updateRepsInput(Int.MAX_VALUE.toString())
        vm.stepReps(1)
        assertEquals("10000", vm.uiState.value.repsInput)
    }

    @Test
    fun bug02_backgroundCoordinatorAdjustWhileAlarmRingingDoesNotRetriggerAlert() {
        val timer = RestTimerController()
        var nowMillis = 1_000L
        var playCalls = 0
        val notifications = object : LocalTimerNotificationScheduler {
            override fun scheduleCompletion(delayMillis: Long, label: String) = Unit
            override fun cancel(clearDelivered: Boolean) = Unit
        }
        val audio = object : TimerAudioDuckingPort {
            override fun playDuckedCompletionAlert() {
                playCalls++
            }
            override fun stopAndUnduckAudio() = Unit
        }
        val coordinator = BackgroundTimerCoordinator(
            controller = timer,
            notifications = notifications,
            audio = audio,
            clockMillis = { nowMillis }
        )

        timer.startTimer(durationMillis = 10_000L, label = "Set 1", currentMillis = nowMillis)
        nowMillis = 12_000L
        timer.tick(nowMillis)
        assertTrue(timer.state.value.alarmRinging)

        coordinator.onAdjustTimer(-15)
        assertEquals(
            0,
            playCalls,
            "Adjusting an already-ringing timer must not trigger completion alert again"
        )
    }

    @Test
    fun bug04_recordWorkoutSessionRejectsInvalidSetsAndEmptySession() = runTest {
        val dao = TestInMemoryGymDao()
        val repo = GymRepository(dao, createPrefs(backgroundScope, "bug04"))
        dao.upsertUserProfile(
            UserProfileEntity(
                uid = "u1",
                name = "Test",
                birthDateMillis = 946684800000L,
                heightCm = 175.0,
                weightKg = 75.0,
                currentStreak = 2,
                consecutiveMissed = 0,
                lastWorkoutEpochDay = 19_999L
            )
        )

        repo.recordWorkoutSession(
            planName = "Push",
            startMillis = 1_000_000L,
            endMillis = 1_600_000L,
            sets = listOf(
                WorkoutSetEntity(
                    sessionId = 0L,
                    exerciseName = "   ",
                    setType = "NORMAL",
                    setOrder = 1,
                    weightKg = -10.0,
                    reps = 0,
                    e1rm = 0.0,
                    timestampMillis = 1_100_000L
                )
            ),
            todayEpochDay = 20_000L
        )

        assertTrue(dao.getAllSessions().isEmpty(), "Invalid sets must not create an empty session")
        assertTrue(dao.getAllSets().isEmpty(), "Invalid sets must not be inserted")
        assertEquals(2, dao.getUserProfile()?.currentStreak, "Invalid session must not increment streak")
    }

    @Test
    fun bug05_backupValidationRejectsOutOfRangeValuesAndInvalidSessions() {
        val invalidBackupJson = """
            {
              "schemaVersion": 1,
              "userProfile": {
                "uid": "u1",
                "name": "Athlete",
                "birthDateMillis": 946684800000,
                "heightCm": 5.0,
                "weightKg": 9999.0,
                "currentStreak": 0,
                "consecutiveMissed": 0,
                "lastWorkoutEpochDay": null,
                "isGoogleLinked": false,
                "isEmailLinked": false
              },
              "workoutPlans": [
                { "dayOfWeek": 1, "isRestDay": false, "planName": "   " }
              ],
              "exerciseHistory": [],
              "workoutSessions": [
                {
                  "sessionId": 1,
                  "dateEpochDay": 20000,
                  "planName": "   ",
                  "startTimeMillis": 2000,
                  "endTimeMillis": 1000,
                  "caloriesBurned": 100
                }
              ],
              "workoutSets": [
                {
                  "setId": 1,
                  "sessionId": 1,
                  "exerciseName": "Bench Press",
                  "setType": "NORMAL",
                  "setOrder": 0,
                  "weightKg": 0.0,
                  "reps": 8,
                  "e1rm": 0.0,
                  "timestampMillis": 1500
                }
              ]
            }
        """.trimIndent()

        assertNull(
            GymBackupManager.validateBackupJson(invalidBackupJson),
            "Corrupt/out-of-range backup payload must be rejected"
        )
    }

    @Test
    fun bug06_recordWorkoutSessionEvaluatesMissedScheduledDaysBeforeIncrementingStreak() = runTest {
        val dao = TestInMemoryGymDao()
        val repo = GymRepository(dao, createPrefs(backgroundScope, "bug06"))

        val day100 = LocalDate(2026, 4, 1)
        val epochDay100 = day100.toEpochDays().toLong()
        val epochDay101 = epochDay100 + 1L
        val epochDay102 = epochDay100 + 2L
        val epochDay103 = epochDay100 + 3L

        dao.upsertWorkoutPlans(
            listOf(
                WorkoutPlanEntity(
                    dayOfWeek = LocalDate.fromEpochDays(epochDay101.toInt()).dayOfWeek.isoDayNumber % 7,
                    isRestDay = false,
                    planName = "Pull"
                ),
                WorkoutPlanEntity(
                    dayOfWeek = LocalDate.fromEpochDays(epochDay102.toInt()).dayOfWeek.isoDayNumber % 7,
                    isRestDay = false,
                    planName = "Legs"
                )
            )
        )
        dao.upsertUserProfile(
            UserProfileEntity(
                uid = "u1",
                name = "Athlete",
                birthDateMillis = 946684800000L,
                heightCm = 175.0,
                weightKg = 75.0,
                currentStreak = 5,
                consecutiveMissed = 0,
                lastWorkoutEpochDay = epochDay100
            )
        )

        repo.recordWorkoutSession(
            planName = "Arms",
            startMillis = 1_000_000L,
            endMillis = 2_000_000L,
            sets = listOf(
                WorkoutSetEntity(
                    sessionId = 0L,
                    exerciseName = "Bicep Curl",
                    setType = "NORMAL",
                    setOrder = 1,
                    weightKg = 15.0,
                    reps = 10,
                    e1rm = 20.0,
                    timestampMillis = 1_500_000L
                )
            ),
            todayEpochDay = epochDay103
        )

        assertEquals(
            1,
            dao.getUserProfile()?.currentStreak,
            "Missing 2 scheduled workout days must reset old streak to 0 before counting new workout as 1"
        )
    }

    @Test
    fun bug07_e1rmOneRepRoundingAndNonFiniteFormattingSafety() {
        assertEquals(82.3, WorkoutCalculator.calculateE1RM(82.34, 1))
        assertEquals(0.0, WorkoutCalculator.calculateE1RM(Double.NaN, 5))
        assertEquals("0.0", formatDecimal1(Double.NaN))
        assertEquals("0", formatCompactNumber(Double.POSITIVE_INFINITY))
    }

    @Test
    fun bug08_logInUserAndBackupRestoreAlwaysMarkTutorialCompleted() = runTest {
        val dao = TestInMemoryGymDao()
        val repo = GymRepository(dao, createPrefs(backgroundScope, "bug08_login"))

        repo.signInWithGoogle("returning@example.com", AuthEntryMode.LOG_IN)
        val sessionAfterLogin = repo.authSession.first()
        assertFalse(sessionAfterLogin.shouldShowTutorial)
        assertTrue(
            sessionAfterLogin.hasCompletedTutorial,
            "Returning LOG_IN user must have hasCompletedTutorial = true so updates/re-auth never show tutorial"
        )

        val dao2 = TestInMemoryGymDao()
        val repo2 = GymRepository(dao2, createPrefs(backgroundScope, "bug08_backup"))
        repo2.signInWithEmail("new@example.com", AuthEntryMode.SIGN_IN)
        assertTrue(repo2.authSession.first().shouldShowTutorial)

        val sessionOnlyBackup = """
            {
              "schemaVersion": 1,
              "userProfile": null,
              "workoutPlans": [],
              "exerciseHistory": [],
              "workoutSessions": [
                {
                  "sessionId": 1,
                  "dateEpochDay": 20000,
                  "planName": "Push",
                  "startTimeMillis": 1000,
                  "endTimeMillis": 2000,
                  "caloriesBurned": 120
                }
              ],
              "workoutSets": []
            }
        """.trimIndent()
        assertTrue(repo2.importBackupJson(sessionOnlyBackup))
        val sessionAfterRestore = repo2.authSession.first()
        assertFalse(sessionAfterRestore.shouldShowTutorial)
        assertTrue(sessionAfterRestore.hasCompletedTutorial)
    }

    @Test
    fun bug09_muscleInferenceDoesNotFalseMatchSubstrings() {
        val machineChest = StatsViewModel.inferTrainedMuscles(listOf("Machine Chest Press"))
        assertTrue(MuscleRegion.CHEST in machineChest)
        assertFalse(
            MuscleRegion.BICEPS in machineChest || MuscleRegion.LATS in machineChest,
            "Substring 'chin' inside 'Machine' must not match BICEPS or LATS"
        )

        val cableLateral = StatsViewModel.inferTrainedMuscles(listOf("Cable Lateral Raise"))
        assertTrue(MuscleRegion.SHOULDERS in cableLateral)
        assertFalse(
            MuscleRegion.ABS in cableLateral || MuscleRegion.LATS in cableLateral,
            "Substrings 'ab'/'lat' inside 'Cable Lateral' must not match ABS or LATS"
        )

        val backSquat = StatsViewModel.inferTrainedMuscles(listOf("Back Squat"))
        assertTrue(MuscleRegion.QUADS in backSquat)
        assertFalse(
            MuscleRegion.LATS in backSquat,
            "'Back Squat' must not match LATS"
        )
    }

    @Test
    fun bug10_chartVisibleLabelsAvoidCollisionsOnNarrowAxis() {
        val visible = computeVisibleChartLabelIndices(
            pointCount = 8,
            selectedIndex = 1,
            chartWidthPx = 280f,
            minSpacingPx = 60f
        )
        assertFalse(
            visible.contains(0) && visible.contains(1),
            "Selected index 1 must not collide with index 0 within minSpacingPx"
        )
    }

    @Test
    fun bug11_selectBackMuscleAutomaticallySwitchesHeatmapOrientation() = runTest {
        val dao = TestInMemoryGymDao()
        val repo = GymRepository(dao, createPrefs(backgroundScope, "bug11"))
        val vm = StatsViewModel(repo)

        assertEquals(HeatmapOrientation.FRONT, vm.uiState.value.heatmapOrientation)
        vm.selectMuscleRegion("TRAPS")
        assertEquals("TRAPS", vm.uiState.value.selectedMuscleId)
        assertEquals(
            HeatmapOrientation.BACK,
            vm.uiState.value.heatmapOrientation,
            "Selecting a posterior muscle region must automatically switch heatmap orientation to BACK"
        )
    }

    @Test
    fun bug01_doubleStartWorkoutDoesNotWipeActiveSets() = runTest {
        val dao = TestInMemoryGymDao()
        val repo = GymRepository(dao, createPrefs(backgroundScope, "bug01_double_start"))
        val timer = RestTimerController()
        val vm = WorkoutViewModel(repo, timerController = timer, todayEpochDay = 20_000L)

        vm.startWorkout(currentMillis = 1_000L)
        vm.updateExerciseInput("Bench Press")
        vm.updateWeightInput("80")
        vm.updateRepsInput("8")
        vm.addSet()
        assertEquals(1, vm.uiState.value.activeSessionSets.size)

        vm.startWorkout(currentMillis = 2_000L)
        assertEquals(1, vm.uiState.value.activeSessionSets.size)
        assertEquals(1_000L, vm.uiState.value.sessionStartMillis)
        vm.confirmFinishWorkout()
    }

    @Test
    fun bug07_bug11_extendedFormattingE1rmAndChartCollisionGuards() {
        assertNull(parseDecimalInput("NaN"))
        assertNull(parseDecimalInput("Infinity"))
        assertFalse(formatDecimal1(-1e20).contains("--"))
        assertEquals(30.3, WorkoutCalculator.calculateE1RM(27.5, 3))

        val visibleNarrow = computeVisibleChartLabelIndices(
            pointCount = 10,
            selectedIndex = 4,
            chartWidthPx = 215f,
            minSpacingPx = 44f
        )
        assertFalse(8 in visibleNarrow)
    }

    @Test
    fun bug06_streakWestwardTimezoneAndMidnightRollover() = runTest {
        val profile = UserProfileEntity(
            uid = "u1",
            name = "Athlete",
            birthDateMillis = 946684800000L,
            heightCm = 175.0,
            weightKg = 75.0,
            currentStreak = 3,
            consecutiveMissed = 0,
            lastWorkoutEpochDay = 20_761L
        )
        val afterWestwardTravel = StreakEvaluator.onWorkoutCompleted(profile, todayEpochDay = 20_760L)
        assertEquals(3, afterWestwardTravel.currentStreak)
        assertEquals(20_761L, afterWestwardTravel.lastWorkoutEpochDay)

        val dao = TestInMemoryGymDao()
        val repo = GymRepository(dao, createPrefs(backgroundScope, "bug10_rollover"))
        dao.upsertUserProfile(
            UserProfileEntity(
                uid = "u1",
                name = "Athlete",
                birthDateMillis = 946684800000L,
                heightCm = 175.0,
                weightKg = 75.0,
                currentStreak = 0
            )
        )
        val day1 = LocalDate(2026, 5, 4)
        val day2 = LocalDate(2026, 5, 5)
        dao.upsertWorkoutPlans(
            listOf(
                WorkoutPlanEntity(dayOfWeek = 1, isRestDay = false, planName = "Push Day"),
                WorkoutPlanEntity(dayOfWeek = 2, isRestDay = false, planName = "Pull Day")
            )
        )

        var currentDate = day1
        val homeVm = HomeViewModel(repo, currentDateProvider = { currentDate })
        val workoutVm = WorkoutViewModel(
            repository = repo,
            timerController = RestTimerController(),
            todayEpochDayProvider = { currentDate.toEpochDays().toLong() }
        )

        assertEquals(1, homeVm.uiState.value.selectedDayOfWeek)
        assertEquals("Push Day", workoutVm.uiState.value.todayPlanName)

        workoutVm.startWorkout(currentMillis = 1_000_000L)
        workoutVm.updateExerciseInput("Bench Press")
        workoutVm.updateWeightInput("60")
        workoutVm.updateRepsInput("10")
        workoutVm.addSet()
        workoutVm.confirmFinishWorkout()
        assertEquals(1, dao.getUserProfile()?.currentStreak)

        currentDate = day2
        homeVm.refreshDateIfNeeded()
        workoutVm.refreshCurrentDayIfNeeded()

        assertEquals(2, homeVm.uiState.value.selectedDayOfWeek)
        assertEquals("Pull Day", workoutVm.uiState.value.todayPlanName)

        workoutVm.startWorkout(currentMillis = 2_000_000L)
        workoutVm.updateExerciseInput("Barbell Row")
        workoutVm.updateWeightInput("70")
        workoutVm.updateRepsInput("8")
        workoutVm.addSet()
        workoutVm.confirmFinishWorkout()
        assertEquals(2, dao.getUserProfile()?.currentStreak)
        assertEquals(day2.toEpochDays().toLong(), dao.getUserProfile()?.lastWorkoutEpochDay)
    }

    @Test
    fun bug08_uidKeyedProfileReplacementKeepsSingleRow() = runTest {
        val delegate = TestInMemoryGymDao()
        val uidKeyedProfiles = MutableStateFlow(linkedMapOf<String, UserProfileEntity>())
        val uidKeyedDao = object : GymDao by delegate {
            override fun observeUserProfile(): Flow<UserProfileEntity?> =
                uidKeyedProfiles.map { it.values.firstOrNull() }

            override suspend fun getUserProfile(): UserProfileEntity? =
                uidKeyedProfiles.value.values.firstOrNull()

            override suspend fun upsertUserProfile(profile: UserProfileEntity) {
                val updated = linkedMapOf<String, UserProfileEntity>().apply {
                    putAll(uidKeyedProfiles.value)
                    put(profile.uid, profile)
                }
                uidKeyedProfiles.value = updated
            }

            override suspend fun clearUserProfile() {
                uidKeyedProfiles.value = linkedMapOf()
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
            UserProfileEntity(
                uid = "legacy-uid",
                name = "Old",
                birthDateMillis = 946684800000L,
                heightCm = 175.0,
                weightKg = 70.0
            )
        )
        val repo = GymRepository(uidKeyedDao, createPrefs(backgroundScope, "bug08_single_row"))
        repo.signInWithGoogle("veteran@mfit.app", AuthEntryMode.LOG_IN)

        assertEquals(1, uidKeyedProfiles.value.size)
        assertEquals(
            SessionPreferences.computeUidForEmail("veteran@mfit.app"),
            uidKeyedDao.getUserProfile()?.uid
        )
    }

    @Test
    fun bug14_tutorialOnPreviousAndSaverRoundTrip() {
        val controller = TutorialController()
        assertEquals(0, controller.stepIndex)
        controller.onPrevious()
        assertEquals(0, controller.stepIndex)

        controller.onNext()
        controller.onNext()
        assertEquals(2, controller.stepIndex)
        controller.onPrevious()
        assertEquals(1, controller.stepIndex)

        val scope = SaverScope { true }
        val saved = with(TutorialController.Saver) { scope.save(controller) }
        assertNotNull(saved)
        val restored = TutorialController.Saver.restore(saved)
        assertNotNull(restored)
        assertEquals(1, restored.stepIndex)
        assertFalse(restored.isDismissedLocally)
    }
}
