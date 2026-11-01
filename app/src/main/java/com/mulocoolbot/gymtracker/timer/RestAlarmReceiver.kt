package com.mulocoolbot.gymtracker.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val controller = RestTimerController.instance
        val currentState = controller.state.value
        if (currentState.isRunning) {
            controller.tick(currentMillis = currentState.endTimeMillis)
        }

        val serviceIntent = Intent(context, RestTimerService::class.java).apply {
            action = RestTimerService.ACTION_TRIGGER_ALARM
        }
        runCatching {
            context.startForegroundService(serviceIntent)
        }.onFailure {
            runCatching { context.startService(serviceIntent) }
        }
    }
}

