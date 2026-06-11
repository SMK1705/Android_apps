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
    }
}

rootProject.name = "SMK-Android"

// One app per directory under apps/. Add new apps here, e.g. include(":apps:habits").
include(":apps:taskmind")

// Shared library modules live under core/ — add them when a second app needs to reuse code,
// e.g. include(":core:llm", ":core:security", ":core:designsystem").
