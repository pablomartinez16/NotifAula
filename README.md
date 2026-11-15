# 🔔 NotifAula (No oficial)

> **📥 [Descargar última versión (.APK) en GitHub Releases](../../releases/latest)**
>
> **Aviso legal:** Aplicación independiente y de código abierto **no oficial**. No está afiliada, respaldada ni patrocinada por la Universidad de Almería (UAL) ni por Anthology / Blackboard. Consulta la [Política de Privacidad](PRIVACY.md) y la [Licencia MIT](LICENSE).

Aplicación Android ligera (Kotlin + Jetpack Compose) que comprueba periódicamente el **Aula Virtual de la Universidad de Almería** (`aulavirtual.ual.es`, basado en Blackboard Ultra) y envía notificaciones push locales con **enlace directo al navegador del sistema**.

## ✨ Características

- **100 % universal y privada**: no contiene ningún ID de usuario ni curso preconfigurado. Al iniciar sesión, obtiene automáticamente tu usuario desde `/learn/api/v1/users/me` y tus asignaturas activas desde `/learn/api/v1/users/{id}/memberships`.
- **Inicio de sesión 100 % en la web oficial**:
  - El usuario inicia sesión directamente en la página oficial de `https://aulavirtual.ual.es` (mostrando la URL y el candado HTTPS en todo momento).
  - **La app nunca pide, ve ni guarda tu contraseña**: únicamente conserva la cookie de sesión (`BbRouter`) cifrada con `EncryptedSharedPreferences` (AES-256) para comprobar novedades en segundo plano. (El WebView se usa exclusivamente para este login; al pulsar cualquier notificación se abre siempre el **navegador del sistema**).
- **Detección completa de novedades**:
  - 📢 **Anuncios** de cada asignatura (`/learn/api/v1/courses/{courseId}/announcements`)
  - 🎓 **Calificaciones** nuevas o modificadas (`/learn/api/v1/courses/{courseId}/gradebook/grades`)
  - 📄 **Contenido y materiales** nuevos recorriendo el árbol de carpetas (`/learn/api/v1/courses/{courseId}/contents/{id}/children`)
  - 📅 **Tareas y entregas** próximas (`/learn/api/v1/calendars/todo/studentItems`)
  - ✉️ **Mensajes / conversaciones** sin leer (`/learn/api/v1/courses/conversations/counts`)
- **Línea base silenciosa**: en la primera sincronización registra el estado actual de tus asignaturas sin inundarte de notificaciones antiguas; solo notifica lo que aparezca o cambie a partir de ese momento.
- **Apertura directa en el navegador del sistema**: al tocar una notificación (o una tarjeta del historial), se marca como leída y se abre la URL directa en tu navegador predeterminado (`Intent.ACTION_VIEW`), nunca en un navegador embebido.
- **Segundo plano sin notificación persistente**: usa `WorkManager` cada ~15 minutos con reprogramación automática al reiniciar el teléfono (`BootReceiver`).

## 🛠️ Cómo compilar e instalar

1. Clona o abre la carpeta del proyecto (`NotifAula`) en **Android Studio** (Ladybug / Meerkat o superior).
2. Android Studio descargará automáticamente el Gradle Wrapper y el Android SDK (API 35).
3. Conecta tu móvil Android (con *Depuración por USB* activa) o pulsa **Build > Build App Bundle(s) / APK(s) > Build APK(s)** para generar el APK instalable.
4. Al abrir la app por primera vez:
   - Acepta el permiso de notificaciones.
   - Pulsa en el aviso de **Ajustes de batería** para permitir la ejecución sin restricciones en segundo plano.
   - Inicia sesión con tus credenciales de la UAL.
