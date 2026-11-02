package com.mulocoolbot.gymtracker.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mulocoolbot.gymtracker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RestTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller = RestTimerController.instance

    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmPlayer: GymAlarmPlayer

    private var tickerJob: Job? = null
    private var lastNotifiedSeconds: Int = -1

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmPlayer = GymAlarmPlayer(this)
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val durationMillis = intent.getLongExtra(EXTRA_DURATION_MILLIS, 120_000L)
                handleStartTimer(durationMillis)
            }
            ACTION_TRIGGER_ALARM -> handleTriggerAlarm()
            ACTION_SKIP_TIMER, ACTION_DISMISS_ALARM -> handleStopAndReset()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        alarmPlayer.stopAlarm()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStartTimer(durationMillis: Long) {
        tickerJob?.cancel()
        alarmPlayer.stopAlarm()

        val now = System.currentTimeMillis()
        controller.startTimer(durationMillis = durationMillis, currentMillis = now)
        val initialState = controller.state.value
        lastNotifiedSeconds = initialState.remainingSeconds

        scheduleBackupAlarm(initialState.endTimeMillis)
        val notification = buildCountdownNotification(initialState.remainingSeconds)
        promoteToForeground(notification)
        startTicker()
    }

    private fun startTicker() {
        tickerJob = serviceScope.launch {
            while (controller.state.value.isRunning) {
                delay(250L)
                controller.tick()
                val currentState = controller.state.value
                if (currentState.alarmRinging) {
                    handleTriggerAlarm()
                    break
                }
                if (currentState.isRunning && currentState.remainingSeconds != lastNotifiedSeconds) {
                    lastNotifiedSeconds = currentState.remainingSeconds
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildCountdownNotification(currentState.remainingSeconds)
                    )
                }
            }
        }
    }

    private fun handleTriggerAlarm() {
        tickerJob?.cancel()
        val currentState = controller.state.value
        if (currentState.isRunning) {
            controller.tick(currentMillis = currentState.endTimeMillis)
        }
        alarmPlayer.playAlarm()
        val alarmNotification = buildFinishedNotification()
        promoteToForeground(alarmNotification)
    }

    private fun handleStopAndReset() {
        tickerJob?.cancel()
        cancelBackupAlarm()
        alarmPlayer.stopAlarm()
        controller.dismissOrSkip()
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promoteToForeground(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun scheduleBackupAlarm(triggerAtMillis: Long) {
        val pendingIntent = buildAlarmPendingIntent()
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        runCatching {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }

    private fun cancelBackupAlarm() {
        runCatching {
            alarmManager.cancel(buildAlarmPendingIntent())
        }
    }

    private fun buildAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, RestAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
        }
        return PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCountdownNotification(remainingSeconds: Int): Notification {
        val skipIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_SKIP_TIMER
        }
        val skipPendingIntent = PendingIntent.getService(
            this,
            SKIP_REQUEST_CODE,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle("Rest Timer")
            .setContentText(RestTimerController.formatMMSS(remainingSeconds))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(0, "Skip", skipPendingIntent)
            .build()
    }

    private fun buildFinishedNotification(): Notification {
        val dismissIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            DISMISS_REQUEST_CODE,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle("Rest Finished")
            .setContentText("00:00")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        val countdownChannel = NotificationChannel(
            CHANNEL_ID_COUNTDOWN,
            "Rest Timer Countdown",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            "Rest Timer Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(true)
        }

        notificationManager.createNotificationChannels(listOf(countdownChannel, alarmChannel))
    }

    companion object {
        const val ACTION_START_TIMER = "com.mulocoolbot.gymtracker.action.START_TIMER"
        const val ACTION_SKIP_TIMER = "com.mulocoolbot.gymtracker.action.SKIP_TIMER"
        const val ACTION_DISMISS_ALARM = "com.mulocoolbot.gymtracker.action.DISMISS_ALARM"
        const val ACTION_TRIGGER_ALARM = "com.mulocoolbot.gymtracker.action.TRIGGER_ALARM"
        const val EXTRA_DURATION_MILLIS = "com.mulocoolbot.gymtracker.extra.DURATION_MILLIS"

        private const val CHANNEL_ID_COUNTDOWN = "rest_timer_countdown_channel"
        private const val CHANNEL_ID_ALARM = "rest_timer_alarm_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 2001
        private const val SKIP_REQUEST_CODE = 2002
        private const val DISMISS_REQUEST_CODE = 2003

        fun startTimer(context: Context, durationMillis: Long) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START_TIMER
                putExtra(EXTRA_DURATION_MILLIS, durationMillis)
            }
            context.startForegroundService(intent)
        }

        fun skipTimer(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_SKIP_TIMER
            }
            context.startService(intent)
        }

        fun dismissAlarm(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_DISMISS_ALARM
            }
            context.startService(intent)
        }
    }
}

