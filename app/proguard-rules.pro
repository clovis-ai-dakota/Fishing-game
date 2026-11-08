# R8 rules for Deep Line's release builds (on top of proguard-android-optimize.txt and the rules
# every library ships: kotlinx.serialization, coroutines, OkHttp, Play Billing, Play Review,
# Firebase, WorkManager, Compose). Keep this file short: every -keep stops R8 from shrinking.
# Background: docs/research/launch.md section 5.

# Readable crash reports (Android vitals and Crashlytics retrace with the bundle's mapping file).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# review-ktx 2.0.2 references an annotation from a newer play-services-basement than the one it
# depends on; it's only an annotation (R8 reported it as the one missing class).
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
