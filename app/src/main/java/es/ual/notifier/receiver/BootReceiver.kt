package es.ual.notifier.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import es.ual.notifier.UALNotifierApp
import es.ual.notifier.data.remote.SessionStore

/** Reprograma la comprobación periódica tras reiniciar el móvil o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (SessionStore.get(context).isLoggedIn) {
                UALNotifierApp.schedulePolling(context)
            }
        }
    }
}
