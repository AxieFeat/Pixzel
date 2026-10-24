pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "pixzel"
include("pixzel-core", "pixzel-common", "pixzel-test", "pixzel-demo", "pixzel-samples")
val arcPath = providers.gradleProperty("arcPath").orNull
if (arcPath != null) {
    include("pixzel-arc")
    includeBuild(arcPath) {
        dependencySubstitution {
            substitute(module("arc.engine:arc-core")).using(project(":arc-core"))
            substitute(module("arc.engine:arc-input-core")).using(project(":arc-extensions:arc-input:arc-input-core"))
            substitute(module("arc.engine:arc-input-glfw")).using(project(":arc-extensions:arc-input:arc-input-glfw"))
            substitute(module("arc.engine:arc-font-core")).using(project(":arc-extensions:arc-font:arc-font-core"))
            substitute(module("arc.engine:arc-opengl")).using(project(":arc-opengl"))
            substitute(module("arc.engine:arc-font-native")).using(project(":arc-extensions:arc-font:arc-font-native"))
        }
    }
    project(":pixzel-demo").buildFileName = "build-arc.gradle.kts"
}
