package org.trustweave.credential.didcomm.crypto.secret

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import kotlinx.coroutines.runBlocking
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.secret.Secret
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [EncryptedFileLocalKeyStore] is the store this module offers for keeping DIDComm secrets at rest.
 *
 * It could not do that: both halves of its persistence threw, on the grounds that the
 * didcomm-java `Secret` API was unclear. The store failed closed rather than losing keys, but a
 * caller who chose it over the in-memory store got something that cannot hold a key at all — so the
 * round trip is what has to be proven, not the absence of a crash.
 */
class EncryptedFileLocalKeyStoreRoundTripTest {
    private val masterKey = ByteArray(32) { it.toByte() }

    private fun secretFor(kid: String): Secret {
        val keyPair = OctetKeyPairGenerator(Curve.X25519).keyID(kid).generate()
        return Secret(
            kid,
            VerificationMethodType.JSON_WEB_KEY_2020,
            VerificationMaterial(VerificationMaterialFormat.JWK, keyPair.toJSONString()),
        )
    }

    private fun storeIn(dir: File) = EncryptedFileLocalKeyStore(File(dir, "keys.enc"), masterKey)

    @Test
    fun `a stored secret survives a reload from disk`(
        @TempDir dir: File,
    ) = runBlocking<Unit> {
        val kid = "did:example:alice#key-1"
        val original = secretFor(kid)

        storeIn(dir).store(kid, original)

        // A fresh instance, so the answer comes from the file rather than any in-process state.
        val reloaded = storeIn(dir).get(kid)

        assertNotNull(reloaded, "The stored secret must come back")
        assertEquals(original.kid, reloaded.kid)
        assertEquals(original.type, reloaded.type)
        assertEquals(original.verificationMaterial.format, reloaded.verificationMaterial.format)
        assertEquals(original.verificationMaterial.value, reloaded.verificationMaterial.value)
    }

    @Test
    fun `several secrets round-trip and can be listed and deleted`(
        @TempDir dir: File,
    ) = runBlocking<Unit> {
        val first = "did:example:alice#key-1"
        val second = "did:example:bob#key-1"

        storeIn(dir).store(first, secretFor(first))
        storeIn(dir).store(second, secretFor(second))

        assertEquals(setOf(first, second), storeIn(dir).list().toSet())

        assertTrue(storeIn(dir).delete(first))
        assertNull(storeIn(dir).get(first))
        assertNotNull(storeIn(dir).get(second), "Deleting one key must not disturb the other")
    }

    @Test
    fun `the file on disk does not contain the key material in the clear`(
        @TempDir dir: File,
    ) = runBlocking<Unit> {
        val kid = "did:example:alice#key-1"
        val secret = secretFor(kid)

        storeIn(dir).store(kid, secret)

        val onDisk = File(dir, "keys.enc").readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(kid !in onDisk, "The key id must not appear in the encrypted file")
        assertTrue(
            secret.verificationMaterial.value !in onDisk,
            "The private JWK must not appear in the encrypted file",
        )
    }
}
