@file:Suppress("UnstableApiUsage")

// Gradle settings DSL uses incubating APIs (e.g., repositories in dependencyResolutionManagement).
// Suppress IDE unstable API inspection while preserving the existing resolution setup.
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
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "IFocus"
include(":app")
