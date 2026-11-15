package es.ual.notifier

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import es.ual.notifier.data.remote.SessionStore
import es.ual.notifier.notification.NotificationHelper
import es.ual.notifier.ui.screens.HomeScreen
import es.ual.notifier.ui.screens.LoginScreen
import es.ual.notifier.ui.theme.UALNotifierTheme

class MainActivity : ComponentActivity() {

    private var forceLogin by mutableStateOf(false)
    private var loggedIn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loggedIn = SessionStore.get(this).isLoggedIn
        handleIntent(intent)

        setContent {
            UALNotifierTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!loggedIn || forceLogin) {
                        LoginScreen(sessionExpired = forceLogin) {
                            forceLogin = false
                            loggedIn = true
                            NotificationHelper(this).cancelSessionExpired()
                            UALNotifierApp.schedulePolling(this)
                            // Primera sincronización inmediata (crea la línea base)
                            UALNotifierApp.syncNow(this, fullScan = true)
                        }
                    } else {
                        HomeScreen(onLoggedOut = { loggedIn = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_FORCE_LOGIN, false) == true) forceLogin = true
    }

    companion object {
        const val EXTRA_FORCE_LOGIN = "force_login"
    }
}
