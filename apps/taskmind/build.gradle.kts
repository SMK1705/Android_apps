import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.hilt.android)
}

// Maps SDK key is kept out of git: set MAPS_API_KEY in local.properties (gitignored). Falls back to
// an empty string so the build still succeeds without it (the embedded map just won't load tiles).
val mapsApiKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("MAPS_API_KEY", "")

android {
    namespace = "com.rajasudhan.taskmind"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rajasudhan.taskmind"
        minSdk = 35
        targetSdk = 36
        versionCode = 4
        versionName = "4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Injected into the manifest's com.google.android.geo.API_KEY meta-data.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric needs merged Android resources/manifest on the unit-test classpath.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Export the current Room schema (schemas/<db>/5.json) so future migrations can be tested against
// a committed baseline. Historical v1–v4 schemas predate this and aren't exported; the migration
// test reconstructs v1 directly and lets Room validate the full chain.
ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
}

// Keep the forked unit-test JVM bounded (Robolectric loads a full Android runtime) and headless so
// KSP/Robolectric don't spin up an AWT event thread. Bounds peak memory on constrained machines.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "1g"
    jvmArgs("-Djava.awt.headless=true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.retrofit)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    "ksp"(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.vosk.android)
    implementation(libs.tesseract4android)
    implementation(libs.reorderable)
    implementation("androidx.hilt:hilt-work:1.2.0")
    "ksp"("androidx.hilt:hilt-compiler:1.2.0")
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    // Compose UI testing on the JVM (Robolectric) — runs in the existing testDebugUnitTest job.
    // (ui-test-manifest is on debugImplementation below, which is on the unit-test classpath.)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    "kspAndroidTest"(libs.hilt.compiler)
    androidTestUtil(libs.androidx.test.orchestrator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}