/**
 * Base Gradle convention plugin for Kotlin/JVM modules.
 */
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(
        rootProject.findProperty("javaVersion").toString().toIntOrNull()
            ?: error("Java version not specified")
    )
}

tasks.test {
    useJUnitPlatform()
}
