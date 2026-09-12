package org.trustweave.observability

/**
 * Directory for evidence this suite writes for the CI gates.
 *
 * Gradle sets `trustweave.reports.dir` to the module's real build directory and declares it as a
 * task output, so the evidence is restored alongside the test results on a build-cache hit. The
 * relative fallback keeps the suite runnable outside Gradle.
 */
internal fun evidenceDir(vararg segments: String): java.nio.file.Path {
    val base = System.getProperty("trustweave.reports.dir") ?: "build/reports"
    var path = java.nio.file.Path.of(base)
    for (segment in segments) {
        path = path.resolve(segment)
    }
    return path
}
