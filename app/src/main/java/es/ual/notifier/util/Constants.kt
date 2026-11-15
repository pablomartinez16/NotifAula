package es.ual.notifier.util

/**
 * Constantes globales. No contiene NINGÚN dato de usuario: el ID del usuario,
 * sus cursos, etc. se obtienen en tiempo de ejecución tras iniciar sesión.
 */
object Constants {
    const val HOST = "aulavirtual.ual.es"
    const val BASE_URL = "https://$HOST"
    const val ULTRA_URL = "$BASE_URL/ultra/"

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    // Canales de notificación
    const val CHANNEL_ANNOUNCEMENTS = "canal_anuncios"
    const val CHANNEL_CONTENT = "canal_contenido"
    const val CHANNEL_GRADES = "canal_calificaciones"
    const val CHANNEL_TASKS = "canal_tareas"
    const val CHANNEL_MESSAGES = "canal_mensajes"
    const val CHANNEL_SYSTEM = "canal_sistema"

    // Intervalos de comprobación
    const val ALARM_INTERVAL_MINUTES = 5L
    const val POLL_WORK_NAME = "ual_notifier_poll"
    const val POLL_NOW_WORK_NAME = "ual_notifier_poll_now"
    const val POLL_INTERVAL_MINUTES = 15L

    // Preferencias NO sensibles (fichero normal)
    const val PREFS_NAME = "ual_notifier_prefs"
    const val PREF_LAST_POLL = "last_poll"
    const val PREF_LAST_ERROR = "last_error"
    const val PREF_RUN_COUNTER = "run_counter"

    // Cada cuántas ejecuciones se recorre el árbol de contenidos completo
    // (en el resto solo se baja a carpetas cuya fecha de modificación cambió).
    const val FULL_CONTENT_SCAN_EVERY = 4

    const val SESSION_EXPIRED_NOTIFICATION_ID = 9999

    // Tipos de notificación
    const val TYPE_ANNOUNCEMENT = "ANNOUNCEMENT"
    const val TYPE_CONTENT = "CONTENT"
    const val TYPE_GRADE = "GRADE"
    const val TYPE_TASK = "TASK"
    const val TYPE_MESSAGE = "MESSAGE"
}
