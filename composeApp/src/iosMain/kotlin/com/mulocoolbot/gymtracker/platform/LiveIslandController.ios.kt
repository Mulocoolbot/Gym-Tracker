package com.mulocoolbot.gymtracker.platform

import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.WorkoutLiveIslandState
import com.mulocoolbot.gymtracker.timer.reconcileAt
import com.mulocoolbot.gymtracker.util.currentEpochMillis

interface IosLiveIslandRenderer {
    fun render(mode: String, endEpochMs: Long, statusText: String)
}

object LiveIslandIosBridge {
    private var renderer: IosLiveIslandRenderer? = null
    private var lastState: WorkoutLiveIslandState = WorkoutLiveIslandState.Inactive

    fun registerRenderer(renderer: IosLiveIslandRenderer?) {
        this.renderer = renderer
        renderState(lastState)
    }

    fun dispatchStopRestTimerFromIsland() {
        LiveIslandActionDispatcher.onStopRestTimerFromIsland()
        if (lastState is WorkoutLiveIslandState.RestTimerActive) {
            lastState = WorkoutLiveIslandState.WorkoutOngoing()
        }
    }

    fun dispatchStopWorkoutFromIsland() {
        LiveIslandActionDispatcher.onStopWorkoutFromIsland()
        lastState = WorkoutLiveIslandState.Inactive
    }

    fun reconcileOnLifecycleEvent() {
        val nowMs = currentEpochMillis()
        RestTimerController.instance.tick(nowMs)
        lastState = lastState.reconcileAt(nowMs)
        renderState(lastState)
    }

    internal fun updateState(state: WorkoutLiveIslandState) {
        lastState = state
        renderState(state)
    }

    private fun renderState(state: WorkoutLiveIslandState) {
        val target = renderer ?: return
        when (state) {
            is WorkoutLiveIslandState.Inactive -> {
                target.render(WorkoutLiveIslandState.MODE_INACTIVE, 0L, "")
            }
            is WorkoutLiveIslandState.RestTimerActive -> {
                target.render(
                    WorkoutLiveIslandState.MODE_REST_TIMER,
                    state.endEpochMs,
                    WorkoutLiveIslandState.DEFAULT_ONGOING_LABEL
                )
            }
            is WorkoutLiveIslandState.WorkoutOngoing -> {
                target.render(WorkoutLiveIslandState.MODE_WORKOUT_GOING, 0L, state.label)
            }
        }
    }
}

private object IosLiveIslandController : LiveIslandController {
    override fun syncState(state: WorkoutLiveIslandState) {
        LiveIslandIosBridge.updateState(state)
    }

    override fun bindActionHandler(handler: LiveIslandActionHandler?) {
        LiveIslandActionDispatcher.bind(handler)
    }
}

actual fun createLiveIslandController(): LiveIslandController = IosLiveIslandController
