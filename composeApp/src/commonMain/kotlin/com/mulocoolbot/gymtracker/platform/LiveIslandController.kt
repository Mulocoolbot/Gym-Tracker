package com.mulocoolbot.gymtracker.platform

import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.WorkoutLiveIslandState
import kotlin.concurrent.Volatile

interface LiveIslandActionHandler {
    fun onStopRestTimerFromIsland()
    fun onStopWorkoutFromIsland()
}

interface LiveIslandController {
    fun syncState(state: WorkoutLiveIslandState)
    fun bindActionHandler(handler: LiveIslandActionHandler?)
}

object LiveIslandActionDispatcher {
    @Volatile
    private var handler: LiveIslandActionHandler? = null

    fun bind(handler: LiveIslandActionHandler?) {
        this.handler = handler
    }

    fun onStopRestTimerFromIsland() {
        val activeHandler = handler
        if (activeHandler != null) {
            activeHandler.onStopRestTimerFromIsland()
        } else {
            RestTimerController.instance.dismissOrSkip()
            runCatching { createPlatformRestTimerBridge().stopBackgroundTimer() }
        }
    }

    fun onStopWorkoutFromIsland() {
        handler?.onStopWorkoutFromIsland()
    }
}

expect fun createLiveIslandController(): LiveIslandController
