package com.mulocoolbot.gymtracker.timer

data class RestTimerState(
    val isRunning: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val endTimeMillis: Long = 0L,
    val alarmRinging: Boolean = false
)

