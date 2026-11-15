package es.ual.notifier.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import es.ual.notifier.MainActivity
import es.ual.notifier.R
import es.ual.notifier.data.local.NotificationEntity
import es.ual.notifier.util.Constants

/**
 * Muestra las notificaciones del sistema. Al pulsarlas se abre el enlace directo
 * en el NAVEGADOR DEL SISTEMA (Intent.ACTION_VIEW), nunca en un WebView embebido.
 */
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    private fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()

    fun show(items: List<NotificationEntity>) {
        if (!canNotify()) return
        items.forEach { show(it) }
        if (items.size > 1) {
            val summary = NotificationCompat.Builder(context, Constants.CHANNEL_ANNOUNCEMENTS)
                .setSmallIcon(R.drawable.ic_stat_ual)
                .setContentTitle("NotifAula")
                .setContentText("${items.size} novedades")
                .setStyle(NotificationCompat.InboxStyle().also { s -> items.take(6).forEach { s.addLine(it.title) } })
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build()
            safeNotify(SUMMARY_ID, summary)
        }
    }

    private fun show(item: NotificationEntity) {
        val channel = when (item.type) {
            Constants.TYPE_ANNOUNCEMENT -> Constants.CHANNEL_ANNOUNCEMENTS
            Constants.TYPE_CONTENT -> Constants.CHANNEL_CONTENT
            Constants.TYPE_GRADE -> Constants.CHANNEL_GRADES
            Constants.TYPE_TASK -> Constants.CHANNEL_TASKS
            Constants.TYPE_MESSAGE -> Constants.CHANNEL_MESSAGES
            else -> Constants.CHANNEL_ANNOUNCEMENTS
        }

        // Actividad invisible que marca como leída y abre el navegador del sistema
        val pi = PendingIntent.getActivity(
            context, item.id.hashCode(), OpenLinkActivity.intent(context, item.id, item.url),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ual)
            .setContentTitle(item.title)
            .setContentText(item.body.ifBlank { item.courseName })
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body.ifBlank { item.courseName }))
            .setSubText(item.courseName.takeIf { it.isNotBlank() })
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setWhen(item.timestamp)
            .setShowWhen(true)
            .setGroup(GROUP)
            .build()
        safeNotify(item.id.hashCode(), n)
    }

    fun showSessionExpired() {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_FORCE_LOGIN, true)
        }
        val pi = PendingIntent.getActivity(
            context, Constants.SESSION_EXPIRED_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, Constants.CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_stat_ual)
            .setContentTitle("⚠️ Sesión del Aula Virtual caducada")
            .setContentText("Toca para volver a iniciar sesión y seguir recibiendo avisos")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        safeNotify(Constants.SESSION_EXPIRED_NOTIFICATION_ID, n)
    }

    fun cancelSessionExpired() = manager.cancel(Constants.SESSION_EXPIRED_NOTIFICATION_ID)

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun safeNotify(id: Int, n: android.app.Notification) {
        try {
            manager.notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val GROUP = "ual_aula_virtual"
        private const val SUMMARY_ID = 1
    }
}
