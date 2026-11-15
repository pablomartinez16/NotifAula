package es.ual.notifier.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import es.ual.notifier.UALNotifierApp
import es.ual.notifier.data.local.NotificationEntity
import es.ual.notifier.data.remote.SessionStore
import es.ual.notifier.data.repository.SyncRepository
import es.ual.notifier.notification.OpenLinkActivity
import es.ual.notifier.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SyncRepository(context) }
    val session = remember { SessionStore.get(context) }
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }

    val notifications by repo.observeNotifications().collectAsState(initial = emptyList())
    val unread by repo.observeUnreadCount().collectAsState(initial = 0)

    // Estado de la sincronización manual
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(Constants.POLL_NOW_WORK_NAME)
        .collectAsState(initial = emptyList())
    val syncing = workInfos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    // Última comprobación / error (se refresca periódicamente)
    var lastPoll by remember { mutableLongStateOf(prefs.getLong(Constants.PREF_LAST_POLL, 0)) }
    var lastError by remember { mutableStateOf(prefs.getString(Constants.PREF_LAST_ERROR, null)) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(syncing) {
        while (true) {
            lastPoll = prefs.getLong(Constants.PREF_LAST_POLL, 0)
            lastError = prefs.getString(Constants.PREF_LAST_ERROR, null)
            tick = System.currentTimeMillis()
            delay(5_000)
        }
    }

    // Permiso de notificaciones (Android 13+)
    var notifGranted by remember { mutableStateOf(hasNotifPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    LaunchedEffect(Unit) {
        if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val batteryOptimized = remember(tick) { isBatteryOptimized(context) }

    var confirmLogout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NotifAula", fontWeight = FontWeight.ExtraBold)
                        session.userDisplayName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { UALNotifierApp.syncNow(context) }, enabled = !syncing) {
                        Icon(Icons.Filled.Refresh, "Comprobar ahora")
                    }
                    if (unread > 0) {
                        IconButton(onClick = { scope.launch { repo.markAllAsRead() } }) {
                            Icon(Icons.Filled.Done, "Marcar todo como leído")
                        }
                    }
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = { scope.launch { repo.clearNotificationsHistory() } }) {
                            Icon(Icons.Filled.Delete, "Limpiar historial")
                        }
                    }
                    IconButton(onClick = { showPrivacy = true }) {
                        Icon(Icons.Filled.Info, "Información y Política de Privacidad")
                    }
                    IconButton(onClick = { confirmLogout = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Cerrar sesión")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
                StatusCard(lastPoll, lastError, unread)
            }
            if (!notifGranted) item {
                WarningCard(
                    "Las notificaciones están desactivadas. Actívalas para recibir avisos.",
                    "Activar"
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
            if (batteryOptimized) item {
                WarningCard(
                    "Para recibir avisos puntuales, pon Batería en 'Sin restricciones' dentro de la info de la app.",
                    "Ajustes"
                ) {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(i) }
                }
            }
            if (notifications.isEmpty()) {
                item { EmptyState() }
            } else {
                items(notifications, key = { it.id }) { n ->
                    NotificationCard(n) {
                        scope.launch { repo.markAsRead(n.id) }
                        OpenLinkActivity.openInBrowser(context, n.url)
                    }
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Dejarás de recibir avisos del Aula Virtual y se borrarán los datos guardados en el móvil.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch {
                        UALNotifierApp.cancelPolling(context)
                        repo.clearLocalData()
                        session.clearAll()
                        prefs.edit().clear().apply()
                        CookieManager.getInstance().removeAllCookies(null)
                        onLoggedOut()
                    }
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } }
        )
    }

    if (showPrivacy) {
        PrivacyInfoDialog(onDismiss = { showPrivacy = false })
    }
}

@Composable
private fun StatusCard(lastPoll: Long, lastError: String?, unread: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (unread > 0) "$unread sin leer" else "Todo al día",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                when {
                    !lastError.isNullOrBlank() -> "⚠️ $lastError"
                    lastPoll > 0 -> "Actualizado ${timeAgo(lastPoll)}"
                    else -> "Sincronizando…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!lastError.isNullOrBlank()) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun WarningCard(text: String, action: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onClick) { Text(action, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Notifications, null, Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
        Spacer(Modifier.height(12.dp))
        Text("Sin notificaciones nuevas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NotificationCard(n: NotificationEntity, onClick: () -> Unit) {
    val (icon, color) = typeStyle(n.type)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (n.isRead) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (n.isRead) 0.dp else 1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (n.courseName.isNotBlank()) Text(
                    n.courseName, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    n.title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (n.isRead) FontWeight.SemiBold else FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (n.body.isNotBlank()) Text(
                    n.body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6, overflow = TextOverflow.Ellipsis
                )
                Text(
                    timeAgo(n.timestamp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            if (!n.isRead) Box(
                Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private fun typeStyle(type: String): Pair<ImageVector, Color> = when (type) {
    Constants.TYPE_ANNOUNCEMENT -> Icons.Filled.Info to Color(0xFF1976D2)
    Constants.TYPE_CONTENT -> Icons.AutoMirrored.Filled.List to Color(0xFF388E3C)
    Constants.TYPE_GRADE -> Icons.Filled.Star to Color(0xFFF57C00)
    Constants.TYPE_TASK -> Icons.Filled.DateRange to Color(0xFF7B1FA2)
    Constants.TYPE_MESSAGE -> Icons.Filled.Email to Color(0xFF00838F)
    else -> Icons.Filled.Notifications to Color(0xFF616161)
}

private fun hasNotifPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun isBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return false
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun timeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "ahora mismo"
        diff < 3_600_000 -> "hace ${diff / 60_000} min"
        diff < 86_400_000 -> "hace ${diff / 3_600_000} h"
        diff < 7 * 86_400_000L -> "hace ${diff / 86_400_000} días"
        else -> SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es", "ES")).format(Date(ts))
    }
}
