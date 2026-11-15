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

/**
 * Tarea de comprobación del Aula Virtual (ejecutada cada 5 min vía AlarmManager
 * y respaldada cada 15 min por WorkManager).
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val session = SessionStore.get(ctx)
        if (!session.isLoggedIn) return Result.success()

        // Asegurar que la alarma de 5 minutos sigue programada
        PollAlarmReceiver.scheduleNext(ctx)

        val prefs = ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val run = prefs.getInt(Constants.PREF_RUN_COUNTER, 0) + 1
        prefs.edit().putInt(Constants.PREF_RUN_COUNTER, run).apply()
        val fullScan = run % Constants.FULL_CONTENT_SCAN_EVERY == 1 ||
            inputData.getBoolean(KEY_FULL_SCAN, false)

        val repo = SyncRepository(ctx)
        val notifier = NotificationHelper(ctx)

        return try {
            val result = repo.sync(fullScan)
            notifier.cancelSessionExpired()
            notifier.show(result.newNotifications)
            prefs.edit()
                .putLong(Constants.PREF_LAST_POLL, System.currentTimeMillis())
                .remove(Constants.PREF_LAST_ERROR)
                .apply()
            Result.success()
        } catch (e: SessionExpiredException) {
            prefs.edit().putString(Constants.PREF_LAST_ERROR, "Sesión caducada").apply()
            notifier.showSessionExpired()
            // No devolvemos failure() para no cancelar el trabajo periódico
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error en la sincronización", e)
            prefs.edit().putString(Constants.PREF_LAST_ERROR, e.message ?: "Error de red").apply()
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "PollWorker"
        const val KEY_FULL_SCAN = "full_scan"
    }
}
