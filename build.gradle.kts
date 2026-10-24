plugins {
    id("pixzel.base")
    id("pixzel.verification")
}

allprojects {
    apply(plugin = "pixzel.base")

    group = rootProject.findProperty("group") ?: error("Project group not specified")
    version = rootProject.findProperty("version") ?: error("Project version not specified")
    description = rootProject.findProperty("description") as? String ?: ""
}
