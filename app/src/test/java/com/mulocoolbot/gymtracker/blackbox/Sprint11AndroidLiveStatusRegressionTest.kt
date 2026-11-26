package com.mulocoolbot.gymtracker.blackbox

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Sprint11AndroidLiveStatusRegressionTest {

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
    fun androidServiceAndIosLiveActivity_preserveRaceGuardsAfterSprint11Fixes() {
        val root = resolveRepoRoot()

        val mainActivity = File(
            root,
            "app/src/main/java/com/mulocoolbot/gymtracker/MainActivity.kt"
        ).readText()
        assertTrue(mainActivity.contains("startRestTimer(ctx, durationMillis, label, appliedInMemory = true)"))
        assertTrue(mainActivity.contains("adjustRestTimer(ctx, deltaSeconds, appliedInMemory = true)"))

        val restTimerService = File(
            root,
            "app/src/main/java/com/mulocoolbot/gymtracker/timer/RestTimerService.kt"
        ).readText()
        assertTrue(restTimerService.contains("EXTRA_APPLIED_IN_MEMORY"))
        assertTrue(restTimerService.contains("if (appliedInMemory && !currentState.isRunning)"))
        assertTrue(restTimerService.contains("if (updatedState.alarmRinging)"))
        assertTrue(restTimerService.contains("if (!currentState.isRunning && !currentState.alarmRinging)"))

        val liveActivityManager = File(
            root,
            "iosApp/iosApp/MFitLiveActivityManager.swift"
        ).readText()
        assertTrue(liveActivityManager.contains("activitySyncTask"))
        assertTrue(liveActivityManager.contains("renderGeneration"))
        assertTrue(liveActivityManager.contains("enqueueActivityWork"))

        val widgetSwift = File(
            root,
            "iosApp/MFitLiveActivityExtension/MFitWorkoutLiveActivityWidget.swift"
        ).readText()
        assertTrue(widgetSwift.contains("timerEndDate.timeIntervalSinceNow > 0.25"))
        assertTrue(widgetSwift.contains("timerInterval: Date()...endDate, countsDown: true"))
    }
}
