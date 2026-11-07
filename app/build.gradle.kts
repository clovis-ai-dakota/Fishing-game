import java.util.Properties
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

// ---- Release builds (see docs/release-checklist.md and docs/research/launch.md) ----------------

/** A Gradle property (-Pname=..., or ~/.gradle/gradle.properties), else an environment variable. */
fun setting(property: String, env: String? = null): String? =
    (project.findProperty(property) as String?)?.takeIf { it.isNotBlank() }
        ?: env?.let { System.getenv(it) }?.takeIf { it.isNotBlank() }

// Version: app/version.properties (+1 versionCode for every upload to Play), overridable on CI.
val versionFile = Properties().apply { file("version.properties").inputStream().use { load(it) } }
val appVersionCode = (setting("deeplineVersionCode") ?: versionFile.getProperty("versionCode")).toInt()
val appVersionName = setting("deeplineVersionName") ?: versionFile.getProperty("versionName")

// The upload key (Play App Signing keeps the real app signing key). Never commit it: put these in
// ~/.gradle/gradle.properties or the CI's secret environment variables. Without them release
// builds are unsigned (fine for checks and CI, refused by Play).
val uploadStoreFile = setting("deeplineUploadStoreFile", "DEEPLINE_UPLOAD_STORE_FILE")
val uploadStorePassword = setting("deeplineUploadStorePassword", "DEEPLINE_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = setting("deeplineUploadKeyAlias", "DEEPLINE_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = setting("deeplineUploadKeyPassword", "DEEPLINE_UPLOAD_KEY_PASSWORD")
val uploadKeyConfigured = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword).all { it != null }

// Server and privacy policy addresses (defaults are for development only).
val serverUrl = setting("deeplineServerUrl") ?: "ws://10.0.2.2:8080/battle"
val privacyUrl = setting("deeplinePrivacyPolicyUrl") ?: "https://deepline.example/privacy"
// R8 shrinks and optimises release builds; -PdeeplineMinify=false turns it off (emergency only).
val minifyRelease = setting("deeplineMinify")?.toBoolean() ?: true

android {
    namespace = "com.deepline.fishing"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.deepline.fishing"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        // Online battle server. Override with -PdeeplineServerUrl=wss://your-host/battle.
        // The default reaches a server running on the development machine from the Android emulator
        // (cleartext ws://, which only debug builds allow: see res/xml/network_security_config.xml).
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$serverUrl\"")

        // The published privacy policy, linked from the consent sheet and Settings > Privacy. Set
        // it before releasing: -PdeeplinePrivacyPolicyUrl=https://your-site/privacy (see
        // docs/privacy-policy.md). The placeholder uses a reserved domain that never resolves.
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyUrl\"")
    }

    signingConfigs {
        if (uploadKeyConfigured) {
            create("upload") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    bundle {
        // Keep every language in the base APK: the in-app language picker must be able to switch to
        // a language the phone isn't set to (Play would otherwise only install the phone's ones).
        language { enableSplit = false }
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
            // R8: shrinks, optimises and obfuscates (libraries ship their own keep rules; ours are in
            // proguard-rules.pro). The bundle carries the mapping file for Play's crash reports.
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (uploadKeyConfigured) signingConfigs.getByName("upload") else null
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

// Before a release build, list what would make it unfit for Google Play. Builds for checks and CI
// only warn; the build that is uploaded uses -PdeeplineStrictRelease=true, which fails instead.
gradle.taskGraph.whenReady {
    val releasing = allTasks.any { t ->
        t.project == project && t.name.endsWith("Release") && (t.name.startsWith("bundle") || t.name.startsWith("assemble"))
    }
    if (!releasing) return@whenReady
    val problems = buildList {
        if (!uploadKeyConfigured) add("no upload key (deeplineUpload* Gradle properties or DEEPLINE_UPLOAD_* variables): the build is unsigned and Play will refuse it")
        if (!serverUrl.startsWith("wss://")) add("the game server is $serverUrl: release builds refuse cleartext, build with -PdeeplineServerUrl=wss://<host>/battle")
        if ("deepline.example" in privacyUrl) add("the privacy policy link is the placeholder: build with -PdeeplinePrivacyPolicyUrl=https://...")
        if (!minifyRelease) add("R8 is off (-PdeeplineMinify=false)")
    }
    if (problems.isEmpty()) return@whenReady
    val text = "Release build $appVersionName ($appVersionCode) is not ready for Google Play:\n" + problems.joinToString("\n") { "  - $it" }
    if (setting("deeplineStrictRelease")?.toBoolean() == true) throw GradleException(text)
    logger.warn("WARNING: $text")
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
    // BoM 34.19.0: the newest whose libraries Kotlin 2.2 can read (BoM 35 is built with Kotlin 2.4 metadata).
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    // Local reminders for the daily loop (see daily/Reminders.kt): no push server.
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    // Google Play Billing (euro purchases; see billing/PlayStore.kt).
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // Google Play In-App Review (review/ReviewPrompter.kt): Play's rating card after a happy moment.
    implementation("com.google.android.play:review-ktx:2.0.2")

    val ktorVersion = "3.3.3"
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.work:work-testing:2.10.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
