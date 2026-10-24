plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.detekt)
}

kotlin {
    jvmToolchain(
        rootProject.findProperty("javaVersion").toString().toIntOrNull()
            ?: error("Java version not specified")
    )
}
