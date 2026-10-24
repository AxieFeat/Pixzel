/**
 * Shared JUnit dependencies for modules with contract tests.
 */
plugins {
    id("pixzel.base")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    testImplementation(libs.findLibrary("junit-api").get())
    testRuntimeOnly(libs.findLibrary("junit-engine").get())
    testRuntimeOnly(libs.findLibrary("junit-launcher").get())
}
