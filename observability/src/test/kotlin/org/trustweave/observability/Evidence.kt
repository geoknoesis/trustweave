package org.trustweave.observability

import java.nio.file.Path

/**
 * Directory for evidence this suite writes for the CI gates.
 *
 * Gradle sets `trustweave.reports.dir` to the module's real build directory and declares it as a
 * task output, so the evidence is restored alongside the test results on a build-cache hit. The
 * relative fallback keeps the suite runnable outside Gradle.
 */
internal fun evidenceDir(vararg segments: String): Path {
    var path = Path.of(System.getProperty("trustweave.reports.dir") ?: "build/qualification")
    for (segment in segments) {
        path = path.resolve(segment)
    }
    return path
}
