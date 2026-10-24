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
        maven("https://maven.pkg.github.com/AxieFeat/Arc") {
            name = "ArcGitHubPackages"
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
            content {
                includeGroup("arc.engine")
            }
        }
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
