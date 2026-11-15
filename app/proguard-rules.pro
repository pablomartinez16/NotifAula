# Reglas de ProGuard / R8 para NotifAula (modo Release)

# Conservar clases serializadas con Gson (StoredCookie en SessionStore y TypeToken)
-keep class es.ual.notifier.data.remote.SessionStore$StoredCookie { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Conservar firmas genéricas y atributos necesarios para Gson y corrutinas
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# Room Database y entidades
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Google Tink (usado internamente por EncryptedSharedPreferences)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
