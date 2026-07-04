# ============================================================================
# PocketCraft ProGuard / R8 Rules
# ============================================================================

# ── Kotlin ───────────────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Moshi ────────────────────────────────────────────────────────────────────
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
# Keep all generated Moshi JSON adapters for network model classes
-keep class com.pocketcraft.app.core.downloader.**JsonAdapter { *; }
-keep class com.pocketcraft.app.core.downloader.** { *; }

# ── Retrofit ─────────────────────────────────────────────────────────────────
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# ── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okio.**

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class com.pocketcraft.app.data.** { *; }

# ── Hilt ─────────────────────────────────────────────────────────────────────
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Enums (used by Room TypeConverters and Moshi) ────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── General Android ───────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable    # keep crash stack traces readable
-renamesourcefileattribute SourceFile

# ── Suppress irrelevant warnings ──────────────────────────────────────────────
-dontwarn sun.misc.**
-dontwarn java.lang.instrument.**
-dontwarn javax.annotation.**
