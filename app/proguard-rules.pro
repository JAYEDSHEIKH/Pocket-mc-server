# PocketCraft ProGuard rules

# Keep Moshi adapter generated classes
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# Keep Retrofit interfaces
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Room entities and DAOs
-keep class com.pocketcraft.app.data.** { *; }

# Hilt — don't touch generated code
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep enum names (used by Room TypeConverters with .name / .valueOf)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
