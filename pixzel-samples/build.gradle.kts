plugins {
    id("pixzel.publishing")
    id("pixzel.detekt")
}

dependencies {
    api(projects.pixzelCommon)
    testImplementation(projects.pixzelTest)
}
