package es.ual.notifier.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.ual.notifier.data.remote.SessionExpiredException
import es.ual.notifier.data.remote.SessionStore
import es.ual.notifier.data.repository.SyncRepository
import es.ual.notifier.notification.NotificationHelper
import es.ual.notifier.receiver.PollAlarmReceiver
import es.ual.notifier.util.Constants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tarea de comprobación del Aula Virtual.
 * Expone [runSync] con un [Mutex] para que tanto la alarma exacta de 5 min
 * ([PollAlarmReceiver]) como WorkManager o el botón manual puedan ejecutarla
 * al instante sin solaparse.
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val forceFull = inputData.getBoolean(KEY_FULL_SCAN, false)
        val ok = runSync(applicationContext, forceFull)
        return if (!ok && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "PollWorker"
        const val KEY_FULL_SCAN = "full_scan"
        private val syncMutex = Mutex()

        /**
         * Ejecuta la comprobación inmediatamente y reprograma la siguiente alarma exacta a 5 min.
         * @return true si terminó bien (o sesión caducada tratada), false si hubo error de red.
         */
        suspend fun runSync(ctx: Context, forceFullScan: Boolean = false): Boolean = syncMutex.withLock {
            val appCtx = ctx.applicationContext
            val session = SessionStore.get(appCtx)
            if (!session.isLoggedIn) return true

            // Programar siempre la siguiente alarma exacta a 5 minutos vista
            PollAlarmReceiver.scheduleNext(appCtx)

            val prefs = appCtx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val run = prefs.getInt(Constants.PREF_RUN_COUNTER, 0) + 1
            prefs.edit().putInt(Constants.PREF_RUN_COUNTER, run).apply()
            val fullScan = forceFullScan || (run % Constants.FULL_CONTENT_SCAN_EVERY == 1)

            val repo = SyncRepository(appCtx)
            val notifier = NotificationHelper(appCtx)

            return try {
                val result = repo.sync(fullScan)
                notifier.cancelSessionExpired()
                notifier.show(result.newNotifications)
                prefs.edit()
                    .putLong(Constants.PREF_LAST_POLL, System.currentTimeMillis())
                    .remove(Constants.PREF_LAST_ERROR)
                    .apply()
                true
            } catch (e: SessionExpiredException) {
                prefs.edit().putString(Constants.PREF_LAST_ERROR, "Sesión caducada").apply()
                notifier.showSessionExpired()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error en la sincronización", e)
                prefs.edit().putString(Constants.PREF_LAST_ERROR, e.message ?: "Error de red").apply()
                false
            }
        }
    }
}
