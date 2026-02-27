package com.gymlog.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gymlog.app.notification.RestTimerNotification
import com.gymlog.app.ui.navigation.GymLogNavigation
import com.gymlog.app.ui.theme.GymLogTheme

class MainActivity : ComponentActivity() {

    var pendingSessionId by mutableStateOf<Long?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RestTimerNotification.createChannel(this)
        handleTimerIntent(intent)
        setContent {
            GymLogTheme {
                GymLogNavigation(
                    pendingSessionId = pendingSessionId,
                    onPendingSessionConsumed = { pendingSessionId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTimerIntent(intent)
    }

    private fun handleTimerIntent(intent: Intent?) {
        val sessionId = intent?.getLongExtra(RestTimerNotification.EXTRA_SESSION_ID, -1L)
        if (sessionId != null && sessionId != -1L) {
            pendingSessionId = sessionId
        }
    }
}
