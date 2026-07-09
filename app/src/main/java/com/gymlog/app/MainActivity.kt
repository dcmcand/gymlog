package com.gymlog.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.gymlog.app.data.GymLogDatabase
import com.gymlog.app.notification.RestTimerNotification
import com.gymlog.app.ui.navigation.GymLogNavigation
import com.gymlog.app.ui.theme.GymLogTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    var pendingSessionId by mutableStateOf<Long?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RestTimerNotification.createChannel(this)
        handleTimerIntent(intent)
        routeToInProgressSessionIfAny()
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

    private fun routeToInProgressSessionIfAny() {
        if (pendingSessionId != null) return
        lifecycleScope.launch {
            val session = GymLogDatabase.getDatabase(this@MainActivity)
                .workoutSessionDao()
                .getInProgressSession()
            if (session != null && pendingSessionId == null) {
                pendingSessionId = session.id
            }
        }
    }
}
