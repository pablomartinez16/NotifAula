package es.ual.notifier.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Notificación generada (historial visible en la pantalla principal). */
@Entity(tableName = "notifications")
data class NotificationEntity(
    /** Clave única, p. ej. "ann:{courseId}:{announcementId}" o "grade:{courseId}:{colId}:{score}". */
    @PrimaryKey val id: String,
    val type: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val body: String,
    /** Enlace directo que se abre en el navegador del sistema. */
    val url: String,
    val timestamp: Long,
    val isRead: Boolean = false
)

/**
 * Elemento ya visto en el Aula Virtual. Sirve para detectar novedades:
 * si un elemento no está aquí (o su huella cambió) → notificación.
 */
@Entity(tableName = "seen_items")
data class SeenItem(
    /** p. ej. "ann:{courseId}:{id}", "content:{contentId}", "grade:{courseId}:{colId}". */
    @PrimaryKey val key: String,
    /** Huella del estado (nota, fecha de modificación...). */
    val fingerprint: String,
    val updatedAt: Long = System.currentTimeMillis()
)
