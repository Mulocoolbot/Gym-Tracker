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
import androidx.core.content.ContextCompat
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
    private var alarmSilenceJob: Job? = null
    private var lastNotifiedSeconds: Int = -1
    private var lastSyncedEndTimeMillis: Long = 0L
    private var isWorkoutOngoing: Boolean = false
    private var ongoingLabel: String = WorkoutLiveIslandState.DEFAULT_ONGOING_LABEL

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
                val label = intent.getStringExtra(EXTRA_TIMER_LABEL) ?: "Rest Timer"
                val appliedInMemory = intent.getBooleanExtra(EXTRA_APPLIED_IN_MEMORY, false)
                isWorkoutOngoing = true
                handleStartTimer(durationMillis, label, appliedInMemory)
            }
            ACTION_ADJUST_TIMER -> {
                val deltaSeconds = intent.getIntExtra(EXTRA_DELTA_SECONDS, 0)
                val appliedInMemory = intent.getBooleanExtra(EXTRA_APPLIED_IN_MEMORY, false)
                handleAdjustTimer(deltaSeconds, appliedInMemory)
            }
            ACTION_SYNC_LIVE_ISLAND -> handleSyncLiveIsland(intent)
            ACTION_TRIGGER_ALARM -> {
                val expectedEndEpochMs = intent.getLongExtra(EXTRA_END_EPOCH_MS, 0L)
                handleTriggerAlarm(expectedEndEpochMs)
            }
            ACTION_STOP_REST_TIMER, ACTION_DISMISS_ALARM -> handleStopRestTimerOnly()
            ACTION_STOP_WORKOUT -> handleStopWorkoutAndReset()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        alarmSilenceJob?.cancel()
        alarmPlayer.stopAlarm()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleSyncLiveIsland(intent: Intent) {
        when (intent.getStringExtra(EXTRA_ISLAND_MODE)) {
            WorkoutLiveIslandState.MODE_INACTIVE -> handleStopWorkoutAndReset()
            WorkoutLiveIslandState.MODE_WORKOUT_GOING -> {
                isWorkoutOngoing = true
                ongoingLabel = intent.getStringExtra(EXTRA_TIMER_LABEL)
                    ?.takeIf { it.isNotBlank() }
                    ?: WorkoutLiveIslandState.DEFAULT_ONGOING_LABEL
                tickerJob?.cancel()
                cancelBackupAlarm()
                lastSyncedEndTimeMillis = 0L
                val notification = LiveUpdateNotificationHelper.buildWorkoutOngoingNotification(
                    context = this,
                    channelId = CHANNEL_ID_LIVE_ISLAND,
                    label = ongoingLabel
                )
                promoteToForeground(notification)
            }
            WorkoutLiveIslandState.MODE_REST_TIMER -> {
                isWorkoutOngoing = true
                val endEpochMs = intent.getLongExtra(EXTRA_END_EPOCH_MS, 0L)
                val totalSeconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, 0)
                val now = System.currentTimeMillis()
                val remainingMillis = (endEpochMs - now).coerceAtLeast(0L)
                val currentState = controller.state.value
                if (remainingMillis <= 0L || (!currentState.isRunning && !currentState.alarmRinging)) {
                    handleStopRestTimerOnly()
                    return
                }
                if (currentState.endTimeMillis != endEpochMs) {
                    handleStopRestTimerOnly()
                    return
                }
                val activeState = controller.state.value
                val resolvedTotal = maxOf(activeState.totalSeconds, totalSeconds)
                lastNotifiedSeconds = activeState.remainingSeconds
                lastSyncedEndTimeMillis = activeState.endTimeMillis

                scheduleBackupAlarm(activeState.endTimeMillis)
                val notification = LiveUpdateNotificationHelper.buildRestTimerNotification(
                    context = this,
                    channelId = CHANNEL_ID_LIVE_ISLAND,
                    endEpochMs = activeState.endTimeMillis,
                    remainingSeconds = activeState.remainingSeconds.coerceAtMost(resolvedTotal.coerceAtLeast(activeState.remainingSeconds))
                )
                promoteToForeground(notification)
                if (tickerJob?.isActive != true) {
                    startTicker()
                }
            }
        }
    }

    private fun handleStartTimer(
        durationMillis: Long,
        label: String,
        appliedInMemory: Boolean
    ) {
        tickerJob?.cancel()
        alarmSilenceJob?.cancel()
        alarmPlayer.stopAlarm()

        val currentState = controller.state.value
        if (appliedInMemory && !currentState.isRunning) {
            handleStopRestTimerOnly()
            return
        }
        if (!appliedInMemory && (!currentState.isRunning || currentState.endTimeMillis == lastSyncedEndTimeMillis)) {
            val now = System.currentTimeMillis()
            controller.startTimer(
                durationMillis = durationMillis,
                label = label,
                currentMillis = now
            )
        }
        val activeState = controller.state.value
        if (!activeState.isRunning) {
            handleStopRestTimerOnly()
            return
        }
        lastNotifiedSeconds = activeState.remainingSeconds
        lastSyncedEndTimeMillis = activeState.endTimeMillis

        scheduleBackupAlarm(activeState.endTimeMillis)
        val notification = LiveUpdateNotificationHelper.buildRestTimerNotification(
            context = this,
            channelId = CHANNEL_ID_LIVE_ISLAND,
            endEpochMs = activeState.endTimeMillis,
            remainingSeconds = activeState.remainingSeconds
        )
        promoteToForeground(notification)
        startTicker()
    }

    private fun handleAdjustTimer(
        deltaSeconds: Int,
        appliedInMemory: Boolean
    ) {
        if (deltaSeconds == 0) return

        val beforeState = controller.state.value
        if (!appliedInMemory && beforeState.isRunning && beforeState.endTimeMillis == lastSyncedEndTimeMillis) {
            controller.adjustTimer(deltaSeconds = deltaSeconds)
        }

        val updatedState = controller.state.value
        lastSyncedEndTimeMillis = updatedState.endTimeMillis

        if (updatedState.alarmRinging) {
            cancelBackupAlarm()
            handleTriggerAlarm(updatedState.endTimeMillis)
        } else if (!updatedState.isRunning || updatedState.remainingSeconds == 0) {
            cancelBackupAlarm()
            handleStopRestTimerOnly()
        } else {
            lastNotifiedSeconds = updatedState.remainingSeconds
            scheduleBackupAlarm(updatedState.endTimeMillis)
            notificationManager.notify(
                NOTIFICATION_ID,
                LiveUpdateNotificationHelper.buildRestTimerNotification(
                    context = this,
                    channelId = CHANNEL_ID_LIVE_ISLAND,
                    endEpochMs = updatedState.endTimeMillis,
                    remainingSeconds = updatedState.remainingSeconds
                )
            )
        }
    }

    private fun startTicker() {
        tickerJob = serviceScope.launch {
            while (controller.state.value.isRunning) {
                delay(250L)
                controller.tick()
                val currentState = controller.state.value
                if (currentState.alarmRinging) {
                    handleTriggerAlarm(currentState.endTimeMillis)
                    break
                }
                if (currentState.isRunning && currentState.remainingSeconds != lastNotifiedSeconds) {
                    lastNotifiedSeconds = currentState.remainingSeconds
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        LiveUpdateNotificationHelper.buildRestTimerNotification(
                            context = this@RestTimerService,
                            channelId = CHANNEL_ID_LIVE_ISLAND,
                            endEpochMs = currentState.endTimeMillis,
                            remainingSeconds = currentState.remainingSeconds
                        )
                    )
                }
            }
        }
    }

    private fun handleTriggerAlarm(expectedEndEpochMs: Long = 0L) {
        tickerJob?.cancel()
        alarmSilenceJob?.cancel()
        val currentState = controller.state.value
        if (!currentState.isRunning && !currentState.alarmRinging) {
            handleStopRestTimerOnly()
            return
        }
        if (expectedEndEpochMs > 0L &&
            currentState.endTimeMillis != 0L &&
            currentState.endTimeMillis != expectedEndEpochMs
        ) {
            return
        }
        if (currentState.isRunning) {
            controller.tick(currentMillis = currentState.endTimeMillis)
        }
        alarmPlayer.playAlarm()

        if (isWorkoutOngoing) {
            lastSyncedEndTimeMillis = 0L
            val ongoingNotification = LiveUpdateNotificationHelper.buildWorkoutOngoingNotification(
                context = this,
                channelId = CHANNEL_ID_LIVE_ISLAND,
                label = ongoingLabel
            )
            promoteToForeground(ongoingNotification)
            alarmSilenceJob = serviceScope.launch {
                delay(3_000L)
                alarmPlayer.stopAlarm()
            }
        } else {
            val alarmNotification = buildFinishedNotification()
            promoteToForeground(alarmNotification)
        }
    }

    private fun handleStopRestTimerOnly() {
        tickerJob?.cancel()
        alarmSilenceJob?.cancel()
        cancelBackupAlarm()
        alarmPlayer.stopAlarm()
        controller.dismissOrSkip()
        lastSyncedEndTimeMillis = 0L

        if (isWorkoutOngoing) {
            val notification = LiveUpdateNotificationHelper.buildWorkoutOngoingNotification(
                context = this,
                channelId = CHANNEL_ID_LIVE_ISLAND,
                label = ongoingLabel
            )
            promoteToForeground(notification)
        } else {
            notificationManager.cancel(NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleStopWorkoutAndReset() {
        isWorkoutOngoing = false
        tickerJob?.cancel()
        alarmSilenceJob?.cancel()
        cancelBackupAlarm()
        alarmPlayer.stopAlarm()
        controller.dismissOrSkip()
        lastSyncedEndTimeMillis = 0L
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
            .setColor(0xFFD4FF00.toInt())
            .setContentTitle("Rest Finished")
            .setContentText("00:00")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(R.drawable.ic_island_stop, "Dismiss", dismissPendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        val liveIslandChannel = NotificationChannel(
            CHANNEL_ID_LIVE_ISLAND,
            "Workout Live Status",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            "Rest Timer Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannels(listOf(liveIslandChannel, alarmChannel))
    }

    companion object {
        const val ACTION_START_TIMER = "com.mulocoolbot.gymtracker.action.START_TIMER"
        const val ACTION_ADJUST_TIMER = "com.mulocoolbot.gymtracker.action.ADJUST_TIMER"
        const val ACTION_DISMISS_ALARM = "com.mulocoolbot.gymtracker.action.DISMISS_ALARM"
        const val ACTION_TRIGGER_ALARM = "com.mulocoolbot.gymtracker.action.TRIGGER_ALARM"
        const val ACTION_SYNC_LIVE_ISLAND = "com.mulocoolbot.gymtracker.action.SYNC_LIVE_ISLAND"
        const val ACTION_STOP_REST_TIMER = LiveIslandActionReceiver.ACTION_STOP_REST_TIMER
        const val ACTION_STOP_WORKOUT = LiveIslandActionReceiver.ACTION_STOP_WORKOUT

        const val EXTRA_DURATION_MILLIS = "com.mulocoolbot.gymtracker.extra.DURATION_MILLIS"
        const val EXTRA_TIMER_LABEL = "com.mulocoolbot.gymtracker.extra.TIMER_LABEL"
        const val EXTRA_DELTA_SECONDS = "com.mulocoolbot.gymtracker.extra.DELTA_SECONDS"
        const val EXTRA_ISLAND_MODE = "com.mulocoolbot.gymtracker.extra.ISLAND_MODE"
        const val EXTRA_END_EPOCH_MS = "com.mulocoolbot.gymtracker.extra.END_EPOCH_MS"
        const val EXTRA_TOTAL_SECONDS = "com.mulocoolbot.gymtracker.extra.TOTAL_SECONDS"
        const val EXTRA_APPLIED_IN_MEMORY = "com.mulocoolbot.gymtracker.extra.APPLIED_IN_MEMORY"

        private const val CHANNEL_ID_LIVE_ISLAND = "mfit_workout_live_status_channel"
        private const val CHANNEL_ID_ALARM = "rest_timer_alarm_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 2001
        private const val DISMISS_REQUEST_CODE = 2003

        fun syncLiveIslandState(context: Context, state: WorkoutLiveIslandState) {
            when (state) {
                is WorkoutLiveIslandState.Inactive -> stopWorkoutLiveIsland(context)
                is WorkoutLiveIslandState.WorkoutOngoing -> {
                    val intent = Intent(context, RestTimerService::class.java).apply {
                        action = ACTION_SYNC_LIVE_ISLAND
                        putExtra(EXTRA_ISLAND_MODE, WorkoutLiveIslandState.MODE_WORKOUT_GOING)
                        putExtra(EXTRA_TIMER_LABEL, state.label)
                    }
                    startForegroundCompat(context, intent)
                }
                is WorkoutLiveIslandState.RestTimerActive -> {
                    val intent = Intent(context, RestTimerService::class.java).apply {
                        action = ACTION_SYNC_LIVE_ISLAND
                        putExtra(EXTRA_ISLAND_MODE, WorkoutLiveIslandState.MODE_REST_TIMER)
                        putExtra(EXTRA_END_EPOCH_MS, state.endEpochMs)
                        putExtra(EXTRA_TOTAL_SECONDS, state.totalSeconds)
                    }
                    startForegroundCompat(context, intent)
                }
            }
        }

        fun startRestTimer(
            context: Context,
            durationMillis: Long,
            label: String = "Rest Timer",
            appliedInMemory: Boolean = false
        ) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START_TIMER
                putExtra(EXTRA_DURATION_MILLIS, durationMillis)
                putExtra(EXTRA_TIMER_LABEL, label)
                putExtra(EXTRA_APPLIED_IN_MEMORY, appliedInMemory)
            }
            startForegroundCompat(context, intent)
        }

        fun adjustRestTimer(
            context: Context,
            deltaSeconds: Int,
            appliedInMemory: Boolean = false
        ) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_ADJUST_TIMER
                putExtra(EXTRA_DELTA_SECONDS, deltaSeconds)
                putExtra(EXTRA_APPLIED_IN_MEMORY, appliedInMemory)
            }
            runCatching { context.startService(intent) }
        }

        fun stopRestTimer(context: Context) {
            stopRestTimerOnly(context)
        }

        fun stopRestTimerOnly(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_STOP_REST_TIMER
            }
            runCatching { context.startService(intent) }
        }

        fun stopWorkoutLiveIsland(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_STOP_WORKOUT
            }
            runCatching { context.startService(intent) }
        }

        private fun startForegroundCompat(context: Context, intent: Intent) {
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                runCatching { context.startService(intent) }
            }
        }
    }
}
