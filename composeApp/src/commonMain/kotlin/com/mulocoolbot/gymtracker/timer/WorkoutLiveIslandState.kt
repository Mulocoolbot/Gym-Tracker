package com.mulocoolbot.gymtracker.timer

sealed interface WorkoutLiveIslandState {
    data object Inactive : WorkoutLiveIslandState

    data class RestTimerActive(
        val endEpochMs: Long,
        val totalSeconds: Int
    ) : WorkoutLiveIslandState

    data class WorkoutOngoing(
        val label: String = DEFAULT_ONGOING_LABEL
    ) : WorkoutLiveIslandState

    companion object {
        const val DEFAULT_ONGOING_LABEL = "Workout Still Going"
        const val MODE_INACTIVE = "INACTIVE"
        const val MODE_REST_TIMER = "REST_TIMER"
        const val MODE_WORKOUT_GOING = "WORKOUT_GOING"
    }
}

fun WorkoutLiveIslandState.reconcileAt(nowEpochMs: Long): WorkoutLiveIslandState {
    return if (this is WorkoutLiveIslandState.RestTimerActive && endEpochMs <= nowEpochMs) {
        WorkoutLiveIslandState.WorkoutOngoing()
    } else {
        this
    }
}

fun resolveWorkoutLiveIslandState(
    isWorkoutActive: Boolean,
    restTimerState: RestTimerState,
    nowEpochMs: Long = Long.MIN_VALUE
): WorkoutLiveIslandState {
    if (!isWorkoutActive) {
        return WorkoutLiveIslandState.Inactive
    }
    val hasActiveCountdown = restTimerState.isRunning &&
        restTimerState.endTimeMillis > 0L &&
        (nowEpochMs == Long.MIN_VALUE || restTimerState.endTimeMillis > nowEpochMs)

    return if (hasActiveCountdown) {
        WorkoutLiveIslandState.RestTimerActive(
            endEpochMs = restTimerState.endTimeMillis,
            totalSeconds = restTimerState.totalSeconds
        )
    } else {
        WorkoutLiveIslandState.WorkoutOngoing()
    }
}
