package com.mulocoolbot.gymtracker.blackbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.backup.GymBackupManager
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.TestInMemoryGymDao
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.platform.AuthBridge
import com.mulocoolbot.gymtracker.platform.createTestPreferencesPath
import com.mulocoolbot.gymtracker.timer.BackgroundTimerCoordinator
import com.mulocoolbot.gymtracker.timer.LocalTimerNotificationScheduler
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.TimerAudioDuckingPort
import com.mulocoolbot.gymtracker.timer.buildRestCompletionWavBytes
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.home.HomeViewModel
import com.mulocoolbot.gymtracker.ui.plan.WorkoutPlanViewModel
import com.mulocoolbot.gymtracker.ui.settings.resolveBackupImportFeedback
import com.mulocoolbot.gymtracker.ui.stats.HeatmapOrientation
import com.mulocoolbot.gymtracker.ui.stats.StatsViewModel
import com.mulocoolbot.gymtracker.ui.workout.WorkoutUiState
import com.mulocoolbot.gymtracker.ui.workout.WorkoutViewModel
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import kotlin.math.abs
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class Sprint5BlackBoxTestSuite {

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
    fun tc_s5_01_timer_foreground_and_timestamp_accuracy() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s5_01_fg_timer.preferences_pb")
        val timerController = RestTimerController()
        val workoutVm = WorkoutViewModel(
            repository = repository,
            timerController = timerController,
            todayEpochDay = 20770L
        )

        assertEquals(60_000L, WorkoutCalculator.getRestDurationMillis(WorkoutUiState.SET_TYPE_WARMUP))
        assertEquals(120_000L, WorkoutCalculator.getRestDurationMillis(WorkoutUiState.SET_TYPE_NORMAL))
        assertEquals(180_000L, WorkoutCalculator.getRestDurationMillis(WorkoutUiState.SET_TYPE_FINAL))

        val triggeredDurations = mutableListOf<Long>()
        workoutVm.startWorkout(currentMillis = 10_000_000L)
        workoutVm.onExerciseNameChanged("Bench Press")
        workoutVm.updateWeightInput("60")
        workoutVm.updateRepsInput("10")

        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_WARMUP)
        workoutVm.addSet { duration, _ -> triggeredDurations.add(duration) }
        assertEquals(60_000L, triggeredDurations.last())
        assertEquals("01:00", RestTimerController.formatMMSS(timerController.state.value.remainingSeconds))

        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        workoutVm.addSet { duration, _ -> triggeredDurations.add(duration) }
        assertEquals(120_000L, triggeredDurations.last())
        assertEquals("02:00", RestTimerController.formatMMSS(timerController.state.value.remainingSeconds))

        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_FINAL)
        workoutVm.addSet { duration, _ -> triggeredDurations.add(duration) }
        assertEquals(180_000L, triggeredDurations.last())
        assertEquals("03:00", RestTimerController.formatMMSS(timerController.state.value.remainingSeconds))

        var fakeNow = 50_000_000L
        timerController.startTimer(durationMillis = 120_000L, label = "Set 1 · Rest Timer", currentMillis = fakeNow)
        assertEquals(50_120_000L, timerController.state.value.endTimeMillis)

        fakeNow += 350L
        timerController.tick(fakeNow)
        assertEquals(120, timerController.state.value.remainingSeconds)

        fakeNow += 1_550L
        timerController.tick(fakeNow)
        assertEquals(119, timerController.state.value.remainingSeconds)

        fakeNow += 13_300L
        timerController.tick(fakeNow)
        assertEquals(105, timerController.state.value.remainingSeconds)

        timerController.adjustTimer(deltaSeconds = 15, currentMillis = fakeNow)
        assertEquals(120, timerController.state.value.remainingSeconds)
        timerController.adjustTimer(deltaSeconds = -15, currentMillis = fakeNow)
        assertEquals(105, timerController.state.value.remainingSeconds)

        var stopBridgeCalled = false
        workoutVm.skipOrDismissRestTimer { stopBridgeCalled = true }
        assertTrue(stopBridgeCalled)
        assertFalse(timerController.state.value.isRunning)
        assertFalse(timerController.state.value.alarmRinging)
        assertEquals(0, timerController.state.value.remainingSeconds)

        timerController.startTimer(durationMillis = 10_000L, label = "Warmup · Rest Timer", currentMillis = fakeNow)
        timerController.adjustTimer(deltaSeconds = -15, currentMillis = fakeNow)
        assertEquals(0, timerController.state.value.remainingSeconds)
        assertFalse(timerController.state.value.isRunning)
        assertTrue(timerController.state.value.alarmRinging)
    }

    @Test
    fun tc_s5_02_timer_background_ios_10s_elapsed_and_local_notification() {
        var fakeClockMillis = 100_000_000L
        var scheduledFireAtMillis: Long? = null
        var scheduledLabel: String? = null
        var firedNotifications = 0
        var foregroundAlerts = 0

        val scheduler = object : LocalTimerNotificationScheduler {
            override fun scheduleCompletion(delayMillis: Long, label: String) {
                scheduledFireAtMillis = fakeClockMillis + delayMillis
                scheduledLabel = label
            }

            override fun cancel(clearDelivered: Boolean) {
                scheduledFireAtMillis = null
            }
        }
        val audio = object : TimerAudioDuckingPort {
            override fun playDuckedCompletionAlert() {
                foregroundAlerts++
            }

            override fun stopAndUnduckAudio() = Unit
        }

        val controller = RestTimerController()
        val coordinator = BackgroundTimerCoordinator(
            controller = controller,
            notifications = scheduler,
            audio = audio,
            clockMillis = { fakeClockMillis }
        )

        coordinator.onStartTimer(durationMillis = 120_000L, label = "Set 1 · Rest Timer")
        assertEquals(120, controller.state.value.remainingSeconds)
        assertEquals(100_120_000L, scheduledFireAtMillis)

        coordinator.onWillResignActive()
        assertTrue(coordinator.isAppInForeground)

        fakeClockMillis += 5_000L
        assertTrue(coordinator.onForegroundTick())
        assertEquals(115, controller.state.value.remainingSeconds)

        coordinator.onDidEnterBackground()
        assertFalse(coordinator.isAppInForeground)
        assertEquals(100_120_000L, scheduledFireAtMillis)

        fakeClockMillis += 10_000L
        coordinator.onWillEnterForeground()
        assertTrue(coordinator.isAppInForeground)
        assertEquals(105, controller.state.value.remainingSeconds)
        assertTrue(controller.state.value.isRunning)
        assertEquals(0, foregroundAlerts)

        coordinator.onDidEnterBackground()
        fakeClockMillis = 100_125_000L
        val targetFireAt = scheduledFireAtMillis
        if (targetFireAt != null && fakeClockMillis >= targetFireAt) {
            firedNotifications++
            scheduledFireAtMillis = null
        }

        coordinator.onWillEnterForeground()
        assertEquals(1, firedNotifications)
        assertEquals("Set 1 · Rest Timer", scheduledLabel)
        assertFalse(controller.state.value.isRunning)
        assertTrue(controller.state.value.alarmRinging)
        assertEquals(0, controller.state.value.remainingSeconds)
        assertEquals(0, foregroundAlerts)
    }

    @Test
    fun tc_s5_03_audio_ducking_ios_temporary_duck_and_restore() {
        val wavBytes = buildRestCompletionWavBytes()
        assertTrue(wavBytes.size > 44)
        assertEquals("RIFF", wavBytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", wavBytes.copyOfRange(8, 12).decodeToString())
        assertEquals("fmt ", wavBytes.copyOfRange(12, 16).decodeToString())
        assertEquals("data", wavBytes.copyOfRange(36, 40).decodeToString())
        val sampleRate = (wavBytes[24].toInt() and 0xFF) or
            ((wavBytes[25].toInt() and 0xFF) shl 8) or
            ((wavBytes[26].toInt() and 0xFF) shl 16) or
            ((wavBytes[27].toInt() and 0xFF) shl 24)
        assertEquals(22_050, sampleRate)
        val hasAudioWaveform = (44 until wavBytes.size).any { wavBytes[it].toInt() != 0 }
        assertTrue(hasAudioWaveform)

        var musicPlaying = true
        var musicVolume = 1.0f
        var duckSessionActive = false
        var deactivateRetryCount = 0
        var simulateFirstDeactivateBusy = true

        val duckingPort = object : TimerAudioDuckingPort {
            override fun playDuckedCompletionAlert() {
                duckSessionActive = true
                if (musicPlaying) {
                    musicVolume = 0.3f
                }
            }

            override fun stopAndUnduckAudio() {
                if (!duckSessionActive) return
                duckSessionActive = false
                if (simulateFirstDeactivateBusy) {
                    simulateFirstDeactivateBusy = false
                    deactivateRetryCount++
                }
                if (musicPlaying) {
                    musicVolume = 1.0f
                }
            }
        }

        val controller = RestTimerController()
        var nowMillis = 200_000_000L
        val coordinator = BackgroundTimerCoordinator(
            controller = controller,
            notifications = object : LocalTimerNotificationScheduler {
                override fun scheduleCompletion(delayMillis: Long, label: String) = Unit
                override fun cancel(clearDelivered: Boolean) = Unit
            },
            audio = duckingPort,
            clockMillis = { nowMillis }
        )

        coordinator.onStartTimer(durationMillis = 60_000L, label = "Warmup · Rest Timer")
        assertEquals(1.0f, musicVolume)
        assertTrue(musicPlaying)

        nowMillis += 60_000L
        assertFalse(coordinator.onForegroundTick())
        assertTrue(controller.state.value.alarmRinging)
        assertTrue(duckSessionActive)
        assertTrue(musicPlaying)
        assertEquals(0.3f, musicVolume)

        coordinator.onStopTimer()
        assertFalse(duckSessionActive)
        assertEquals(1, deactivateRetryCount)
        assertTrue(musicPlaying)
        assertEquals(1.0f, musicVolume)
    }

    @Test
    fun tc_s5_04_json_backup_and_restore_round_trip_ios_and_android_v2() = runTest(testDispatcher) {
        val sourceDao = TestInMemoryGymDao()
        val profile = UserProfileEntity(
            uid = "uid-ios-roundtrip",
            name = "Alex Mercer",
            birthDateMillis = 946684800000L,
            heightCm = 180.0,
            weightKg = 76.5,
            currentStreak = 6,
            consecutiveMissed = 0,
            lastWorkoutEpochDay = 20770L,
            isGoogleLinked = false,
            isEmailLinked = true
        )
        val plans = listOf(
            WorkoutPlanEntity(dayOfWeek = 0, isRestDay = true, planName = "Rest Day"),
            WorkoutPlanEntity(dayOfWeek = 1, isRestDay = false, planName = "Push Day"),
            WorkoutPlanEntity(dayOfWeek = 2, isRestDay = true, planName = "Rest Day")
        )
        val history = listOf(
            ExerciseHistoryEntity(normalizedName = "Bench Press", muscleGroup = "CHEST", lastUsedMillis = 50_000L)
        )
        val sessions = listOf(
            WorkoutSessionEntity(
                sessionId = 10L,
                dateEpochDay = 20770L,
                planName = "Push Day",
                startTimeMillis = 10_000L,
                endTimeMillis = 1_810_000L,
                caloriesBurned = 220
            )
        )
        val sets = listOf(
            WorkoutSetEntity(
                setId = 100L,
                sessionId = 10L,
                exerciseName = "Bench Press",
                setType = "NORMAL",
                setOrder = 1,
                weightKg = 80.0,
                reps = 8,
                e1rm = 101.3,
                timestampMillis = 25_000L
            )
        )
        sourceDao.upsertUserProfile(profile)
        sourceDao.upsertWorkoutPlans(plans)
        sourceDao.upsertExerciseHistories(history)
        sourceDao.insertSessions(sessions)
        sourceDao.insertSets(sets)

        val iosExportedJson = GymBackupManager.exportToJson(sourceDao)
        assertTrue(iosExportedJson.contains("\"userProfile\""))
        assertTrue(iosExportedJson.contains("\"workoutPlans\""))
        assertTrue(iosExportedJson.contains("\"exerciseHistory\""))
        assertTrue(iosExportedJson.contains("\"workoutSessions\""))
        assertTrue(iosExportedJson.contains("\"workoutSets\""))

        val iosRestoreDao = TestInMemoryGymDao()
        assertTrue(GymBackupManager.importFromJson(iosRestoreDao, iosExportedJson))
        assertTrue(GymBackupManager.importFromJson(iosRestoreDao, iosExportedJson))
        assertEquals(profile, iosRestoreDao.getUserProfile())
        assertEquals(plans, iosRestoreDao.getWorkoutPlans())
        assertEquals(history, iosRestoreDao.getExerciseHistory())
        assertEquals(1, iosRestoreDao.getAllSessions().size)
        assertEquals(1, iosRestoreDao.getAllSets().size)

        val androidV2JsonWithProfile = """
            {"schemaVersion": 1,"userProfile":{"uid":"android-v2-uid","name":"Jordan","birthDateMillis":915148800000,"heightCm":175.0,"weightKg":72.0,"currentStreak":3,"consecutiveMissed":1,"lastWorkoutEpochDay":null,"isGoogleLinked":false,"isEmailLinked":true},"workoutPlans":[{"dayOfWeek":1,"isRestDay":false,"planName":"Upper Body"}],"exerciseHistory":[{"normalizedName":"Overhead Press","muscleGroup":"SHOULDERS","lastUsedMillis":123456}],"workoutSessions":[{"sessionId":1,"dateEpochDay":20750,"planName":"Upper Body","startTimeMillis":1000,"endTimeMillis":1801000,"caloriesBurned":189}],"workoutSets":[{"setId":1,"sessionId":1,"exerciseName":"Overhead Press","setType":"FINAL","setOrder":1,"weightKg":60.0,"reps":6,"e1rm":72.0,"timestampMillis":5000}]}
        """.trimIndent()
        val androidV2Dao = TestInMemoryGymDao()
        assertTrue(GymBackupManager.importFromJson(androidV2Dao, androidV2JsonWithProfile))
        assertEquals("Jordan", androidV2Dao.getUserProfile()?.name)
        assertEquals(1, androidV2Dao.getAllSessions().size)
        assertEquals(1, androidV2Dao.getAllSets().size)

        val androidV2JsonWithoutProfile = """
            {"schemaVersion": 1,"workoutPlans":[{"dayOfWeek":1,"isRestDay":false,"planName":"Push"}],"exerciseHistory":[],"workoutSessions":[{"sessionId":5,"dateEpochDay":20755,"planName":"Push","startTimeMillis":1000,"endTimeMillis":null,"caloriesBurned":0}],"workoutSets":[]}
        """.trimIndent()
        val noProfileDao = TestInMemoryGymDao()
        assertTrue(GymBackupManager.importFromJson(noProfileDao, androidV2JsonWithoutProfile))
        assertNull(noProfileDao.getUserProfile())
        assertEquals("Push", noProfileDao.getWorkoutPlans().single().planName)
        assertEquals(5L, noProfileDao.getAllSessions().single().sessionId)
    }

    @Test
    fun tc_s5_05_corrupted_or_invalid_json_error_feedback_and_data_safety() = runTest(testDispatcher) {
        val (dao, repository) = createEnvironment(backgroundScope, "s5_05_corrupt_json.preferences_pb")
        val existingProfile = UserProfileEntity(
            uid = "uid-keep-safe",
            name = "Safe Athlete",
            birthDateMillis = 946684800000L,
            heightCm = 176.0,
            weightKg = 72.0
        )
        val existingSession = WorkoutSessionEntity(
            sessionId = 99L,
            dateEpochDay = 20760L,
            planName = "Pull Day",
            startTimeMillis = 5_000L,
            endTimeMillis = 65_000L,
            caloriesBurned = 150
        )
        val existingSet = WorkoutSetEntity(
            setId = 999L,
            sessionId = 99L,
            exerciseName = "Lat Pulldown",
            setType = "NORMAL",
            setOrder = 1,
            weightKg = 60.0,
            reps = 10,
            e1rm = 80.0,
            timestampMillis = 10_000L
        )
        dao.upsertUserProfile(existingProfile)
        dao.upsertWorkoutPlans(listOf(WorkoutPlanEntity(dayOfWeek = 2, isRestDay = false, planName = "Pull Day")))
        dao.upsertExerciseHistories(listOf(ExerciseHistoryEntity("Lat Pulldown", "LATS", 10_000L)))
        dao.insertSessions(listOf(existingSession))
        dao.insertSets(listOf(existingSet))

        val corruptedPayloads = listOf(
            "",
            "{ broken json",
            "{}",
            """{"schemaVersion":0,"userProfile":null,"workoutPlans":[],"exerciseHistory":[],"workoutSessions":[],"workoutSets":[]}""",
            """{"schemaVersion":1,"userProfile":null,"workoutPlans":[],"exerciseHistory":[],"workoutSessions":[]}""",
            """{"schemaVersion":1,"userProfile":null,"workoutPlans":{},"exerciseHistory":[],"workoutSessions":[],"workoutSets":[]}""",
            """{"schemaVersion":1,"userProfile":null,"workoutPlans":[{"dayOfWeek":9,"isRestDay":false,"planName":"Bad"}],"exerciseHistory":[],"workoutSessions":[],"workoutSets":[]}""",
            """{"schemaVersion":1,"userProfile":{"uid":"u1","name":"Bad","birthDateMillis":946684800000,"heightCm":175.0,"weightKg":0.0},"workoutPlans":[],"exerciseHistory":[],"workoutSessions":[],"workoutSets":[]}""",
            """{"schemaVersion":1,"userProfile":null,"workoutPlans":[],"exerciseHistory":[],"workoutSessions":[],"workoutSets":[{"setId":1,"sessionId":404,"exerciseName":"Squat","setType":"NORMAL","setOrder":1,"weightKg":100.0,"reps":5,"e1rm":116.7,"timestampMillis":1000}]}"""
        )

        val expectedError = resolveBackupImportFeedback(restored = false)
        assertTrue(expectedError.isError)
        assertTrue(expectedError.message.contains("invalid or corrupted", ignoreCase = true))

        corruptedPayloads.forEach { badJson ->
            assertFalse(repository.importBackupJson(badJson))
            assertEquals(existingProfile, dao.getUserProfile())
            assertEquals(1, dao.getWorkoutPlans().size)
            assertEquals(1, dao.getExerciseHistory().size)
            assertEquals(listOf(99L), dao.getAllSessions().map { it.sessionId })
            assertEquals(listOf(999L), dao.getAllSets().map { it.setId })
        }

        val okFeedback = resolveBackupImportFeedback(restored = true)
        assertFalse(okFeedback.isError)
    }

    @Test
    fun tc_s5_06_offline_first_airplane_mode_all_daily_features() = runTest(testDispatcher) {
        val (_, repository) = createEnvironment(backgroundScope, "s5_06_airplane.preferences_pb")
        val airplaneBridge = object : AuthBridge {
            override val isGoogleSignInSupported: Boolean = false
            override val isCloudAuthConfigured: Boolean = false
            override fun syncEmailCredentials(email: String, password: String, isRegister: Boolean) {
                error("Network must not be called in Airplane Mode")
            }

            override suspend fun deliverOtp(email: String, otpCode: String) {
                error("Remote OTP must not be called in offline-first flow")
            }
        }

        val authVm = AuthOnboardingViewModel(repository = repository, authBridge = airplaneBridge)
        authVm.onOpeningAnimationFinished()
        assertEquals(AppStartDestination.WELCOME, authVm.uiState.value.destination)
        authVm.onBeginFromWelcome()
        assertEquals(AppStartDestination.SIGN_IN, authVm.uiState.value.destination)
        assertFalse(authVm.uiState.value.isCloudAuthConfigured)

        authVm.onContinueOffline()
        assertEquals(AppStartDestination.PROFILE_SETUP, authVm.uiState.value.destination)
        authVm.onSubmitProfile("Airplane Athlete", 946684800000L, "74.0", "178")
        assertEquals(AppStartDestination.MAIN_HOME, authVm.uiState.value.destination)

        val monday = LocalDate(2026, 11, 2)
        val planVm = WorkoutPlanViewModel(repository)
        planVm.startCreateOrEditPlan()
        planVm.toggleDaySelection(1)
        planVm.goToInputPlanPage()
        planVm.updateDraftPlanName(1, "Push Day")
        planVm.saveWorkoutPlan()

        val homeVm = HomeViewModel(repository = repository, currentDate = monday)
        assertEquals("Push Day", homeVm.uiState.value.selectedDayPlan.planName)
        assertFalse(homeVm.uiState.value.selectedDayPlan.isRestDay)

        val timerController = RestTimerController()
        val workoutVm = WorkoutViewModel(
            repository = repository,
            timerController = timerController,
            todayEpochDay = monday.toEpochDays().toLong()
        )
        workoutVm.startWorkout(currentMillis = 1_000_000L)
        workoutVm.onExerciseNameChanged("Bench Press")
        workoutVm.selectMuscleGroup("CHEST")
        workoutVm.updateWeightInput("80")
        workoutVm.updateRepsInput("8")
        workoutVm.selectSetType(WorkoutUiState.SET_TYPE_NORMAL)
        workoutVm.addSet()
        assertEquals(120, timerController.state.value.remainingSeconds)
        workoutVm.stopWorkout(planName = "Push Day")

        assertTrue(homeVm.uiState.value.todayCalories > 0)
        assertEquals(1, homeVm.uiState.value.currentStreak)

        val statsVm = StatsViewModel(repository)
        assertTrue(statsVm.uiState.value.hasStatisticsData)
        assertEquals("Bench Press", statsVm.uiState.value.selectedExerciseName)
        assertEquals(1, statsVm.uiState.value.muscleVolumes["CHEST"]?.weeklySets)
        statsVm.switchHeatmapOrientation(HeatmapOrientation.BACK)
        assertEquals("LATS", statsVm.uiState.value.selectedMuscleId)

        val exportedJson = authVm.exportBackupJson()
        val (restoredDao, restoredRepo) = createEnvironment(backgroundScope, "s5_06_restore.preferences_pb")
        assertTrue(restoredRepo.importBackupJson(exportedJson))
        assertEquals("Airplane Athlete", restoredDao.getUserProfile()?.name)
        assertEquals(1, restoredDao.getAllSessions().size)
        assertEquals(1, restoredDao.getAllSets().size)
        assertFalse(restoredDao.getWorkoutPlans().first { it.dayOfWeek == 1 }.isRestDay)
    }
}
