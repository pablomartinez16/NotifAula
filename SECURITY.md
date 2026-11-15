# 🔒 Política de Seguridad — NotifAula

## Versiones con soporte

Actualmente se mantienen con actualizaciones de seguridad las siguientes versiones de **NotifAula**:

| Versión | Con soporte |
| ------- | ----------- |
| 1.0.x   | ✅ Sí       |

## Arquitectura de seguridad de la aplicación

- **Sin servidores intermedios**: Toda la comunicación se realiza exclusivamente desde el dispositivo del usuario (Android o iOS) contra `https://aulavirtual.ual.es` mediante HTTPS/TLS. No se envía ni redirige ningún dato a dominios externos.
- **Sin acceso a contraseñas**: El usuario se autentica directamente en la página web oficial de la Universidad de Almería. La aplicación nunca solicita, intercepta ni almacena contraseñas.
- **Almacenamiento cifrado en el dispositivo**:
  - En **Android**: las cookies de sesión se guardan únicamente en el almacenamiento privado del dispositivo utilizando `EncryptedSharedPreferences` con cifrado `AES256_SIV` / `AES256_GCM` respaldado por Android Keystore.
  - En **iOS (Scriptable)**: la sesión reside en el contenedor WebKit del sistema y los metadatos básicos de sesión en el llavero cifrado de iOS (`Keychain`).

## Cómo informar de una vulnerabilidad

Si descubres algún problema de seguridad en **NotifAula**, por favor **no abras un Issue público**. En su lugar:

1. Ve a la pestaña **[Security → Report a vulnerability](https://github.com/pablomartinez16/NotifAula/security/advisories/new)** de este repositorio para enviar un aviso privado.
2. Describe los pasos para reproducir el problema y su posible impacto.
3. Revisaremos el reporte lo antes posible y publicaremos un parche en una nueva versión dentro de **GitHub Releases**.
