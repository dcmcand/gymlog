package com.gymlog.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gymlog.app.MainActivity
import com.gymlog.app.R

object RestTimerNotification {

    const val CHANNEL_ID = "rest_timer"
    const val NOTIFICATION_ID = 1
    const val EXTRA_SESSION_ID = "rest_timer_session_id"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Do NOT delete the channel here. deleteNotificationChannel throws
        // SecurityException ("Not allowed to delete channel ... with a foreground
        // service") whenever the rest-timer foreground service is using it, which
        // crashed the app when finishing a set (or relaunching) mid-timer (#28).
        // createNotificationChannel is a no-op when the channel already exists, so
        // it is safe to call on every launch and every timer start.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rest Timer",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Countdown during rest between sets"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context, endTimeMs: Long, sessionId: Long? = null): Notification {
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

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest Timer")
            .setContentText("Rest between sets")
            .setWhen(endTimeMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setRequestPromotedOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun buildCompletedNotification(context: Context, sessionId: Long? = null): Notification {
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

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest Complete")
            .setContentText("Time to start your next set")
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun show(context: Context, endTimeMs: Long, sessionId: Long? = null) {
        val notification = buildNotification(context, endTimeMs, sessionId)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }
}
