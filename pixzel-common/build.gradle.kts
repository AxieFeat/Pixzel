plugins { `java-library` }
dependencies {
    api(project(":pixzel-core"))
    testImplementation(project(":pixzel-test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.0")
}
tasks.test { useJUnitPlatform() }
tasks.register<JavaExec>("stateChecks") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "pixzel.state.StateChecksKt"
}
tasks.register<JavaExec>("animationChecks") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "pixzel.animation.AnimationChecksKt"
}
