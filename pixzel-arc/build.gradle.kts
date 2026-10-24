plugins { `java-library` }
dependencies {
    api(project(":pixzel-core"))
    api("arc.engine:arc-core:0.0.0")
    api("arc.engine:arc-input-core:0.0.0")
    api("arc.engine:arc-font-core:0.0.0")
    testImplementation("arc.engine:arc-font-native:0.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.0")
}
tasks.test { useJUnitPlatform() }
