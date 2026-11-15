package es.ual.notifier.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import es.ual.notifier.data.remote.SessionStore
import es.ual.notifier.util.Constants
import es.ual.notifier.worker.PollWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarma exacta cada 5 minutos (`setExactAndAllowWhileIdle`) que ejecuta la
 * comprobación directamente mediante `goAsync()` + `WakeLock`, sin esperar a la
 * cola diferida de WorkManager.
 */
class PollAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!SessionStore.get(context).isLoggedIn) return
        val appCtx = context.applicationContext

        // Reprogramar ya el siguiente disparo exacto a 5 minutos
        scheduleNext(appCtx)

        // Ejecutar la comprobación en este mismo instante usando goAsync() + WakeLock
        val pendingResult = goAsync()
        val pm = appCtx.getSystemService(PowerManager::class.java)
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NotifAula:PollAlarm")?.apply {
            acquire(60_000L)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                PollWorker.runSync(appCtx)
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 5005

        fun scheduleNext(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAt = System.currentTimeMillis() + Constants.ALARM_INTERVAL_MINUTES * 60_000L
            val pi = pendingIntent(context)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
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
