plugins {
    id("pixzel.publishing")
    id("pixzel.detekt")
}

dependencies {
    api(projects.pixzelCore)
    testImplementation(projects.pixzelTest)
}
tasks.register<JavaExec>("stateChecks") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "pixzel.state.StateChecksKt"
}
tasks.register<JavaExec>("animationChecks") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "pixzel.animation.AnimationChecksKt"
}
