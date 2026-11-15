# Política de Privacidad — NotifAula (No oficial)

**Última actualización:** Noviembre de 2026

## 1. Carácter no oficial
**NotifAula** es una herramienta independiente desarrollada por estudiantes y **no está afiliada, patrocinada ni respaldada por la Universidad de Almería (UAL) ni por Anthology Inc. (Blackboard)**.

## 2. Recopilación y tratamiento de datos
**NotifAula NO recopila, almacena ni comparte ningún dato personal en servidores propios ni de terceros.**

- **Conexión exclusiva con el Aula Virtual oficial**: Tanto en Android (`NotifAula.apk`) como en iOS (`NotifAula.js` para Scriptable), todas las consultas de asignaturas, calificaciones, anuncios, materiales y mensajes se realizan directamente desde el dispositivo del usuario contra `https://aulavirtual.ual.es` mediante HTTPS. Ningún dato académico ni personal se envía ni redirige a ningún otro dominio.
- **Sin acceso a tu contraseña**: El inicio de sesión se realiza directamente en la página web oficial de la Universidad de Almería (`https://aulavirtual.ual.es`). La herramienta **nunca solicita, intercepta ni almacena tu contraseña**:
  - En **Android**: únicamente conserva las cookies de sesión de Blackboard cifradas localmente en el dispositivo (`EncryptedSharedPreferences` con AES-256).
  - En **iOS (Scriptable)**: utiliza el almacén de cookies nativo de WebKit del propio iPhone y el llavero seguro de iOS (`Keychain`).
- **Historial de notificaciones y asignaturas**: Se guarda únicamente en el almacenamiento privado local del dispositivo (base de datos SQLite `Room` en Android, `FileManager` local en Scriptable para iOS, o `localStorage` local en la PWA).
- **Sin analíticas ni rastreadores**: No incluye SDKs de analítica, publicidad ni telemetría de ningún tipo.

## 3. Eliminación de datos
El usuario puede eliminar en cualquier momento la totalidad de los datos almacenados (cookies, credenciales cifradas e historial de avisos) pulsando el botón **Cerrar sesión** dentro de la aplicación/script o desinstalándola de su dispositivo.
