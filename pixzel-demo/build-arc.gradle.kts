plugins { application }
dependencies {
    implementation(project(":pixzel-common"))
    implementation(project(":pixzel-test"))
    implementation(project(":pixzel-samples"))
    implementation(project(":pixzel-arc"))
    implementation("arc.engine:arc-opengl:0.0.0")
    implementation("arc.engine:arc-font-native:0.0.0")
    implementation("arc.engine:arc-input-glfw:0.0.0")
    // The concrete desktop demo reads its own framebuffer for smoke-test captures.
    implementation("org.lwjgl:lwjgl:3.4.2")
    implementation("org.lwjgl:lwjgl-opengl:3.4.2")
    testImplementation("org.lwjgl:lwjgl:3.4.2")
    testImplementation("org.lwjgl:lwjgl-glfw:3.4.2")
    testImplementation("org.lwjgl:lwjgl-opengl:3.4.2")
}
application { mainClass = "pixzel.demo.ArcDemoKt" }
sourceSets.main { kotlin.srcDir("src/arc/kotlin") }
// Native fixture entry points run through the explicit JavaExec tasks below, not JUnit discovery.
tasks.test { failOnNoDiscoveredTests = false }
tasks.register<JavaExec>("graphicsChecks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "pixzel.demo.ArcGraphicsChecks"
}
tasks.register<JavaExec>("inputChecks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "pixzel.demo.ArcInputChecks"
}
tasks.register<JavaExec>("benchmark") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "pixzel.demo.BenchmarksKt"
}
