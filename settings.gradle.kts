pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "DeepLine"

include(":core")
include(":server")

// The Android app module needs an Android SDK. It is included whenever one is
// configured (ANDROID_HOME / ANDROID_SDK_ROOT, or sdk.dir in local.properties),
// so the pure-Kotlin game core can still be built and tested on machines without it.
val localProperties = settingsDir.resolve("local.properties")
val androidSdkAvailable = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (localProperties.isFile && localProperties.readText().contains("sdk.dir"))

if (androidSdkAvailable) {
    include(":app")
} else {
    logger.lifecycle("No Android SDK found: building only the :core module.")
}
