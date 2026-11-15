package es.ual.notifier.notification

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import es.ual.notifier.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Actividad invisible que se lanza al tocar una notificación:
 *  1. Marca la notificación como leída en el historial.
 *  2. Abre el enlace directo en el NAVEGADOR DEL SISTEMA (no un WebView).
 *  3. Se cierra inmediatamente.
 */
class OpenLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        val url = intent.getStringExtra(EXTRA_URL)

        if (id != null) {
            val appContext = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.get(appContext).notificationDao().markAsRead(id)
            }
        }
        if (url != null) openInBrowser(this, url)
        finish()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_URL = "url"

        fun intent(context: Context, id: String, url: String): Intent =
            Intent(context, OpenLinkActivity::class.java).apply {
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_URL, url)
                // data única para que cada PendingIntent sea distinto
                data = Uri.parse("ualnotifier://open/${id.hashCode()}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }

        /** Abre [url] en el navegador predeterminado del sistema. */
        fun openInBrowser(context: Context, url: String) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(view)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No hay ningún navegador instalado", Toast.LENGTH_LONG).show()
            }
        }
    }
}
