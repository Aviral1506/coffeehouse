# app/proguard-rules.pro
# Phase 5 — R8 keep rules for the release build.
# minifyEnabled=true + shrinkResources=true are now active; without these
# rules R8 would strip or rename classes that the framework instantiates
# by reflection (services, receivers, AudioEffect subclasses, WorkManager
# workers), causing silent runtime failures on the release APK.

# Keep all app classes — prevents R8 from renaming service/receiver
-keep class com.coffeehouse.** { *; }

# Keep AudioEffect subclasses used by name at runtime
-keep class android.media.audiofx.** { *; }

# Keep WorkManager worker so it can be instantiated by class name
-keep class androidx.work.** { *; }

# Keep DataStore — uses reflection internally
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences {
    *;
}

# Suppress notes about missing classes in dependencies
-dontnote **
