package com.gymlog.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.gymlog.app.notification.RestTimerNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimerState(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val sessionId: Long? = null,
    val endTimeMs: Long = 0L
)

class RestTimerService : Service() {

    companion object {
        private const val ACTION_START = "com.gymlog.app.action.START_TIMER"
        private const val ACTION_EXTEND = "com.gymlog.app.action.EXTEND_TIMER"
        private const val ACTION_STOP = "com.gymlog.app.action.STOP_TIMER"

        private const val EXTRA_DURATION_SECONDS = "duration_seconds"
        private const val EXTRA_ADDITIONAL_SECONDS = "additional_seconds"
        private const val EXTRA_SESSION_ID = "session_id"

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        fun start(context: Context, durationSeconds: Int, sessionId: Long? = null) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun extend(context: Context, additionalSeconds: Int) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_EXTEND
                putExtra(EXTRA_ADDITIONAL_SECONDS, additionalSeconds)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var countDownTimer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0)
                val sessionId = if (intent.hasExtra(EXTRA_SESSION_ID)) {
                    intent.getLongExtra(EXTRA_SESSION_ID, 0L)
                } else {
                    null
                }
                startTimer(duration, sessionId)
            }
            ACTION_EXTEND -> {
                val additional = intent.getIntExtra(EXTRA_ADDITIONAL_SECONDS, 0)
                extendTimer(additional)
            }
            ACTION_STOP -> {
                stopTimer()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer(durationSeconds: Int, sessionId: Long?) {
        countDownTimer?.cancel()
        cancelAutoDismiss()

        val endTimeMs = System.currentTimeMillis() + durationSeconds * 1000L

        RestTimerNotification.createChannel(this)
        val notification = RestTimerNotification.buildNotification(this, endTimeMs, sessionId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                RestTimerNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(RestTimerNotification.NOTIFICATION_ID, notification)
        }

        _timerState.value = TimerState(
            isRunning = true,
            remainingSeconds = durationSeconds,
            totalSeconds = durationSeconds,
            sessionId = sessionId,
            endTimeMs = endTimeMs
        )

        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = ((millisUntilFinished + 500) / 1000).toInt()
                _timerState.value = _timerState.value.copy(remainingSeconds = remaining)
            }

            override fun onFinish() {
                _timerState.value = _timerState.value.copy(
                    isRunning = false,
                    remainingSeconds = 0
                )

                vibrate()

                val completedNotification = RestTimerNotification.buildCompletedNotification(
                    this@RestTimerService,
                    _timerState.value.sessionId
                )
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.notify(RestTimerNotification.NOTIFICATION_ID, completedNotification)

                autoDismissRunnable = Runnable {
                    _timerState.value = TimerState()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                handler.postDelayed(autoDismissRunnable!!, 3000L)
            }
        }.start()
    }

    private fun extendTimer(additionalSeconds: Int) {
        val current = _timerState.value
        if (!current.isRunning) return

        countDownTimer?.cancel()
        cancelAutoDismiss()

        val newRemaining = current.remainingSeconds + additionalSeconds
        val newTotal = current.totalSeconds + additionalSeconds
        val endTimeMs = System.currentTimeMillis() + newRemaining * 1000L

        val notification = RestTimerNotification.buildNotification(this, endTimeMs, current.sessionId)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(RestTimerNotification.NOTIFICATION_ID, notification)

        _timerState.value = current.copy(
            remainingSeconds = newRemaining,
            totalSeconds = newTotal,
            endTimeMs = endTimeMs
        )

        countDownTimer = object : CountDownTimer(newRemaining * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = ((millisUntilFinished + 500) / 1000).toInt()
                _timerState.value = _timerState.value.copy(remainingSeconds = remaining)
            }

            override fun onFinish() {
                _timerState.value = _timerState.value.copy(
                    isRunning = false,
                    remainingSeconds = 0
                )

                vibrate()

                val completedNotification = RestTimerNotification.buildCompletedNotification(
                    this@RestTimerService,
                    _timerState.value.sessionId
                )
                val mgr = getSystemService(android.app.NotificationManager::class.java)
                mgr.notify(RestTimerNotification.NOTIFICATION_ID, completedNotification)

                autoDismissRunnable = Runnable {
                    _timerState.value = TimerState()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                handler.postDelayed(autoDismissRunnable!!, 3000L)
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        cancelAutoDismiss()
        _timerState.value = TimerState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
    }

    private fun vibrate() {
        try {
            val pattern = longArrayOf(0, 300, 200, 300)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
            // Vibration not available
        }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        countDownTimer = null
        cancelAutoDismiss()
        _timerState.value = TimerState()
        super.onDestroy()
    }
}
