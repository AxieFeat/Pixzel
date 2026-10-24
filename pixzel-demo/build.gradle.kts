plugins {
    application
    id("pixzel.testing")
    id("pixzel.detekt")
}
dependencies {
    implementation(projects.pixzelCommon)
    implementation(projects.pixzelTest)
    implementation(projects.pixzelSamples)
    implementation(projects.pixzelArc)

    implementation(libs.arc.opengl)
    implementation(libs.arc.font.native)
    implementation(libs.arc.input.glfw)

    // The desktop showcase reads its own framebuffer for smoke-test captures.
    lwjgl(libs.lwjgl.lib)
    lwjgl(libs.lwjgl.opengl)
    testImplementation(libs.lwjgl.glfw)
}
application {
    mainClass = "pixzel.demo.ArcDemoKt"
}

// Native fixtures are executed by the JavaExec verification tasks, rather than JUnit discovery.
tasks.test {
    failOnNoDiscoveredTests = false
}

tasks.register<JavaExec>("preview") {
    group = "application"
    description = "Writes the independent software preview without opening a native window."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "pixzel.demo.HeadlessDemoKt"
}

tasks.register<JavaExec>("graphicsChecks") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "pixzel.demo.ArcGraphicsChecks"
}

tasks.register<JavaExec>("inputChecks") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "pixzel.demo.ArcInputChecks"
}
tasks.register<JavaExec>("benchmark") {
    group = "application"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "pixzel.demo.BenchmarksKt"
}
