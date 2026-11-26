package com.mulocoolbot.gymtracker.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mulocoolbot.gymtracker.MainActivity
import com.mulocoolbot.gymtracker.R

internal object LiveUpdateNotificationHelper {

    private const val VOLT_ACCENT_ARGB = 0xFFD4FF00.toInt()
    private const val REQUEST_CODE_OPEN_APP = 3000

    fun buildRestTimerNotification(
        context: Context,
        channelId: String,
        endEpochMs: Long,
        remainingSeconds: Int,
        title: String = "MFit · Rest Timer"
    ): Notification {
        val formattedTime = RestTimerController.formatMMSS(remainingSeconds)
        val stopPendingIntent = LiveIslandActionReceiver.buildStopRestTimerPendingIntent(context)
        val contentIntent = buildOpenAppPendingIntent(context)

        if (Build.VERSION.SDK_INT >= 36) {
            buildApi36PromotedNotification(
                context = context,
                channelId = channelId,
                title = title,
                contentText = formattedTime,
                shortCriticalText = formattedTime,
                category = Notification.CATEGORY_STOPWATCH,
                useChronometer = true,
                whenMillis = endEpochMs,
                stopPendingIntent = stopPendingIntent,
                contentIntent = contentIntent
            )?.let { return it }
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(VOLT_ACCENT_ARGB)
            .setContentTitle(title)
            .setContentText(formattedTime)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(endEpochMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .addAction(R.drawable.ic_island_stop, "■ Stop", stopPendingIntent)
            .build()
    }

    fun buildWorkoutOngoingNotification(
        context: Context,
        channelId: String,
        label: String = WorkoutLiveIslandState.DEFAULT_ONGOING_LABEL
    ): Notification {
        val stopPendingIntent = LiveIslandActionReceiver.buildStopWorkoutPendingIntent(context)
        val contentIntent = buildOpenAppPendingIntent(context)

        if (Build.VERSION.SDK_INT >= 36) {
            buildApi36PromotedNotification(
                context = context,
                channelId = channelId,
                title = "MFit",
                contentText = label,
                shortCriticalText = "MFit",
                category = Notification.CATEGORY_WORKOUT,
                useChronometer = false,
                whenMillis = 0L,
                stopPendingIntent = stopPendingIntent,
                contentIntent = contentIntent
            )?.let { return it }
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(VOLT_ACCENT_ARGB)
            .setContentTitle("MFit")
            .setContentText(label)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .addAction(R.drawable.ic_island_stop, "■ Stop", stopPendingIntent)
            .build()
    }

    private fun buildApi36PromotedNotification(
        context: Context,
        channelId: String,
        title: String,
        contentText: String,
        shortCriticalText: String,
        category: String,
        useChronometer: Boolean,
        whenMillis: Long,
        stopPendingIntent: PendingIntent,
        contentIntent: PendingIntent
    ): Notification? = runCatching {
        val action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_island_stop),
            "■ Stop",
            stopPendingIntent
        ).build()

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(VOLT_ACCENT_ARGB)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(category)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(useChronometer)
            .setUsesChronometer(useChronometer)
            .addAction(action)

        if (useChronometer && whenMillis > 0L) {
            builder.setWhen(whenMillis)
            builder.setChronometerCountDown(true)
        }

        // Android 16 (API 36) Live Updates promotion APIs invoked reflectively to stay compatible with compileSdk 35.
        Notification.Builder::class.java
            .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
            .invoke(builder, true)
        Notification.Builder::class.java
            .getMethod("setShortCriticalText", CharSequence::class.java)
            .invoke(builder, shortCriticalText)

        builder.build()
    }.getOrNull()

    private fun buildOpenAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
