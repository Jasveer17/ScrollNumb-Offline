# ScrollNumb ProGuard / R8 Rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room entities (reflection-based column mapping)
-keep class com.scrollnumb.app.core.database.entity.** { *; }

# Accessibility service must keep its exact name (registered in system settings)
-keep class com.scrollnumb.app.service.ScrollAccessibilityService { *; }
-keep class com.scrollnumb.app.service.BlockerOverlayService { *; }

# Firestore document models (deserialized via reflection / toObject)
-keep class com.scrollnumb.app.feature.leaderboard.LeaderboardUser { *; }
-keep class com.scrollnumb.app.data.repository.RemoteProfile { *; }
-keepclassmembers class com.scrollnumb.app.feature.leaderboard.LeaderboardUser { <init>(); }

# Firebase Messaging service
-keep class com.scrollnumb.app.service.ScrollNumbMessagingService { *; }

# Crashlytics: keep file names/line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Kotlin coroutines (debug metadata)
-dontwarn kotlinx.coroutines.**

# Glance widget receiver
-keep class com.scrollnumb.app.widget.** { *; }

# Strip ALL android.util.Log calls from release builds. The accessibility
# service logs verbosely on every poll pass (80-300ms cadence) — string
# building + logcat writes on that hot path cost real CPU. R8 removes the
# calls AND the dead string-concat args entirely. Crash reporting is
# unaffected (Crashlytics hooks the exception, not logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static boolean isLoggable(java.lang.String, int);
}
