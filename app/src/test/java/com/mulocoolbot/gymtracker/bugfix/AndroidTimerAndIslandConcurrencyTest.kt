package com.mulocoolbot.gymtracker.bugfix

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import com.mulocoolbot.gymtracker.platform.LiveIslandActionDispatcher
import com.mulocoolbot.gymtracker.platform.LiveIslandActionHandler
import com.mulocoolbot.gymtracker.timer.RestAlarmReceiver
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.RestTimerService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidTimerAndIslandConcurrencyTest {

    private class RecordingContext : ContextWrapper(null) {
        var startServiceCalls = 0
        var startForegroundCalls = 0

        override fun getPackageName(): String = "com.mulocoolbot.gymtracker"

        override fun startService(service: Intent?): ComponentName? {
            startServiceCalls++
            return null
        }

        override fun startForegroundService(service: Intent?): ComponentName? {
            startForegroundCalls++
            return null
        }
    }

    @Before
    fun setUp() {
        LiveIslandActionDispatcher.bind(null)
        RestTimerController.instance.dismissOrSkip()
    }

    @After
    fun tearDown() {
        LiveIslandActionDispatcher.bind(null)
        RestTimerController.instance.dismissOrSkip()
    }

    @Test
    fun liveIslandDispatcher_whenHandlerUnbound_dismissesTimerWithoutCrash() {
        RestTimerController.instance.startTimer(
            durationMillis = 60_000L,
            label = "Set 1 · Rest Timer",
            currentMillis = 1_000L
        )
        assertTrue(RestTimerController.instance.state.value.isRunning)

        LiveIslandActionDispatcher.onStopRestTimerFromIsland()

        val state = RestTimerController.instance.state.value
        assertFalse(state.isRunning)
        assertFalse(state.alarmRinging)
    }

    @Test
    fun liveIslandDispatcher_whenHandlerBound_delegatesStopToHandler() {
        var stoppedRest = 0
        LiveIslandActionDispatcher.bind(
            object : LiveIslandActionHandler {
                override fun onStopRestTimerFromIsland() {
                    stoppedRest++
                }

                override fun onStopWorkoutFromIsland() = Unit
            }
        )

        LiveIslandActionDispatcher.onStopRestTimerFromIsland()
        assertEquals(1, stoppedRest)
    }

    @Test
    fun restAlarmReceiver_whenTimerAlreadyStopped_skipsStartingService() {
        val context = RecordingContext()
        RestTimerController.instance.dismissOrSkip()

        RestAlarmReceiver().onReceive(context, null)

        assertEquals(0, context.startForegroundCalls)
        assertEquals(0, context.startServiceCalls)
    }

    @Test
    fun restTimerService_exposesAppliedInMemoryExtraConstant() {
        assertEquals(
            "com.mulocoolbot.gymtracker.extra.APPLIED_IN_MEMORY",
            RestTimerService.EXTRA_APPLIED_IN_MEMORY
        )
    }
}
