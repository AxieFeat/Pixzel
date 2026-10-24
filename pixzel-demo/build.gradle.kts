plugins { application }
dependencies {
    implementation(project(":pixzel-common"))
    implementation(project(":pixzel-test"))
    implementation(project(":pixzel-samples"))
}
application { mainClass = "pixzel.demo.HeadlessDemoKt" }
sourceSets.test { kotlin.exclude("**/Arc*.kt") }
tasks.register<JavaExec>("benchmark") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "pixzel.demo.BenchmarksKt"
}
