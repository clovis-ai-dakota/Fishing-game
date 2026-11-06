buildscript {
    // Same detection as settings.gradle.kts: only put the Android Gradle Plugin on the
    // classpath when an Android SDK is present.
    val localProperties = File(rootDir, "local.properties")
    val androidSdkAvailable = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        (localProperties.isFile && localProperties.readText().contains("sdk.dir"))
    if (androidSdkAvailable) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools.build:gradle:8.13.0")
        }
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    // Firebase (analytics and crash reports). :app applies them only once the developer has added
    // app/google-services.json (see docs/firebase-setup.md), so every build works without it.
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}
