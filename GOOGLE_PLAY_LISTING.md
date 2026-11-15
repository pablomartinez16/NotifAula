# 🏪 Ficha para Google Play Store — NotifAula

Guía lista para copiar y pegar en **Google Play Console** cumpliendo la política de suplantación de identidad (*Impersonation Policy*) y la sección de Seguridad de los Datos (*Data Safety*).

---

## 1. Nombre de la aplicación (Máx. 30 caracteres)
```text
NotifAula - Avisos (No oficial)
```
*(30 caracteres — o simplemente `NotifAula` [9 caracteres]).*

---

## 2. Descripción breve (Máx. 80 caracteres)
```text
Notificaciones de notas, anuncios, entregas y mensajes con enlace directo.
```
*(74 caracteres).*

---

## 3. Descripción completa (Máx. 4.000 caracteres)
```text
Recibe notificaciones en tu móvil cuando se publique una nueva calificación, anuncio, documento, entrega o mensaje en tus asignaturas del Aula Virtual de la Universidad de Almería (aulavirtual.ual.es) y ábrelas directamente en tu navegador predeterminado con un solo toque.

⚠️ AVISO IMPORTANTE (APLICACIÓN NO OFICIAL):
Esta es una herramienta estudiantil independiente y de código abierto. NO es una aplicación oficial ni está afiliada, patrocinada, autorizada ni respaldada por la Universidad de Almería (UAL) ni por Anthology Inc. (Blackboard).

🔔 ¿QUÉ NOTIFICA LA APLICACIÓN?
• 🎓 Calificaciones: Aviso inmediato cuando se publica o actualiza una nota, incluyendo la puntuación sobre el total, la media de la clase (si está disponible) y los comentarios de corrección del profesor.
• 📢 Anuncios: Texto completo del anuncio y fecha de publicación de cada asignatura.
• 📄 Materiales y contenidos: Nuevos documentos, archivos PDF, carpetas, cuestionarios, foros o enlaces, indicando la ruta exacta de carpetas donde se han publicado (p. ej. "Teoría › Tema 5").
• 📅 Entregas y tareas: Nuevas fechas límite del calendario de tus asignaturas.
• ✉️ Mensajes: Avisos por asignatura cuando recibes nuevas conversaciones o mensajes sin leer, mostrando asunto, remitente y vista previa.

🌐 ENLACE DIRECTO A TU NAVEGADOR
Al pulsar cualquier notificación (o tarjeta del historial dentro de la app), se abre el enlace directo en el navegador del sistema de tu teléfono (Chrome, Firefox, Brave, etc.), sin visores internos lentos ni pantallas intermedias.

🔒 PRIVACIDAD Y SEGURIDAD (CERO SERVIDORES INTERMEDIOS)
• Inicio de sesión 100 % en la web oficial: Te autenticas directamente en https://aulavirtual.ual.es.
• La aplicación NUNCA ve, solicita ni almacena tu contraseña.
• Sin servidores externos: Todas las comprobaciones se realizan directamente desde tu propio teléfono contra aulavirtual.ual.es mediante conexión cifrada HTTPS.
• Tus cookies de sesión se guardan únicamente en tu dispositivo protegidas con cifrado AES-256 (Android Keystore) y se eliminan por completo al cerrar sesión.
• Sin anuncios, sin rastreadores y sin analíticas.
```

---

## 4. Cómo rellenar el cuestionario "Seguridad de los datos" (Data Safety) en Google Play Console

En Google Play Console te harán estas preguntas obligatorias:

1. **¿Tu aplicación recopila o comparte alguno de los tipos de datos de usuario requeridos?**
   👉 **NO** *(La recopilación en Google Play se define como transmitir datos fuera del dispositivo del usuario hacia ti o un tercero. Como la app solo se comunica directamente con `aulavirtual.ual.es` donde el usuario ya tiene cuenta y no envía nada a ningún servidor del desarrollador, la respuesta es **No**).*
2. **URL de la Política de Privacidad**:
   👉 Sube el archivo `PRIVACY.md` a tu repositorio de GitHub y pega la URL pública (por ejemplo: `https://raw.githubusercontent.com/TU_USUARIO/TU_REPO/main/PRIVACY.md` o mediante GitHub Pages).
3. **Acceso a la aplicación (Credenciales de prueba para los revisores de Google)**:
   👉 En la sección *"Acceso a la aplicación"* de Play Console, selecciona *"Algunas funciones están restringidas"* e indica en las instrucciones para el revisor que es un cliente para estudiantes con cuenta institucional de `aulavirtual.ual.es` (o proporciona unas credenciales de prueba temporales si quieres que pasen de la pantalla de bienvenida).
4. **Categoría recomendada**:
   👉 **Educación** o **Herramientas**.
5. **Clasificación de contenido (IARC)**:
   👉 **PEGI 3 / Para todos**.
