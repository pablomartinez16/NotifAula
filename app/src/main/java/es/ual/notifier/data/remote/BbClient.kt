package es.ual.notifier.data.remote

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import es.ual.notifier.util.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** La sesión de Blackboard no es válida (401/403 o redirección al login). */
class SessionExpiredException(msg: String = "Sesión caducada") : IOException(msg)

/** Respuesta HTTP no exitosa distinta de un problema de sesión. */
class HttpException(val code: Int, url: String) : IOException("HTTP $code en $url")

/**
 * Cliente mínimo para la API REST interna que usa la propia web de Blackboard Ultra
 * (`/learn/api/v1/...`). Se autentica con las cookies de sesión + cabecera X-Blackboard-XSRF,
 * exactamente igual que el navegador.
 */
class BbClient private constructor(context: Context) {

    val session = SessionStore.get(context)

    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(session)
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val b = chain.request().newBuilder()
                .header("User-Agent", Constants.USER_AGENT)
            if (chain.request().url.encodedPath.startsWith("/learn/api")) {
                b.header("Accept", "application/json, text/plain, */*")
                session.getXsrfToken()?.let { b.header("X-Blackboard-XSRF", it) }
            }
            chain.proceed(b.build())
        })
        .build()

    /** GET a una ruta de la API (p. ej. "learn/api/v1/users/me?expand=x"). */
    fun getJson(pathAndQuery: String): JsonElement {
        val url = if (pathAndQuery.startsWith("http")) pathAndQuery
        else "${Constants.BASE_URL}/${pathAndQuery.trimStart('/')}"
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            val finalPath = resp.request.url.encodedPath
            if (resp.code == 401 || (resp.code == 403 && url.contains("/users/me"))) {
                throw SessionExpiredException()
            }
            if (finalPath.contains("/webapps/login") || finalPath.startsWith("/ultra/logout")) {
                throw SessionExpiredException()
            }
            if (!resp.isSuccessful) throw HttpException(resp.code, url)
            val body = resp.body?.string().orEmpty()
            val ct = resp.header("Content-Type").orEmpty()
            if (!ct.contains("json") && body.trimStart().startsWith("<")) {
                // Nos ha devuelto HTML (página de login) en lugar de JSON
                throw SessionExpiredException()
            }
            return JsonParser.parseString(body)
        }
    }

    fun getObject(pathAndQuery: String): JsonObject = getJson(pathAndQuery).asJsonObject

    /**
     * Devuelve todos los elementos de un listado paginado de Blackboard
     * (`{"results":[...],"paging":{"nextPage":"/learn/api/..."}}`).
     */
    fun getAllResults(pathAndQuery: String, maxPages: Int = 10): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var next: String? = pathAndQuery
        var pages = 0
        while (!next.isNullOrBlank() && pages < maxPages) {
            val obj = getObject(next)
            obj.getAsJsonArray("results")?.forEach { if (it.isJsonObject) out += it.asJsonObject }
            next = obj.getAsJsonObject("paging")?.str("nextPage")
            pages++
        }
        return out
    }

    /** Comprueba la sesión y devuelve el usuario actual (`/learn/api/v1/users/me`). */
    fun currentUser(): JsonObject = getObject("learn/api/v1/users/me")

    companion object {
        @Volatile private var instance: BbClient? = null
        fun get(context: Context): BbClient =
            instance ?: synchronized(this) {
                instance ?: BbClient(context.applicationContext).also { instance = it }
            }
    }
}

// ───────────── Utilidades para leer JSON de forma tolerante ─────────────

fun JsonObject.str(key: String): String? {
    val e = get(key) ?: return null
    return if (e.isJsonPrimitive) e.asString.takeIf { it.isNotEmpty() } else null
}

fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

fun JsonObject.bool(key: String): Boolean? =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }

fun JsonObject.num(key: String): Double? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
