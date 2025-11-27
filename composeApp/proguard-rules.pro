# Keep Ktor classes
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Ktor client plugins
-keep class io.ktor.client.plugins.** { *; }
-keep class io.ktor.client.engine.** { *; }

# Keep Coil classes
-keep class coil3.** { *; }
-dontwarn coil3.**

# Keep kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep Compose runtime
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# Keep all @Composable functions
-keep @androidx.compose.runtime.Composable public class * {
    *;
}

# Keep SQLDelight classes
-keep class app.cash.sqldelight.** { *; }
-keep class com.opoojkk.podium.db.** { *; }
-keepclassmembers class com.opoojkk.podium.db.** { *; }

# Keep Rust JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep data classes and serializable classes
-keep @kotlinx.serialization.Serializable class com.opoojkk.podium.data.model.** { *; }
-keepclassmembers class com.opoojkk.podium.data.model.** {
    <init>(...);
    <fields>;
}

# Keep Android Media classes
-keep class androidx.media.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media.**
-dontwarn androidx.media3.**

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimization
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

