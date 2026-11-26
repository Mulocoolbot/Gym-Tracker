package com.mulocoolbot.gymtracker.timer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mulocoolbot.gymtracker.platform.LiveIslandActionDispatcher

class LiveIslandActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP_REST_TIMER -> {
                LiveIslandActionDispatcher.onStopRestTimerFromIsland()
                RestTimerService.stopRestTimerOnly(context)
            }
            ACTION_STOP_WORKOUT -> {
                LiveIslandActionDispatcher.onStopWorkoutFromIsland()
                RestTimerService.stopWorkoutLiveIsland(context)
            }
        }
    }

    companion object {
        const val ACTION_STOP_REST_TIMER = "com.mulocoolbot.gymtracker.action.STOP_REST_TIMER"
        const val ACTION_STOP_WORKOUT = "com.mulocoolbot.gymtracker.action.STOP_WORKOUT"

        private const val REQUEST_CODE_STOP_REST_TIMER = 3001
        private const val REQUEST_CODE_STOP_WORKOUT = 3002

        fun buildStopRestTimerPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, LiveIslandActionReceiver::class.java).apply {
                action = ACTION_STOP_REST_TIMER
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_STOP_REST_TIMER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun buildStopWorkoutPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, LiveIslandActionReceiver::class.java).apply {
                action = ACTION_STOP_WORKOUT
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_STOP_WORKOUT,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
