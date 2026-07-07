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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Tesseract4Android (on-device OCR)
    }
}

rootProject.name = "Android_apps"

// One app per directory under apps/. Add new apps here, e.g. include(":apps:habits").
include(":apps:taskmind")

// The TaskMind Wear OS companion (#216): wrist voice-capture + a next-due tile, talking to the phone
// over the Data Layer. A separate application module — it builds/installs its own watch APK.
include(":apps:taskmind-wear")

// Shared library modules live under core/ — add them when a second app needs to reuse code,
// e.g. include(":core:llm", ":core:security", ":core:designsystem").
