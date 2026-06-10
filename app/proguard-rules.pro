# FlipLK — ProGuard / R8 Rules
# These rules prevent R8 from stripping or renaming classes that are accessed
# via reflection, JNI, or dynamic class loading in Firebase, Cloudinary, and Coil.

# ============================================================
# 1. KOTLIN — Standard rules for coroutines and serialization
# ============================================================

# Keep Kotlin metadata used by reflection (required for coroutines, serialization)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep Kotlin coroutine debug info (required for kotlinx.coroutines internal dispatcher)
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.android.** { *; }

# ============================================================
# 2. FIREBASE — Firestore, Auth, Messaging
# ============================================================

# Firestore data model classes use reflection for serialization/deserialization.
# Keep all classes in our model package so field names are not renamed.
-keep class com.pixeleye.welandapola.model.** { *; }

# Keep Firebase Firestore internals used via reflection
-keepattributes RuntimeVisibleAnnotations
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep Firebase Auth providers
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep Firebase messaging service entry point
-keep class com.pixeleye.welandapola.data.FirebaseService { *; }

# ============================================================
# 3. CLOUDINARY — Android SDK
# ============================================================

# Cloudinary uses reflection to find UploadCallback implementations
-keep class com.cloudinary.** { *; }
-dontwarn com.cloudinary.**

# Keep our CloudinaryManager object (accessed from multiple screens)
-keep class com.pixeleye.welandapola.data.CloudinaryManager { *; }

# ============================================================
# 4. COIL — Image loading library
# ============================================================

-keep class coil.** { *; }
-dontwarn coil.**

# ============================================================
# 5. OKHTTP / OKIO (used by Firebase and Coil internally)
# ============================================================

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ============================================================
# 6. ANDROID / ANDROIDX — Safe general rules
# ============================================================

# Keep custom Application subclass (if any), Activities, Services, Receivers
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application

# Keep FlipLKNotificationListener (extends NotificationListenerService)
-keep class com.pixeleye.welandapola.WelandapolaNotificationListener { *; }

# Keep MainActivity and all its companion objects
-keep class com.pixeleye.welandapola.MainActivity { *; }

# ============================================================
# 7. JETPACK COMPOSE — Safe rules
# ============================================================

# Compose uses reflection for state restoration; keep Saver implementations
-keepclassmembers class * {
    @androidx.compose.runtime.saveable.Saver *;
}

# ============================================================
# 8. BUILDCONFIG — Prevent stripping of build constants
# ============================================================

-keep class com.pixeleye.welandapola.BuildConfig { *; }

# ============================================================
# 9. DEBUGGING — Preserve line numbers in crash stack traces
# ============================================================

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile