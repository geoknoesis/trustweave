package org.trustweave.anchor

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards [ChainId.Eip155] against drifting from the chains actually shipped as
 * anchor plugins: scans every `eip155:<number>` literal in the plugins' main
 * sources and asserts the ChainId table knows each of them.
 */
class ChainIdPluginCoverageTest {

    private val eip155Pattern = Regex("eip155:(\\d+)")

    @Test
    fun `Eip155 table covers every eip155 chain id referenced by shipped plugins`() {
        val pluginsDir = findPluginsDir()
        assumeTrue(pluginsDir != null, "anchors/plugins source directory not found - skipping")

        val referencedChainNumbers = Files.walk(pluginsDir!!).use { stream ->
            stream.asSequence()
                .filter { it.extension == "kt" }
                // Only shipped sources define the supported chains; tests may
                // reference deliberately-unsupported ids.
                .filter { path -> path.map { it.toString() }.containsAll(listOf("src", "main")) }
                .flatMap { path ->
                    eip155Pattern.findAll(path.readText()).map { it.groupValues[1].toInt() }
                }
                .toSortedSet()
        }

        assertTrue(referencedChainNumbers.isNotEmpty(), "expected plugins to reference eip155 chain ids")

        val missing = referencedChainNumbers.filter { ChainId.Eip155.fromChainNumber(it) == null }
        assertTrue(
            missing.isEmpty(),
            "ChainId.Eip155 is missing chain numbers referenced by shipped plugins: $missing"
        )
    }

    @Test
    fun `Eip155 table itself parses as valid chain ids`() {
        for (entry in ChainId.Eip155.all) {
            assertNotNull(ChainId.parse(entry.toString()), "ChainId.parse must handle $entry")
        }
    }

    /**
     * Locates `anchors/plugins` by walking up from the test working directory,
     * so the test works whether Gradle runs it from the module dir or repo root.
     */
    private fun findPluginsDir(): Path? {
        var dir: Path? = Paths.get("").toAbsolutePath()
        while (dir != null) {
            val fromRepoRoot = dir.resolve("anchors").resolve("plugins")
            if (Files.isDirectory(fromRepoRoot)) return fromRepoRoot
            val sibling = dir.resolve("plugins")
            if (dir.fileName?.toString() == "anchors" && Files.isDirectory(sibling)) return sibling
            dir = dir.parent
        }
        return null
    }
}
