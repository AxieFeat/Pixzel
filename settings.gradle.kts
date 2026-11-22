@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "pixzel"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS

    repositories {
        mavenCentral()
        maven("https://repo.arial.su/snapshots/")
        maven("https://repo.arial.su/releases/")
    }
}

project("core")
project("common")
project("test")
project("samples")
project("arc")
project("demo")

/**
 * Includes a module with the root project prefix.
 */
fun project(moduleName: String) {
    include("${rootProject.name}-$moduleName")
}
