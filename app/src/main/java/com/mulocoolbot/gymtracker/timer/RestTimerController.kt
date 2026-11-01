package com.mulocoolbot.gymtracker.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RestTimerController {

    private val _state = MutableStateFlow(RestTimerState())
    val state: StateFlow<RestTimerState> = _state.asStateFlow()

    fun startTimer(
        durationMillis: Long,
        currentMillis: Long = System.currentTimeMillis()
    ) {
        val totalSeconds = (durationMillis / 1000L).toInt().coerceAtLeast(0)
        _state.value = RestTimerState(
            isRunning = true,
            totalSeconds = totalSeconds,
            remainingSeconds = totalSeconds,
            endTimeMillis = currentMillis + durationMillis,
            alarmRinging = false
        )
    }

    fun tick(currentMillis: Long = System.currentTimeMillis()) {
        val current = _state.value
        if (!current.isRunning) return

        val remainingSeconds = ((current.endTimeMillis - currentMillis + 999L) / 1000L)
            .toInt()
            .coerceAtLeast(0)

        if (remainingSeconds == 0) {
            _state.value = current.copy(
                isRunning = false,
                remainingSeconds = 0,
                alarmRinging = true
            )
        } else {
            _state.value = current.copy(remainingSeconds = remainingSeconds)
        }
    }

    fun dismissOrSkip() {
        _state.value = RestTimerState(
            isRunning = false,
            totalSeconds = 0,
            remainingSeconds = 0,
            endTimeMillis = 0L,
            alarmRinging = false
        )
    }

    fun formatMMSS(seconds: Int): String = Companion.formatMMSS(seconds)

    companion object {
        val instance: RestTimerController by lazy { RestTimerController() }

        fun formatMMSS(seconds: Int): String {
            val safeSeconds = seconds.coerceAtLeast(0)
            return "%02d:%02d".format(safeSeconds / 60, safeSeconds % 60)
        }
    }
}

