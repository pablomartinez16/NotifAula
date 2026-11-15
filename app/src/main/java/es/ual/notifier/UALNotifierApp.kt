package es.ual.notifier

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import es.ual.notifier.data.remote.SessionStore
import es.ual.notifier.receiver.PollAlarmReceiver
import es.ual.notifier.util.Constants
import es.ual.notifier.worker.PollWorker
import java.util.concurrent.TimeUnit

class UALNotifierApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
        if (SessionStore.get(this).isLoggedIn) schedulePolling(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: String, desc: String, imp: Int = NotificationManager.IMPORTANCE_HIGH) =
            NotificationChannel(id, name, imp).apply { description = desc }
        nm.createNotificationChannels(
            listOf(
                ch(Constants.CHANNEL_ANNOUNCEMENTS, "Anuncios", "Nuevos anuncios de tus asignaturas"),
                ch(Constants.CHANNEL_GRADES, "Calificaciones", "Notas nuevas o modificadas"),
                ch(Constants.CHANNEL_CONTENT, "Contenido", "Nuevos materiales, documentos y actividades"),
                ch(Constants.CHANNEL_TASKS, "Tareas", "Nuevas entregas y fechas límite"),
                ch(Constants.CHANNEL_MESSAGES, "Mensajes", "Conversaciones y mensajes nuevos"),
                ch(
                    Constants.CHANNEL_SYSTEM, "Estado de la app", "Avisos de sesión caducada",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        )
    }

    companion object {
        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Programa tanto la alarma rápida cada 5 minutos ([PollAlarmReceiver])
         * como el trabajo periódico de WorkManager cada 15 min de respaldo.
         */
        fun schedulePolling(context: Context, replace: Boolean = false) {
            PollAlarmReceiver.scheduleNext(context)
            val request = PeriodicWorkRequestBuilder<PollWorker>(
                Constants.POLL_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.POLL_WORK_NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Lanza una comprobación inmediata (botón "Actualizar", alarma de 5 min o tras el login). */
        fun syncNow(context: Context, fullScan: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<PollWorker>()
                .setConstraints(networkConstraint)
                .setInputData(workDataOf(PollWorker.KEY_FULL_SCAN to fullScan))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.POLL_NOW_WORK_NAME, ExistingWorkPolicy.KEEP, request
            )
        }

        fun cancelPolling(context: Context) {
            PollAlarmReceiver.cancel(context)
            WorkManager.getInstance(context).cancelUniqueWork(Constants.POLL_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(Constants.POLL_NOW_WORK_NAME)
        }
    }
}
