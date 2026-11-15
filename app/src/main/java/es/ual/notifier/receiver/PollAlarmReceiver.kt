package es.ual.notifier.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import es.ual.notifier.UALNotifierApp
import es.ual.notifier.data.remote.SessionStore
import es.ual.notifier.util.Constants

/**
 * Alarma cada 5 minutos que despierta la comprobación usando `setAndAllowWhileIdle`.
 * No requiere permisos restringidos de Google Play (`USE_EXACT_ALARM`).
 */
class PollAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!SessionStore.get(context).isLoggedIn) return
        scheduleNext(context)
        UALNotifierApp.syncNow(context)
    }

    companion object {
        private const val REQUEST_CODE = 5005

        fun scheduleNext(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAt = System.currentTimeMillis() + Constants.ALARM_INTERVAL_MINUTES * 60_000L
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, PollAlarmReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
