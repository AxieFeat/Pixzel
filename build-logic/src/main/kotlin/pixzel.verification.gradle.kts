/**
 * Checks source boundaries and the project's multiline KDoc convention.
 */
val verifyBoundaries = tasks.register("verifyBoundaries") {
    group = "verification"
    description = "Checks independent layers and the neutral Arc adapter for forbidden imports."

    doLast {
        listOf("pixzel-core", "pixzel-common").forEach { module ->
            fileTree("$module/src").matching { include("**/*.kt") }.forEach { source ->
                check(
                    !Regex("(?m)^import (arc\\.|org\\.lwjgl\\.|org\\.joml\\.|java\\.awt\\.|javax\\.swing\\.)")
                        .containsMatchIn(source.readText())
                ) {
                    "Forbidden independent-layer dependency: $source"
                }
            }
        }

        fileTree("pixzel-arc/src").matching { include("**/*.kt") }.forEach { source ->
            check(
                !Regex("(?m)^import (org\\.lwjgl\\.|arc\\.gl\\.|arc\\.vk\\.|arc\\.window\\.glfw\\.)")
                    .containsMatchIn(source.readText())
            ) {
                "Concrete technology leaked into adapter: $source"
            }
        }
    }
}

val verifyCodeStyle = tasks.register("verifyCodeStyle") {
    group = "verification"
    description = "Checks that live Kotlin sources and build scripts use multiline KDoc."

    doLast {
        val singleLineKDoc = Regex("(?m)^[\\t ]*/\\*\\*[^\\r\\n]*\\*/")
        val sources = fileTree(rootDir) {
            include("*.gradle.kts", "build-logic/**/*.kt", "build-logic/**/*.kts", "pixzel-*/**/*.kt", "pixzel-*/*.gradle.kts")
            exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        }
        sources.forEach { source ->
            check(!singleLineKDoc.containsMatchIn(source.readText())) {
                "Single-line KDoc must be expanded: $source"
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyBoundaries, verifyCodeStyle)
}
