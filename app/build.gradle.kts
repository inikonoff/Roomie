plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// The Google Play *upload* key, supplied by the environment (CI secrets, or a developer's shell) and
// never committed. When it isn't there — a plain local build, a fork's pull request — release builds
// fall back to the debug key below, which Play refuses, so such a build can't be published by accident.
val uploadKeystoreFile: String? = System.getenv("CULLECT_KEYSTORE_FILE")

android {
    namespace = "com.cullect.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cullect.app"
        minSdk = 26
        targetSdk = 36
        // Play rejects an upload whose versionCode isn't higher than every earlier one. CI's run
        // number only ever goes up, so every pipeline build is uploadable without bookkeeping;
        // local builds stay at 1.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.0"
    }

    signingConfigs {
        getByName("debug") {
            // Committed on purpose: it's a debug-only key (never used for a Play Store release),
            // and every debug build needs the *same* signature so a new APK from CI installs as an
            // update over the previous one instead of Android refusing it as a different app and
            // forcing an uninstall first. Without this, AGP would generate a fresh, random
            // ~/.android/debug.keystore on every CI runner (a new ephemeral machine each time).
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (uploadKeystoreFile != null) {
            create("release") {
                storeFile = file(uploadKeystoreFile)
                storePassword = System.getenv("CULLECT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CULLECT_KEY_ALIAS")
                keyPassword = System.getenv("CULLECT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Upload key when the environment provides it (see uploadKeystoreFile); otherwise the
            // debug key, which keeps a keyless build — and the on-device framestats runs that use a
            // release-optimized binary — working, but is rejected by Play.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3") {
        // Pulls in a native lib (libandroidx.graphics.path.so) for shape-morphing APIs
        // (MaterialShapes/RoundedPolygon) that Cullect's own UI never uses. That native lib has
        // caused 16 KB page-size crashes on newer Android 15 devices; drop it rather than carry
        // dead weight that can crash the app before a single line of our code even runs.
        exclude(group = "androidx.graphics", module = "graphics-shapes")
        exclude(group = "androidx.graphics", module = "graphics-path")
    }
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // DataStore. Pinned to 1.0.0 (pre-dating the 1.1.0 multi-process "shared counter" feature,
    // which ships as a native lib, libdatastore_shared_counter.so) — Cullect is single-process and
    // never needed that guarantee, and that native lib is a second suspect in 16 KB page-size
    // crashes on newer Android 15 devices.
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil 3 for MediaStore image loading with downsampling
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-video:3.0.0")

    // Media3 ExoPlayer for inline video playback in the swipe card
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
