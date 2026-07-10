plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rajasudhan.taskmind.wear"
    compileSdk = 37

    defaultConfig {
        // Data Layer pairs the phone + watch apps by matching applicationId AND signing cert, so this
        // MUST equal the phone module's applicationId (namespace differs, to keep the R/BuildConfig apart).
        applicationId = "com.rajasudhan.taskmind"
        minSdk = 30      // Wear OS 3+
        targetSdk = 34   // Wear OS 5
        versionCode = 6
        versionName = "5.1"
    }

    signingConfigs {
        // Same committed debug keystore as the phone app — the Data Layer requires matching signatures.
        getByName("debug") {
            storeFile = file("../taskmind/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            optimization { enable = false }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    // Compose-for-Wear UI.
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    // The next-due tile.
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.concurrent.futures)
    // Phone <-> watch Data Layer.
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
