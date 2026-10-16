package org.trustweave.did.sidetree

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertEquals

class FileSidetreeKeyStorePermissionsTest {
    private fun sample() =
        SidetreeKeyPair(
            updatePrivateJwk = mapOf("kty" to "EC", "d" to "secret-up"),
            updatePublicJwk = mapOf("kty" to "EC", "x" to "ux"),
            recoveryPrivateJwk = mapOf("kty" to "EC", "d" to "secret-rec"),
            recoveryPublicJwk = mapOf("kty" to "EC", "x" to "rx"),
        )

    private fun posix() = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    @Test
    fun `stored key files are owner-only`(
        @TempDir dir: Path,
    ) = runBlocking<Unit> {
        assumeTrue(posix())
        FileSidetreeKeyStore(dir).put("EiAbc", sample())
        val perms = Files.getPosixFilePermissions(dir.resolve("EiAbc.json"))
        assertEquals("rw-------", PosixFilePermissions.toString(perms))
    }

    @Test
    fun `a pre-planted predictable temp name is never written through`(
        @TempDir dir: Path,
    ) = runBlocking<Unit> {
        assumeTrue(posix())
        val victim = Files.writeString(dir.resolve("victim.txt"), "untouched")
        val store = FileSidetreeKeyStore(dir.resolve("keys"))
        Files.createSymbolicLink(dir.resolve("keys").resolve("EiAbc.json.tmp"), victim)
        store.put("EiAbc", sample())
        assertEquals("untouched", Files.readString(victim))
    }
}
