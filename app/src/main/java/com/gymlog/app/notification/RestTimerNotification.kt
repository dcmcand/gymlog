package com.gymlog.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gymlog.app.MainActivity
import com.gymlog.app.R

object RestTimerNotification {

    private const val CHANNEL_ID = "rest_timer"
    private const val NOTIFICATION_ID = 1
    const val EXTRA_SESSION_ID = "rest_timer_session_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rest Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Countdown during rest between sets"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, endTimeMs: Long, sessionId: Long? = null) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest Timer")
            .setContentText("Rest between sets")
            .setWhen(endTimeMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }
}
