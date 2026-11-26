package com.mulocoolbot.gymtracker.platform

import com.mulocoolbot.gymtracker.timer.WorkoutLiveIslandState
import kotlin.concurrent.Volatile

private object AndroidLiveIslandController : LiveIslandController {
    @Volatile
    private var hasActiveSessionSynced: Boolean = false

    override fun syncState(state: WorkoutLiveIslandState) {
        if (state is WorkoutLiveIslandState.Inactive && !hasActiveSessionSynced) {
            return
        }
        hasActiveSessionSynced = state !is WorkoutLiveIslandState.Inactive
        runCatching {
            val context = AndroidPlatformContext.requireContext()
            AndroidPlatformBindings.syncLiveIslandState(context, state)
        }
    }

    override fun bindActionHandler(handler: LiveIslandActionHandler?) {
        LiveIslandActionDispatcher.bind(handler)
    }
}

actual fun createLiveIslandController(): LiveIslandController = AndroidLiveIslandController
