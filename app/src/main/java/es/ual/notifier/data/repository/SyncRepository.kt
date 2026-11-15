package es.ual.notifier.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import es.ual.notifier.data.local.AppDatabase
import es.ual.notifier.data.local.NotificationEntity
import es.ual.notifier.data.local.SeenItem
import es.ual.notifier.data.remote.AuthManager
import es.ual.notifier.data.remote.BbClient
import es.ual.notifier.data.remote.SessionExpiredException
import es.ual.notifier.data.remote.bool
import es.ual.notifier.data.remote.num
import es.ual.notifier.data.remote.obj
import es.ual.notifier.data.remote.str
import es.ual.notifier.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Motor de sincronización: consulta la API interna de Blackboard Ultra con la sesión
 * del usuario y genera notificaciones enriquecidas para todo lo nuevo:
 *  - 📢 Anuncios de cada asignatura (con texto completo y fecha)
 *  - 📄 Contenido nuevo (con ruta de carpetas, tipo de material y descripción)
 *  - 🎓 Calificaciones nuevas o modificadas (con nota, comentarios del profesor, adjuntos y media)
 *  - 📅 Tareas / entregas próximas (con asignatura y fecha límite)
 *  - ✉️ Mensajes / conversaciones por asignatura (con asignatura, remitente y asunto/extracto)
 *
 * La primera vez que se ve una asignatura (o un tipo de elemento) se crea una
 * "línea base" SIN notificar, para no recibir cientos de avisos de cosas antiguas.
 */
class SyncRepository(context: Context) {

    private val client = BbClient.get(context)
    private val session = client.session
    private val auth = AuthManager(context)
    private val db = AppDatabase.get(context)
    private val seenDao = db.seenDao()
    private val notificationDao = db.notificationDao()

    data class Course(val id: String, val name: String, val shortName: String)
    data class ContentNode(val json: JsonObject, val breadcrumb: String)
    data class SyncResult(val newNotifications: List<NotificationEntity>, val coursesChecked: Int)

    fun observeNotifications() = notificationDao.observeRecent()
    fun observeUnreadCount() = notificationDao.observeUnreadCount()
    suspend fun markAsRead(id: String) = notificationDao.markAsRead(id)
    suspend fun markAllAsRead() = notificationDao.markAllAsRead()
    suspend fun clearNotificationsHistory() = notificationDao.deleteAll()

    suspend fun clearLocalData() {
        notificationDao.deleteAll()
        seenDao.deleteAll()
    }

    /**
     * Ejecuta una sincronización completa.
     * @param fullContentScan si es true recorre todas las carpetas de contenido.
     * @throws SessionExpiredException si la sesión no es válida.
     */
    suspend fun sync(fullContentScan: Boolean): SyncResult = withContext(Dispatchers.IO) {
        val userId = session.userId ?: auth.finishLogin().let { session.userId!! }
        val courses = fetchCourses(userId)
        val found = mutableListOf<NotificationEntity>()

        for (course in courses) {
            found += safe("anuncios ${course.shortName}") { checkAnnouncements(course) }
            found += safe("notas ${course.shortName}") { checkGrades(course, userId) }
            found += safe("contenido ${course.shortName}") { checkContent(course, fullContentScan) }
            found += safe("mensajes ${course.shortName}") { checkCourseMessages(course) }
        }
        found += safe("tareas") { checkTasks(courses) }
        found += safe("mensajes globales") { checkGlobalConversations() }

        // Guardar en el historial; solo devolvemos las realmente nuevas
        val inserted = found.filter { notificationDao.insert(it) != -1L }
        notificationDao.deleteOlderThan(System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
        SyncResult(inserted, courses.size)
    }

    // ───────────────────────── Asignaturas ─────────────────────────

    private fun fetchCourses(userId: String): List<Course> {
        val memberships = client.getAllResults(
            "learn/api/v1/users/${enc(userId)}/memberships" +
                "?expand=course.effectiveAvailability,courseRole&includeCount=true&limit=10000"
        )
        return memberships.mapNotNull { m ->
            val c = m.obj("course") ?: return@mapNotNull null
            val id = c.str("id") ?: m.str("courseId") ?: return@mapNotNull null
            val available = (m.bool("isAvailable") ?: true) &&
                (c.bool("effectiveAvailability") ?: c.bool("isAvailable") ?: true) &&
                c.bool("isClosed") != true
            if (!available) return@mapNotNull null
            val name = c.str("displayName") ?: c.str("name") ?: id
            Course(id, name, shortCourseName(name))
        }
    }

    // ───────────────────────── Anuncios ─────────────────────────

    private suspend fun checkAnnouncements(course: Course): List<NotificationEntity> {
        val cid = course.id
        val items = client.getAllResults(
            "learn/api/v1/courses/${enc(cid)}/announcements?limit=25&offset=0" +
                "&sort=${enc("startDateRestriction(desc)")}",
            maxPages = 1
        ).filter { it.bool("isDraft") != true }

        return diff(
            scope = "ann:$cid",
            items = items,
            key = { "ann:$cid:${it.str("id")}" },
            fingerprint = { it.str("modifiedDate") ?: "1" }
        ) { a, old ->
            if (old != null) return@diff null
            // Si ya fue leído en el Aula Virtual, no notificar
            if (a.obj("readStatus")?.bool("isRead") == true) return@diff null
            val id = a.str("id") ?: return@diff null
            val titleText = a.str("title")?.trim() ?: "Nuevo anuncio"
            val rawHtml = a.obj("body")?.str("rawText") ?: a.obj("body")?.str("displayText")
            val textBody = htmlToText(rawHtml, maxChars = 600)
            val dateStr = (a.str("startDateRestriction") ?: a.str("createdDate"))?.let { prettyDate(it) }
            val fullBody = buildString {
                if (textBody.isNotBlank()) append(textBody)
                if (!dateStr.isNullOrBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("🕒 Publicado: ").append(dateStr)
                }
            }
            NotificationEntity(
                id = "ann:$cid:$id",
                type = Constants.TYPE_ANNOUNCEMENT,
                courseId = cid,
                courseName = course.shortName,
                title = "📢 $titleText",
                body = fullBody.ifBlank { "Nuevo anuncio en ${course.shortName}" },
                url = "${Constants.ULTRA_URL}courses/$cid/announcements/announcement-detail" +
                    "?courseId=$cid&announcementId=$id",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    // ───────────────────────── Calificaciones ─────────────────────────

    private suspend fun checkGrades(course: Course, userId: String): List<NotificationEntity> {
        val cid = course.id
        val items = client.getAllResults(
            "learn/api/v1/courses/${enc(cid)}/gradebook/grades?userId=${enc(userId)}" +
                "&limit=100&offset=0&expand=${enc("lastAttempt,column,hasAttemptOrGradeFeedback")}" +
                "&includeNoGradeItems=true"
        ).filter { g ->
            val col = g.obj("column")
            col?.bool("visible") != false && col?.bool("gradesReleased") != false
        }

        return diff(
            scope = "grade:$cid",
            items = items,
            key = { "grade:$cid:${it.str("columnId")}" },
            fingerprint = { gradeFingerprint(it) }
        ) { g, old ->
            val now = gradeText(g)
            if (now.isEmpty()) return@diff null

            val oldFp = old?.fingerprint.orEmpty()
            val oldScore = oldFp.substringBefore(":")

            // 1) Si el alumno ya había visto esta calificación en Blackboard y la nota no ha cambiado, no notificar
            val viewedInBb = g.bool("hasBeenViewedByStudent") == true || g.str("studentReviewedTimestamp") != null
            if (viewedInBb && (oldScore.isEmpty() || oldScore == now)) {
                return@diff null
            }

            // 2) Si la nota numérica es exactamente la misma que antes (p. ej. tras actualizar la app), no notificar
            if (oldScore.isNotEmpty() && oldScore == now) {
                return@diff null
            }

            val updated = oldScore.isNotEmpty()
            val col = g.obj("column")
            val colName = col?.str("effectiveColumnName")
                ?: col?.obj("localizedColumnName")?.str("languageKey")
                ?: col?.str("columnName")
                ?: "Calificación"
            val possible = col?.num("possible")?.takeIf { it > 0 }?.let { " / ${fmt(it)}" }.orEmpty()
            val contentId = col?.str("contentId")

            val attempt = g.obj("lastAttempt")
            val feedbackRaw = attempt?.obj("feedbackToUser")?.str("rawText")
                ?: attempt?.obj("feedbackToUser")?.str("displayText")
            val feedbackText = htmlToText(feedbackRaw, maxChars = 350)
            val avg = g.num("averageScore")?.takeIf { it > 0 }?.let { fmt(it) }

            val richBody = buildString {
                append("📊 Nota: ").append(now).append(possible)
                if (avg != null) append("  (Media: $avg)")
                if (feedbackText.isNotBlank()) {
                    append("\n💬 Comentario: ").append(feedbackText)
                }
            }

            NotificationEntity(
                id = "grade:$cid:${g.str("columnId")}:${gradeFingerprint(g)}",
                type = Constants.TYPE_GRADE,
                courseId = cid,
                courseName = course.shortName,
                title = (if (updated) "🎓 Nota actualizada: " else "🎓 Nueva nota: ") + colName,
                body = richBody,
                url = if (contentId != null)
                    "${Constants.ULTRA_URL}courses/$cid/assessment/$contentId/overview?courseId=$cid"
                else "${Constants.ULTRA_URL}courses/$cid/grades",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun gradeText(g: JsonObject): String {
        val dg = g.obj("displayGrade") ?: return ""
        dg.num("score")?.let { return fmt(it) }
        return dg.str("text") ?: dg.str("letter") ?: ""
    }

    private fun gradeFingerprint(g: JsonObject): String {
        val score = gradeText(g)
        if (score.isEmpty()) return ""
        val feedback = g.obj("lastAttempt")?.obj("feedbackToUser")?.str("rawText")?.hashCode() ?: 0
        return "$score:$feedback"
    }

    // ───────────────────────── Contenido ─────────────────────────

    private suspend fun checkContent(course: Course, fullScan: Boolean): List<NotificationEntity> {
        val cid = course.id
        val isBaseline = !seenDao.exists("init:content:$cid")
        val collected = mutableListOf<ContentNode>()
        walkContent(cid, "ROOT", "", 0, fullScan || isBaseline, collected)

        val news = diff(
            scope = "content:$cid",
            items = collected,
            key = { "content:${it.json.str("id")}" },
            fingerprint = { it.json.get("modifiedDate")?.toString() ?: "" }
        ) { node, old ->
            if (old != null) return@diff null
            val item = node.json
            val id = item.str("id") ?: return@diff null
            val title = item.str("title")?.trim() ?: "Nuevo contenido"
            val handler = item.str("contentHandler").orEmpty()
            val kind = contentTypeLabel(handler)
            val desc = htmlToText(
                item.str("description") ?: item.obj("body")?.str("rawText") ?: item.obj("body")?.str("displayText"),
                maxChars = 350
            )
            val richBody = buildString {
                if (node.breadcrumb.isNotBlank()) {
                    append("📁 En: ").append(node.breadcrumb)
                }
                if (desc.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(desc)
                }
            }.ifBlank { "$kind publicado en ${course.shortName}" }

            NotificationEntity(
                id = "content:$id",
                type = Constants.TYPE_CONTENT,
                courseId = cid,
                courseName = course.shortName,
                title = "📄 $kind: $title",
                body = richBody,
                url = contentUrl(cid, id, handler),
                timestamp = System.currentTimeMillis()
            )
        }

        // Si se publica una carpeta con muchos archivos, agrupamos mostrando ruta y lista detallada
        if (news.size > 3) {
            val ts = System.currentTimeMillis()
            return listOf(
                NotificationEntity(
                    id = "content-batch:$cid:$ts",
                    type = Constants.TYPE_CONTENT,
                    courseId = cid,
                    courseName = course.shortName,
                    title = "📄 ${news.size} materiales nuevos en ${course.shortName}",
                    body = news.take(6).joinToString("\n") { n ->
                        val cleanTitle = n.title.removePrefix("📄 ")
                        val folder = n.body.lines().firstOrNull { it.startsWith("📁 En: ") }
                            ?.removePrefix("📁 En: ")
                        if (!folder.isNullOrBlank()) "• $cleanTitle ($folder)" else "• $cleanTitle"
                    },
                    url = "${Constants.ULTRA_URL}courses/$cid/outline",
                    timestamp = ts
                )
            )
        }
        return news
    }

    private suspend fun walkContent(
        cid: String,
        parentId: String,
        breadcrumb: String,
        depth: Int,
        full: Boolean,
        out: MutableList<ContentNode>
    ) {
        if (depth > MAX_CONTENT_DEPTH || out.size > MAX_CONTENT_ITEMS) return
        val children = try {
            client.getAllResults(
                "learn/api/v1/courses/${enc(cid)}/contents/${enc(parentId)}/children?@view=Summary&limit=200",
                maxPages = 3
            )
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            return
        }
        for (item in children) {
            out += ContentNode(item, breadcrumb)
            if (isFolder(item)) {
                val id = item.str("id") ?: continue
                val title = item.str("title")?.trim().orEmpty()
                val nextCrumb = if (breadcrumb.isBlank()) title else "$breadcrumb › $title"
                val old = seenDao.get("content:$id")
                val fp = item.get("modifiedDate")?.toString() ?: ""
                if (full || old == null || old.fingerprint != fp) {
                    walkContent(cid, id, nextCrumb, depth + 1, full, out)
                }
            }
        }
    }

    private fun isFolder(item: JsonObject): Boolean {
        val handler = item.str("contentHandler").orEmpty()
        if (handler == "resource/x-bb-folder" || handler == "resource/x-bb-lesson") return true
        val detail = item.obj("contentDetail") ?: return false
        return detail.entrySet().any { (_, v) -> v.isJsonObject && v.asJsonObject.bool("isFolder") == true }
    }

    private fun contentTypeLabel(handler: String): String = when {
        handler.contains("asmt") || handler.contains("assignment") -> "Nueva tarea"
        handler.contains("x-bb-test") || handler.contains("assessment") -> "Cuestionario"
        handler == "resource/x-bb-document" -> "Documento"
        handler == "resource/x-bb-file" -> "Archivo"
        handler == "resource/x-bb-Gatekeeper" || handler == "resource/x-bb-externallink" -> "Enlace"
        handler == "resource/x-bb-folder" -> "Carpeta"
        handler == "resource/x-bb-lesson" -> "Módulo"
        handler.contains("discussion") || handler.contains("forum") -> "Foro"
        else -> "Material"
    }

    private fun contentUrl(cid: String, contentId: String, handler: String): String = when {
        handler.contains("asmt") || handler.contains("assignment") || handler.contains("x-bb-test") ->
            "${Constants.ULTRA_URL}courses/$cid/assessment/$contentId/overview?courseId=$cid"
        handler == "resource/x-bb-document" ->
            "${Constants.ULTRA_URL}courses/$cid/outline/edit/document/$contentId?courseId=$cid&view=content"
        else -> "${Constants.ULTRA_URL}courses/$cid/outline"
    }

    // ───────────────────────── Tareas / entregas ─────────────────────────

    private suspend fun checkTasks(courses: List<Course>): List<NotificationEntity> {
        val now = System.currentTimeMillis()
        val since = iso(now - 24 * 3600 * 1000L)
        val until = iso(now + 45L * 24 * 3600 * 1000)
        val obj = client.getObject(
            "learn/api/v1/calendars/todo/studentItems?since=${enc(since)}&until=${enc(until)}"
        )
        val items = listOf("overdueItems", "dueTodayItems", "futureDueItems").flatMap { k ->
            obj.getAsJsonArray(k)?.filter { it.isJsonObject }?.map { it.asJsonObject } ?: emptyList()
        }
        val names = courses.associate { it.id to it.shortName }

        return diff(
            scope = "task",
            items = items,
            key = { "task:${taskId(it)}" },
            fingerprint = { firstStr(it, "dueDate", "endDate", "end", "dueDateTime") ?: "" }
        ) { t, old ->
            val cid = firstStr(t, "courseId", "calendarId").orEmpty()
            val contentId = firstStr(t, "contentId", "itemSourceId")?.takeIf { it.startsWith("_") }
            val due = firstStr(t, "dueDate", "endDate", "end", "dueDateTime")
            val courseName = names[cid] ?: ""
            val taskTitle = firstStr(t, "title", "name", "itemName") ?: "Entrega"
            val updated = old != null
            NotificationEntity(
                id = "task:${taskId(t)}:${due.orEmpty()}",
                type = Constants.TYPE_TASK,
                courseId = cid,
                courseName = courseName.ifBlank { "Calendario" },
                title = (if (updated) "📅 Fecha actualizada: " else "📅 Nueva entrega: ") + taskTitle,
                body = buildString {
                    if (courseName.isNotBlank()) append("📘 Asignatura: $courseName\n")
                    if (due != null) append("⏰ Fecha límite: ${prettyDate(due)}")
                }.trim(),
                url = if (cid.startsWith("_") && contentId != null)
                    "${Constants.ULTRA_URL}courses/$cid/assessment/$contentId/overview?courseId=$cid"
                else "${Constants.ULTRA_URL}calendar",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun taskId(t: JsonObject): String =
        firstStr(t, "id", "itemSourceId", "contentId") ?: (t.toString().hashCode().toString())

    // ───────────────────────── Mensajes por asignatura y globales ─────────────────────────

    private suspend fun checkCourseMessages(course: Course): List<NotificationEntity> {
        val cid = course.id
        val counts = client.getObject("learn/api/v1/courses/${enc(cid)}/conversations/counts")
        val unread = counts.num("unreadCount")?.toInt() ?: return emptyList()
        val total = counts.num("totalCount")?.toInt() ?: 0

        val key = "conv:course:$cid"
        val baselineKey = "init:$key"
        val now = System.currentTimeMillis()
        if (!seenDao.exists(baselineKey)) {
            seenDao.upsert(SeenItem(key, "$unread:$total", now))
            seenDao.upsert(SeenItem(baselineKey, "1", now))
            return emptyList()
        }

        val oldParts = seenDao.get(key)?.fingerprint?.split(":")
        val oldUnread = oldParts?.getOrNull(0)?.toIntOrNull() ?: 0
        seenDao.upsert(SeenItem(key, "$unread:$total", now))

        if (unread <= oldUnread) return emptyList()
        val delta = unread - oldUnread

        // Intentar obtener detalle de las conversaciones del curso (asunto / remitente / texto)
        val details = fetchCourseMessagePreview(cid)

        val title = when {
            details?.subject != null -> "✉️ ${course.shortName}: ${details.subject}"
            delta == 1 -> "✉️ 1 mensaje nuevo en ${course.shortName}"
            else -> "✉️ $delta mensajes nuevos en ${course.shortName}"
        }

        val body = buildString {
            if (!details?.sender.isNullOrBlank()) {
                append("👤 De: ").append(details!!.sender).append("\n")
            }
            if (!details?.preview.isNullOrBlank()) {
                append(details!!.preview)
            } else if (unread == 1) {
                append("Tienes 1 conversación sin leer en ${course.shortName}")
            } else {
                append("Tienes $unread conversaciones sin leer en ${course.shortName}")
            }
        }.trim()

        return listOf(
            NotificationEntity(
                id = "msg:$cid:$now",
                type = Constants.TYPE_MESSAGE,
                courseId = cid,
                courseName = course.shortName,
                title = title,
                body = body,
                url = "${Constants.ULTRA_URL}courses/$cid/messages",
                timestamp = now
            )
        )
    }

    private data class MessagePreview(val subject: String?, val sender: String?, val preview: String?)

    private fun fetchCourseMessagePreview(cid: String): MessagePreview? {
        val candidates = listOf(
            "learn/api/v1/courses/${enc(cid)}/conversations?limit=5&offset=0",
            "learn/api/v1/courses/${enc(cid)}/messages?limit=5&offset=0"
        )
        for (endpoint in candidates) {
            try {
                val results = client.getAllResults(endpoint, maxPages = 1)
                val first = results.firstOrNull { it.bool("isRead") == false || (it.num("unreadCount") ?: 0.0) > 0 }
                    ?: results.firstOrNull()
                    ?: continue
                val subject = firstStr(first, "subject", "title", "name")
                val senderObj = first.obj("sender") ?: first.obj("lastMessage")?.obj("sender") ?: first.obj("author")
                val sender = senderObj?.let {
                    val g = it.obj("name")?.str("given").orEmpty()
                    val f = it.obj("name")?.str("family").orEmpty()
                    "$g $f".trim().ifEmpty { firstStr(it, "displayName", "userName", "name") }
                } ?: firstStr(first, "senderName", "authorName")
                val rawBody = first.obj("body")?.str("rawText")
                    ?: first.obj("lastMessage")?.obj("body")?.str("rawText")
                    ?: firstStr(first, "body", "snippet", "previewText")
                val preview = htmlToText(rawBody, maxChars = 300).takeIf { it.isNotBlank() }
                if (subject != null || sender != null || preview != null) {
                    return MessagePreview(subject, sender, preview)
                }
            } catch (_: Exception) {
                // Si el endpoint concreto no está activo en esta versión de Ultra, seguimos
            }
        }
        return null
    }

    private suspend fun checkGlobalConversations(): List<NotificationEntity> {
        val counts = client.getObject("learn/api/v1/courses/conversations/counts")
        val unread = counts.num("unreadCount")?.toInt() ?: return emptyList()
        val key = "conv:unread"
        seenDao.upsert(SeenItem(key, unread.toString()))
        return emptyList()
    }

    // ───────────────────────── Detección de cambios ─────────────────────────

    /**
     * Compara [items] con lo ya visto. La primera vez que se ve un [scope] solo se
     * guarda el estado (línea base) sin notificar.
     */
    private suspend fun <T> diff(
        scope: String,
        items: List<T>,
        key: (T) -> String,
        fingerprint: (T) -> String,
        onChange: (T, SeenItem?) -> NotificationEntity?
    ): List<NotificationEntity> {
        val baselineKey = "init:$scope"
        val now = System.currentTimeMillis()
        if (!seenDao.exists(baselineKey)) {
            seenDao.upsertAll(items.map { SeenItem(key(it), fingerprint(it), now) })
            seenDao.upsert(SeenItem(baselineKey, "1", now))
            return emptyList()
        }
        val out = mutableListOf<NotificationEntity>()
        for (item in items) {
            val k = key(item)
            val fp = fingerprint(item)
            val old = seenDao.get(k)
            if (old == null || old.fingerprint != fp) {
                onChange(item, old)?.let { out += it }
                seenDao.upsert(SeenItem(k, fp, now))
            }
        }
        return out
    }

    private suspend fun safe(what: String, block: suspend () -> List<NotificationEntity>): List<NotificationEntity> =
        try {
            block()
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error comprobando $what", e)
            emptyList()
        }

    // ───────────────────────── Utilidades ─────────────────────────

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun firstStr(o: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { o.str(it) }

    private fun fmt(d: Double): String =
        if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale("es", "ES"), "%.2f", d).trimEnd('0').trimEnd(',')

    private fun iso(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(ms))

    private fun prettyDate(isoDate: String): String = runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val d = parser.parse(isoDate.take(19))!!
        SimpleDateFormat("EEE d MMM, HH:mm", Locale("es", "ES")).format(d)
    }.getOrDefault(isoDate)

    companion object {
        private const val TAG = "SyncRepository"
        private const val MAX_CONTENT_DEPTH = 5
        private const val MAX_CONTENT_ITEMS = 1500

        private val LOWERCASE_WORDS = setOf(
            "de", "del", "la", "las", "el", "los", "y", "e", "o", "u", "a", "en", "con", "por", "para", "al"
        )
        private val ROMAN_NUMERALS = setOf("i", "ii", "iii", "iv", "v", "vi")

        /** "[P] - INGENIERÍA DEL SOFTWARE - CÓDIGO[...] - ..." → "Ingeniería del Software" */
        fun shortCourseName(full: String): String {
            val locale = Locale("es", "ES")
            val parts = full.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
            val candidate = when {
                parts.size >= 2 && parts[0].startsWith("[") -> parts[1]
                parts.isNotEmpty() -> parts[0]
                else -> full
            }
            val words = candidate.lowercase(locale).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val formatted = words.mapIndexed { index, word ->
                when {
                    word in ROMAN_NUMERALS -> word.uppercase(locale)
                    index > 0 && word in LOWERCASE_WORDS -> word
                    else -> word.replaceFirstChar { it.titlecase(locale) }
                }
            }.joinToString(" ")
            return formatted.take(60)
        }

        fun htmlToText(html: String?, maxChars: Int = 500): String {
            if (html.isNullOrBlank()) return ""
            return html
                .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n\\s*\\n+"), "\n")
                .trim()
                .take(maxChars)
        }
    }
}
