package org.trustweave.core.plugin

/**
 * Minimal version / version-range evaluation for [PluginDependency.versionRange].
 *
 * Supported syntax (mirrors the documented example `">=1.0.0,<2.0.0"`):
 * - A range is a comma-separated list of constraints; **all** must hold.
 * - Each constraint is an operator (`>=`, `<=`, `>`, `<`, `=`, `==`) followed by a version.
 *   A bare version (no operator) means exact match.
 * - Versions are dot-separated numeric segments (`1.2.3`); a pre-release/build suffix
 *   after `-` or `+` is ignored for comparison. Missing segments compare as `0`
 *   (`1.2` == `1.2.0`).
 *
 * Anything that does not fit this grammar is reported as *unparseable* ([satisfies]
 * returns `null`) and enforcement is skipped by the caller — the dependency model does
 * not define richer semantics, so we deliberately do not invent any.
 *
 * @suppress This is an internal API
 */
internal object PluginVersions {

    private val CONSTRAINT = Regex("""^(>=|<=|==|=|>|<)?\s*([0-9][0-9A-Za-z.+\-]*)$""")

    /**
     * Parse a dotted numeric version into its segments, or `null` if unparseable.
     * Pre-release (`-...`) and build metadata (`+...`) suffixes are stripped.
     */
    fun parseVersion(version: String): List<Int>? {
        val core = version.trim().substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }

    /** Compare two parsed versions; missing segments are treated as `0`. */
    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /**
     * Evaluate [version] against [range].
     *
     * @return `true` if every constraint is satisfied, `false` if any constraint is
     *   violated, or `null` if the range or version cannot be parsed (callers should
     *   skip enforcement and log).
     */
    fun satisfies(version: String, range: String): Boolean? {
        val v = parseVersion(version) ?: return null
        val constraints = range.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (constraints.isEmpty()) return null
        for (constraint in constraints) {
            val match = CONSTRAINT.matchEntire(constraint) ?: return null
            val op = match.groupValues[1].ifEmpty { "=" }
            val bound = parseVersion(match.groupValues[2]) ?: return null
            val cmp = compare(v, bound)
            val ok = when (op) {
                ">=" -> cmp >= 0
                "<=" -> cmp <= 0
                ">" -> cmp > 0
                "<" -> cmp < 0
                "=", "==" -> cmp == 0
                else -> return null
            }
            if (!ok) return false
        }
        return true
    }
}
