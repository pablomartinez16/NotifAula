# Reglas de ProGuard para UAL Notifier
# Archivo de configuración para la ofuscación y optimización del código

# ============================================================
# Reglas para Retrofit
# ============================================================
# Mantener las interfaces de los servicios de Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Mantener las clases genéricas de Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# No ofuscar las interfaces de servicio de Retrofit
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Mantener los métodos con anotaciones HTTP de Retrofit
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ============================================================
# Reglas para Gson
# ============================================================
# Mantener las clases de modelo que Gson necesita para serializar/deserializar
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Mantener las clases anotadas con @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Evitar que Gson falle con tipos genéricos
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ============================================================
# Reglas para Room
# ============================================================
# Mantener las clases generadas por Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================================
# Reglas para OkHttp
# ============================================================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================================
# Reglas generales para Kotlin
# ============================================================
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
