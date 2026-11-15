package es.ual.notifier.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import es.ual.notifier.util.Constants
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Almacén cifrado de la sesión del usuario:
 *  - Cookies de sesión de Blackboard (BbRouter, JSESSIONID, AWSALB...) → implementa [CookieJar].
 *  - Token XSRF (se extrae de la propia cookie BbRouter, campo `xsrf:`).
 *  - ID y nombre del usuario (obtenidos de /learn/api/v1/users/me tras el login).
 *
 * No almacena contraseñas: el inicio de sesión se realiza siempre en la web oficial
 * de aulavirtual.ual.es y solo se conservan las cookies de sesión.
 */
class SessionStore private constructor(context: Context) : CookieJar {

    private val gson = Gson()

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Cookies en memoria: nombre → cookie. Solo guardamos las del host del Aula Virtual. */
    private val cookies = ConcurrentHashMap<String, StoredCookie>()

    init {
        prefs.getString(KEY_COOKIES, null)?.let { json ->
            runCatching {
                val type = object : TypeToken<Map<String, StoredCookie>>() {}.type
                val map: Map<String, StoredCookie> = gson.fromJson(json, type)
                cookies.putAll(map)
            }
        }
    }

    // ───────────────────────── CookieJar ─────────────────────────

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.endsWith(Constants.HOST)) return
        var changed = false
        for (c in cookies) {
            if (c.expiresAt < System.currentTimeMillis()) {
                changed = this.cookies.remove(c.name) != null || changed
            } else {
                this.cookies[c.name] = StoredCookie(
                    name = c.name,
                    value = c.value,
                    expiresAt = if (c.persistent) c.expiresAt else 0L,
                    path = c.path
                )
                changed = true
            }
        }
        if (changed) persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.endsWith(Constants.HOST)) return emptyList()
        val now = System.currentTimeMillis()
        return cookies.values
            .filter { it.expiresAt == 0L || it.expiresAt > now }
            .mapNotNull { sc ->
                runCatching {
                    Cookie.Builder()
                        .name(sc.name)
                        .value(sc.value)
                        .domain(Constants.HOST)
                        .path(sc.path.ifBlank { "/" })
                        .secure()
                        .build()
                }.getOrNull()
            }
    }

    /**
     * Importa las cookies obtenidas tras iniciar sesión en la web oficial
     * en el WebView (formato "a=1; b=2").
     */
    fun importCookieHeader(cookieHeader: String) {
        cookieHeader.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { pair ->
            val name = pair.substringBefore("=").trim()
            val value = pair.substringAfter("=").trim()
            if (name.isNotEmpty()) cookies[name] = StoredCookie(name, value, 0L, "/")
        }
        persist()
    }

    fun clearCookies() {
        cookies.clear()
        persist()
    }

    fun hasSessionCookie(): Boolean = cookies.containsKey("BbRouter")

    /**
     * Blackboard incluye el token anti-CSRF dentro de la cookie BbRouter:
     * `expires:...,id:...,sessionId:...,signature:...,site:...,timeout:...,user:...,v:2,xsrf:<TOKEN>`
     */
    fun getXsrfToken(): String? {
        val raw = cookies["BbRouter"]?.value ?: return null
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return decoded.split(",")
            .firstOrNull { it.trim().startsWith("xsrf:") }
            ?.substringAfter("xsrf:")
            ?.trim()
            ?.trim('"')
    }

    private fun persist() {
        prefs.edit().putString(KEY_COOKIES, gson.toJson(HashMap(cookies))).apply()
    }

    // ───────────────────────── Usuario ─────────────────────────

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(v) = prefs.edit().putString(KEY_USER_ID, v).apply()

    var userDisplayName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(v) = prefs.edit().putString(KEY_USER_NAME, v).apply()

    /** ¿Ha completado el usuario el login (y no ha cerrado sesión)? */
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)
        set(v) = prefs.edit().putBoolean(KEY_LOGGED_IN, v).apply()

    /** Borra absolutamente todo (cerrar sesión). */
    fun clearAll() {
        cookies.clear()
        prefs.edit().clear().apply()
    }

    data class StoredCookie(
        val name: String,
        val value: String,
        /** 0 = cookie de sesión (sin caducidad explícita). */
        val expiresAt: Long,
        val path: String
    )

    companion object {
        private const val SECURE_PREFS = "ual_notifier_secure"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LOGGED_IN = "logged_in"

        @Volatile private var instance: SessionStore? = null

        fun get(context: Context): SessionStore =
            instance ?: synchronized(this) {
                instance ?: SessionStore(context.applicationContext).also { instance = it }
            }
    }
}
