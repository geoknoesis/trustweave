package org.trustweave.did.sidetree

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Contract tests that every [SidetreeKeyStore] implementation MUST satisfy.
 *
 * Both implementations ([InMemorySidetreeKeyStore], [FileSidetreeKeyStore]) are
 * exercised here so we can be confident a plugin can swap one for the other
 * without behavioural change.
 */
class SidetreeKeyStoreContractTest {

    private fun sample(seed: String) = SidetreeKeyPair(
        updatePrivateJwk = mapOf("kty" to "EC", "crv" to "P-256", "d" to "up-$seed", "x" to "ux-$seed", "y" to "uy-$seed"),
        updatePublicJwk = mapOf("kty" to "EC", "crv" to "P-256", "x" to "ux-$seed", "y" to "uy-$seed"),
        recoveryPrivateJwk = mapOf("kty" to "EC", "crv" to "P-256", "d" to "rp-$seed", "x" to "rx-$seed", "y" to "ry-$seed"),
        recoveryPublicJwk = mapOf("kty" to "EC", "crv" to "P-256", "x" to "rx-$seed", "y" to "ry-$seed"),
    )

    private fun runContract(store: SidetreeKeyStore) = runBlocking {
        // get on an empty store returns null
        assertNull(store.get("EiAbsent"))

        // put + get round-trips the full keypair
        val keys = sample("alpha")
        store.put("EiAlpha", keys)
        assertEquals(keys, store.get("EiAlpha"))

        // put replaces an existing entry
        val replacement = sample("alpha-2")
        store.put("EiAlpha", replacement)
        assertEquals(replacement, store.get("EiAlpha"))

        // multiple DIDs are isolated
        store.put("EiBeta", sample("beta"))
        assertEquals(replacement, store.get("EiAlpha"))
        assertEquals(sample("beta"), store.get("EiBeta"))

        // remove deletes only the targeted entry
        store.remove("EiAlpha")
        assertNull(store.get("EiAlpha"))
        assertEquals(sample("beta"), store.get("EiBeta"))

        // remove on a missing key is a no-op
        store.remove("EiAlpha")
    }

    @Test
    fun `in-memory store satisfies the contract`() {
        runContract(InMemorySidetreeKeyStore())
    }

    @Test
    fun `file-backed store satisfies the contract`(@TempDir tmp: Path) {
        runContract(FileSidetreeKeyStore(tmp.resolve("keys")))
    }

    @Test
    fun `file-backed store persists across instances pointing at the same directory`(@TempDir tmp: Path) = runBlocking {
        val dir = tmp.resolve("persisted")
        val first = FileSidetreeKeyStore(dir)
        first.put("EiAcrossInstances", sample("persistent"))

        val second = FileSidetreeKeyStore(dir)
        assertEquals(sample("persistent"), second.get("EiAcrossInstances"))
    }

    @Test
    fun `file-backed store survives a clear() and starts clean`(@TempDir tmp: Path) = runBlocking {
        val store = FileSidetreeKeyStore(tmp.resolve("clearable"))
        store.put("EiOne", sample("one"))
        store.put("EiTwo", sample("two"))
        store.clear()
        assertNull(store.get("EiOne"))
        assertNull(store.get("EiTwo"))
    }
}
