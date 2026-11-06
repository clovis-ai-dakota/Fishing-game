import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase is optional: its Gradle plugins (which read the config and upload Crashlytics mapping
// files) only run once app/google-services.json exists. Without it the app builds and runs the
// same, and analytics/crash reports stay off (see analytics/Telemetry.kt, docs/firebase-setup.md).
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.deepline.fishing"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.deepline.fishing"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Online battle server. Override with -PdeeplineServerUrl=wss://your-host/battle.
        // The default reaches a server running on the development machine from the Android emulator.
        val serverUrl = (project.findProperty("deeplineServerUrl") as String?) ?: "ws://10.0.2.2:8080/battle"
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$serverUrl\"")

        // The published privacy policy, linked from the consent sheet and Settings > Privacy. Set
        // it before releasing: -PdeeplinePrivacyPolicyUrl=https://your-site/privacy (see
        // docs/privacy-policy.md). The placeholder uses a reserved domain that never resolves.
        val privacyUrl = (project.findProperty("deeplinePrivacyPolicyUrl") as String?) ?: "https://deepline.example/privacy"
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyUrl\"")
    }

    buildTypes {
        debug {
            // Debug builds pay through the in-app test store (approve, decline, fail or leave
            // pending). Build with -PdeeplinePlayBilling=true to test real Google Play billing.
            val play = (project.findProperty("deeplinePlayBilling") as String?)?.toBoolean() ?: false
            buildConfigField("boolean", "USE_PLAY_BILLING", play.toString())
            // Features built and testable now but shipped in a later update: on in debug builds.
            buildConfigField("boolean", "CLUBS_ENABLED", clubs(default = true))
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "USE_PLAY_BILLING", "true")
            // Clubs ship in their own update: off in release builds until then (-PdeeplineClubs=true).
            buildConfigField("boolean", "CLUBS_ENABLED", clubs(default = false))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric UI tests render the real app on the JVM.
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST")
        }
    }
}

/** A feature flag's value: `-PdeeplineClubs=true|false` overrides the build type's default. */
fun clubs(default: Boolean): String =
    ((project.findProperty("deeplineClubs") as String?)?.toBoolean() ?: default).toString()

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The online UI test runs the real battle server as a separate process.
tasks.withType<Test>().configureEach {
    // Robolectric UI test classes each get a fresh JVM so app state (e.g. the coroutine main
    // dispatcher) never leaks from one class into the next.
    forkEvery = 1
    dependsOn(":server:installDist")
    systemProperty("deepline.serverBin", rootProject.file("server/build/install/server/bin/server").absolutePath)
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // Analytics and crash reports (Firebase). Nothing is collected until the player agrees, and
    // nothing at all without app/google-services.json: see analytics/Telemetry.kt.
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    // Google Play Billing (euro purchases; see billing/PlayStore.kt).
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    val ktorVersion = "3.3.3"
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
