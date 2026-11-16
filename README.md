# 🔔 NotifAula (No oficial)

### **¿Estás harto de que la app oficial no funcione como debería?**
**Simplemente recibe una notificación y entra a través de la web. Más sencillo, sin rodeos ni fallos.**

> **🤖 [Descargar APK (Android)](../../releases/latest)** · **🍎 [Script iOS (`NotifAula.js`)](ios/NotifAula.js)** · **📲 [Web App PWA (iOS)](https://pablomartinez16.github.io/NotifAula/pwa.html)** · **🌐 [Página web](https://pablomartinez16.github.io/NotifAula/)**
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

## 🍎 Cómo usarla en iPhone (iOS)

En iOS dispones de dos opciones **100 % gratuitas, sin cuenta de desarrollador de pago y sin servidores intermedios** que puedes usar juntas o por separado:

### Opción A: Motor en segundo plano con Scriptable + Atajos de iOS (Recomendada)
1. Instala gratis [**Scriptable**](https://apps.apple.com/es/app/scriptable/id1405459188) desde la App Store y activa sus permisos en *Ajustes → Notificaciones → Scriptable*.
2. Descarga [`ios/NotifAula.js`](ios/NotifAula.js) (o desde la [web de NotifAula](https://pablomartinez16.github.io/NotifAula/#ios)) e impórtalo en Scriptable (*Compartir → Scriptable → Add to My Scripts*).
3. En **Scriptable**, si ves el editor de código abierto, pulsa **Listo / Done** arriba (**no** pulses el botón ▶️ abajo a la derecha) y toca **NotifAula** en tu lista. Pulsa **«🔑 Iniciar sesión en aulavirtual.ual.es»**, autentícate en la web oficial de la UAL y pulsa **Cerrar / Done** arriba a la izquierda cuando carguen tus asignaturas (`✅ ¡Listo!`).
4. Configura la automatización en **Atajos** (*Shortcuts*) para que compruebe cada ~5 minutos en segundo plano:
   1. Abre **Atajos → Automatización → `+` → App**.
   2. Selecciona tus 3-4 apps diarias (*WhatsApp, Instagram, Safari...*), marca **«Al cerrarse»** y **«Ejecutar inmediatamente»**, y pulsa **Siguiente**.
   3. Toca **«Nueva automatización en blanco»** → busca **Scriptable → Run Script** → elige `NotifAula` y en el desplegable `>` desmarca **«Mostrar al ejecutar»**.
   4. `NotifAula.js` incluye un filtro interno (~4-5 min) para no repetir comprobaciones ni gastar batería aunque abras y cierres apps seguidas. Al tocar cualquier notificación se abrirá el enlace directo en **Safari**.

### Opción B (Alternativa): Web App instalable (PWA) para iOS
1. Abre [**`https://pablomartinez16.github.io/NotifAula/pwa.html`**](https://pablomartinez16.github.io/NotifAula/pwa.html) desde **Safari** en tu iPhone.
2. Pulsa el botón **Compartir (⬆️)** → **«Añadir a pantalla de inicio»** para instalar **NotifAula** como una Web App (PWA) a pantalla completa con la misma interfaz que en Android.
3. Desde la PWA puedes:
   - Activar las **notificaciones nativas de la PWA** en iOS 16.4+ (`ServiceWorker`).
   - Sincronizar en 1 toque con **Scriptable** (`scriptable:///run/NotifAula`) y ver todo el historial de avisos por asignatura.
   - O usar el **Marcador Inteligente de Safari** incluido (sin instalar ninguna app extra) para escanear tus asignaturas cuando entres a `aulavirtual.ual.es`.

## 🛠️ Cómo compilar e instalar en Android

1. Clona o abre la carpeta del proyecto (`NotifAula`) en **Android Studio** (Ladybug / Meerkat o superior).
2. Android Studio descargará automáticamente el Gradle Wrapper y el Android SDK (API 35).
3. Conecta tu móvil Android (con *Depuración por USB* activa) o pulsa **Build > Build App Bundle(s) / APK(s) > Build APK(s)** para generar el APK instalable.
4. Al abrir la app por primera vez:
   - Acepta el permiso de notificaciones.
   - Pulsa en el aviso de **Ajustes de batería** para permitir la ejecución sin restricciones en segundo plano.
   - Inicia sesión con tus credenciales de la UAL.
