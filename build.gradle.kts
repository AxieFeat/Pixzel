import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins { kotlin("jvm") version "2.4.10" apply false }
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    group = "pixzel.engine"
    version = "0.1.0-dev"
    extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(21) }
    if (name != "pixzel-demo") {
        apply(plugin = "maven-publish")
        extensions.configure<JavaPluginExtension> { withSourcesJar() }
        extensions.configure<PublishingExtension> {
            publications.create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set("Pixzel retained Kotlin UI: ${project.name}")
                }
            }
            repositories {
                maven {
                    name = "localArtifacts"
                    url = uri(rootProject.layout.buildDirectory.dir("repository"))
                }
            }
        }
    }
}
tasks.register("verifyBoundaries") {
    doLast {
        listOf("pixzel-core", "pixzel-common").forEach { module ->
            fileTree("$module/src").matching { include("**/*.kt") }.forEach { source ->
                check(!Regex("(?m)^import (arc\\.|org\\.lwjgl\\.|org\\.joml\\.|java\\.awt\\.|javax\\.swing\\.)").containsMatchIn(source.readText())) {
                    "Forbidden independent-layer dependency: $source"
                }
            }
        }
        fileTree("pixzel-arc/src").matching { include("**/*.kt") }.forEach { source ->
            check(!Regex("(?m)^import (org\\.lwjgl\\.|arc\\.gl\\.|arc\\.vk\\.|arc\\.window\\.glfw\\.)").containsMatchIn(source.readText())) {
                "Concrete technology leaked into adapter: $source"
            }
        }
    }
}
