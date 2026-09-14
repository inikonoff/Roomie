plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.roomie.probe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roomie.probe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Deliberately nothing beyond the AAR every Android app already needs. No Compose, no Room,
    // no WorkManager, no DataStore, no permissions of any kind — this app does nothing but show
    // one line of text. Its only job is to prove whether the OS itself kills any freshly
    // installed app ~1.1s after launch on this device, independent of anything Roomie does.
    implementation("androidx.appcompat:appcompat:1.7.0")
}
