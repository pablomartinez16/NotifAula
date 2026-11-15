package es.ual.notifier.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import es.ual.notifier.data.remote.AuthManager
import es.ual.notifier.data.remote.SessionStore
import es.ual.notifier.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de inicio de sesión.
 *
 * El usuario inicia sesión directamente en la web oficial (`https://aulavirtual.ual.es`).
 * La app nunca ve ni almacena la contraseña: solo recoge las cookies de sesión
 * cuando Blackboard redirige a `/ultra` tras un login válido.
 * (Este WebView se usa únicamente para el login; las notificaciones siempre se abren
 * en el navegador del sistema).
 */
@Composable
fun LoginScreen(sessionExpired: Boolean, onLoggedIn: () -> Unit) {
    var showWebView by remember { mutableStateOf(false) }
    if (showWebView) {
        OfficialWebLogin(onBack = { showWebView = false }, onLoggedIn = onLoggedIn)
    } else {
        WelcomeLoginScreen(sessionExpired = sessionExpired, onStartLogin = { showWebView = true })
    }
}

@Composable
private fun WelcomeLoginScreen(sessionExpired: Boolean, onStartLogin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "NotifAula",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Avisos del Aula Virtual (No oficial)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (sessionExpired) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Sesión caducada",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onStartLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Iniciar sesión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            "App no oficial · Conexión directa con aulavirtual.ual.es",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OfficialWebLogin(onBack: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { SessionStore.get(context) }
    val auth = remember { AuthManager(context) }

    var loading by remember { mutableStateOf(true) }
    var finishing by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(Constants.ULTRA_URL) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Iniciar sesión", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                currentUrl,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cm = CookieManager.getInstance()
                    cm.removeAllCookies(null)
                    cm.setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        cm.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                loading = true
                                currentUrl = url
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                currentUrl = url
                                if (finishing) return
                                val cookies = cm.getCookie(Constants.BASE_URL) ?: return
                                val onUltra = url.contains("${Constants.HOST}/ultra") &&
                                    !url.contains("logout") &&
                                    !url.contains("login")
                                if (onUltra && cookies.contains("BbRouter=")) {
                                    finishing = true
                                    cm.flush()
                                    session.importCookieHeader(cookies)
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            runCatching { auth.finishLogin() }.isSuccess
                                        }
                                        if (ok) {
                                            onLoggedIn()
                                        } else {
                                            finishing = false
                                            error = "No se pudo verificar la sesión. Inténtalo de nuevo."
                                        }
                                    }
                                }
                            }
                        }
                        loadUrl(Constants.ULTRA_URL)
                    }
                }
            )
            if (loading || finishing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }
}
