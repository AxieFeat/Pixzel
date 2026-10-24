plugins {
    id("pixzel.publishing")
    id("pixzel.detekt")
}

dependencies {
    api(projects.pixzelCore)
    api(libs.arc.core)
    api(libs.arc.input.core)
    api(libs.arc.font.core)

    testImplementation(libs.arc.font.native)
}
