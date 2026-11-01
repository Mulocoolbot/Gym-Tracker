package com.mulocoolbot.gymtracker.timer

import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerControllerTest {

    @Test
    fun startTimer_setsTotalAndRemainingSecondsAndRunningState() {
        val controller = RestTimerController()

        controller.startTimer(durationMillis = 60_000L, currentMillis = 1_000_000L)
        val state = controller.state.value

        assertEquals(60, state.totalSeconds)
        assertEquals(60, state.remainingSeconds)
        assertEquals(1_060_000L, state.endTimeMillis)
        assertTrue(state.isRunning)
        assertFalse(state.alarmRinging)
    }

    @Test
    fun tick_afterBackgroundJumpUpdatesRemainingSecondsDirectly() {
        val controller = RestTimerController()
        controller.startTimer(durationMillis = 60_000L, currentMillis = 1_000_000L)

        controller.tick(currentMillis = 1_045_000L)
        val state = controller.state.value

        assertEquals(15, state.remainingSeconds)
        assertTrue(state.isRunning)
        assertFalse(state.alarmRinging)
    }

    @Test
    fun tick_atEndTimeTransitionsToZeroAndTriggersAlarm() {
        val controller = RestTimerController()
        controller.startTimer(durationMillis = 60_000L, currentMillis = 1_000_000L)

        controller.tick(currentMillis = 1_060_000L)
        val state = controller.state.value

        assertEquals(0, state.remainingSeconds)
        assertFalse(state.isRunning)
        assertTrue(state.alarmRinging)
    }

    @Test
    fun dismissOrSkip_resetsRunningAndRingingStatesToIdle() {
        val controller = RestTimerController()
        controller.startTimer(durationMillis = 120_000L, currentMillis = 1_000_000L)

        controller.dismissOrSkip()
        val skippedState = controller.state.value
        assertFalse(skippedState.isRunning)
        assertEquals(0, skippedState.remainingSeconds)
        assertFalse(skippedState.alarmRinging)

        controller.startTimer(durationMillis = 60_000L, currentMillis = 2_000_000L)
        controller.tick(currentMillis = 2_060_000L)
        assertTrue(controller.state.value.alarmRinging)

        controller.dismissOrSkip()
        val dismissedState = controller.state.value
        assertFalse(dismissedState.isRunning)
        assertEquals(0, dismissedState.remainingSeconds)
        assertFalse(dismissedState.alarmRinging)
    }

    @Test
    fun formatMMSS_formatsSecondsIntoTwoDigitMinutesAndSeconds() {
        val controller = RestTimerController()

        assertEquals("01:00", controller.formatMMSS(60))
        assertEquals("02:00", controller.formatMMSS(120))
        assertEquals("03:00", controller.formatMMSS(180))
        assertEquals("00:05", controller.formatMMSS(5))
    }

    @Test
    fun startTimer_withWorkoutCalculatorSetTypeDurationsStartsExpectedCountdowns() {
        val controller = RestTimerController()

        controller.startTimer(
            durationMillis = WorkoutCalculator.getRestDurationMillis("WARMUP"),
            currentMillis = 1_000_000L
        )
        assertEquals(60, controller.state.value.totalSeconds)
        assertEquals(60, controller.state.value.remainingSeconds)
        assertTrue(controller.state.value.isRunning)

        controller.startTimer(
            durationMillis = WorkoutCalculator.getRestDurationMillis("NORMAL"),
            currentMillis = 1_000_000L
        )
        assertEquals(120, controller.state.value.totalSeconds)
        assertEquals(120, controller.state.value.remainingSeconds)
        assertTrue(controller.state.value.isRunning)

        controller.startTimer(
            durationMillis = WorkoutCalculator.getRestDurationMillis("FINAL"),
            currentMillis = 1_000_000L
        )
        assertEquals(180, controller.state.value.totalSeconds)
        assertEquals(180, controller.state.value.remainingSeconds)
        assertTrue(controller.state.value.isRunning)
    }
}

