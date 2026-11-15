package es.ual.notifier.data.remote

import android.content.Context

/**
 * Verifica la sesión iniciada por el usuario en la web oficial de aulavirtual.ual.es
 * y obtiene su identificador y nombre desde `/learn/api/v1/users/me`.
 *
 * No maneja ni almacena contraseñas: toda la autenticación ocurre directamente
 * en la página oficial de la UAL.
 */
class AuthManager(context: Context) {

    private val client = BbClient.get(context)
    private val session = client.session

    /**
     * Tras iniciar sesión en el WebView oficial, obtiene el ID y nombre del usuario
     * desde la API de Blackboard y marca la sesión como activa.
     */
    fun finishLogin(): String {
        val me = client.currentUser()
        val id = me.str("id") ?: throw SessionExpiredException()
        session.userId = id
        val given = me.obj("name")?.str("given").orEmpty()
        val family = me.obj("name")?.str("family").orEmpty()
        val display = "$given $family".trim().ifEmpty { me.str("userName") ?: "" }
        session.userDisplayName = display
        session.isLoggedIn = true
        return display
    }
}
