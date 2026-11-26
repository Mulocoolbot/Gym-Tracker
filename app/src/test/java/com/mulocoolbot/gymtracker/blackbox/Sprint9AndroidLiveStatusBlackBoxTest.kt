package com.mulocoolbot.gymtracker.blackbox

import android.content.Context
import android.content.ContextWrapper
import com.mulocoolbot.gymtracker.platform.AndroidPlatformBindings
import com.mulocoolbot.gymtracker.platform.AndroidPlatformContext
import com.mulocoolbot.gymtracker.platform.createLiveIslandController
import com.mulocoolbot.gymtracker.timer.LiveIslandActionReceiver
import com.mulocoolbot.gymtracker.timer.RestTimerService
import com.mulocoolbot.gymtracker.timer.WorkoutLiveIslandState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sprint9AndroidLiveStatusBlackBoxTest {

    private fun resolveRepoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "composeApp").isDirectory && File(dir, "iosApp").isDirectory) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Unable to locate repository root from ${File(".").absolutePath}")
    }

    @Test
    fun androidActionConstants_areStrictlySeparated_andControllerSkipsIdleInactiveSync() {
        assertNotEquals(
            LiveIslandActionReceiver.ACTION_STOP_REST_TIMER,
            LiveIslandActionReceiver.ACTION_STOP_WORKOUT
        )
        assertEquals(
            LiveIslandActionReceiver.ACTION_STOP_REST_TIMER,
            RestTimerService.ACTION_STOP_REST_TIMER
        )
        assertEquals(
            LiveIslandActionReceiver.ACTION_STOP_WORKOUT,
            RestTimerService.ACTION_STOP_WORKOUT
        )

        val fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        AndroidPlatformContext.init(fakeContext)

        val syncedStates = mutableListOf<WorkoutLiveIslandState>()
        val previousBinding = AndroidPlatformBindings.syncLiveIslandState
        try {
            AndroidPlatformBindings.syncLiveIslandState = { _, state ->
                syncedStates.add(state)
            }
            val controller = createLiveIslandController()

            controller.syncState(WorkoutLiveIslandState.Inactive)
            assertTrue(
                "Cold-start Inactive must not trigger foreground service notification",
                syncedStates.isEmpty()
            )

            val ongoing = WorkoutLiveIslandState.WorkoutOngoing("Workout Still Going")
            val timer = WorkoutLiveIslandState.RestTimerActive(endEpochMs = 1_900_000L, totalSeconds = 120)
            controller.syncState(ongoing)
            controller.syncState(timer)
            controller.syncState(WorkoutLiveIslandState.Inactive)
            controller.syncState(WorkoutLiveIslandState.Inactive)

            assertEquals(
                listOf(ongoing, timer, WorkoutLiveIslandState.Inactive),
                syncedStates
            )
        } finally {
            AndroidPlatformBindings.syncLiveIslandState = previousBinding
        }
    }

    @Test
    fun androidNotificationAndIosWidget_stopButtonsNeverCrossWireActions() {
        val root = resolveRepoRoot()

        val androidHelper = File(
            root,
            "app/src/main/java/com/mulocoolbot/gymtracker/timer/LiveUpdateNotificationHelper.kt"
        ).readText()
        val restTimerBlock = androidHelper.substringAfter("fun buildRestTimerNotification")
            .substringBefore("fun buildWorkoutOngoingNotification")
        val ongoingBlock = androidHelper.substringAfter("fun buildWorkoutOngoingNotification")
            .substringBefore("private fun buildApi36PromotedNotification")

        assertTrue(restTimerBlock.contains("buildStopRestTimerPendingIntent"))
        assertFalse(restTimerBlock.contains("buildStopWorkoutPendingIntent"))
        assertTrue(restTimerBlock.contains("setUsesChronometer(true)"))
        assertTrue(restTimerBlock.contains("setChronometerCountDown(true)"))

        assertTrue(ongoingBlock.contains("buildStopWorkoutPendingIntent"))
        assertFalse(ongoingBlock.contains("buildStopRestTimerPendingIntent"))
        assertTrue(ongoingBlock.contains("WorkoutLiveIslandState.DEFAULT_ONGOING_LABEL"))

        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("com.mulocoolbot.gymtracker.timer.LiveIslandActionReceiver".replace("com.mulocoolbot.gymtracker", "")) || manifest.contains(".timer.LiveIslandActionReceiver"))
        assertTrue(manifest.contains("POST_PROMOTED_NOTIFICATIONS"))

        val iosWidget = File(
            root,
            "iosApp/MFitLiveActivityExtension/MFitWorkoutLiveActivityWidget.swift"
        ).readText()
        assertTrue(iosWidget.contains("StopRestTimerIntent()"))
        assertTrue(iosWidget.contains("StopWorkoutIntent()"))
        assertTrue(iosWidget.contains("timerInterval: Date()...endDate, countsDown: true"))
        assertTrue(iosWidget.contains("monospacedDigit()"))
        assertTrue(iosWidget.contains("#0B0C0E") || iosWidget.contains("11.0 / 255.0"))
        assertTrue(iosWidget.contains("212.0 / 255.0"))

        val iosIntents = File(
            root,
            "iosApp/SharedLiveActivity/MFitLiveActivityIntents.swift"
        ).readText()
        val stopTimerIntentBlock = iosIntents.substringAfter("struct StopRestTimerIntent")
            .substringBefore("struct StopWorkoutIntent")
        val stopWorkoutIntentBlock = iosIntents.substringAfter("struct StopWorkoutIntent")

        assertTrue(stopTimerIntentBlock.contains("MFitLiveIslandActionBridge.onStopRestTimer?()"))
        assertTrue(stopTimerIntentBlock.contains("MFitWorkoutMode.workoutGoing"))
        assertFalse(stopTimerIntentBlock.contains("onStopWorkout"))

        assertTrue(stopWorkoutIntentBlock.contains("MFitLiveIslandActionBridge.onStopWorkout?()"))
        assertTrue(stopWorkoutIntentBlock.contains("dismissalPolicy: .immediate"))
        assertFalse(stopWorkoutIntentBlock.contains("onStopRestTimer"))

        val iosPlist = File(root, "iosApp/iosApp/Info.plist").readText()
        assertTrue(iosPlist.contains("NSSupportsLiveActivities"))
    }
}
