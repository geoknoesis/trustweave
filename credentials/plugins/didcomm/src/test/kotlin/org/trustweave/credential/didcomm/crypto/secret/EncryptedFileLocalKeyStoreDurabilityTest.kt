package org.trustweave.credential.didcomm.crypto.secret

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.secret.Secret
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.trustweave.credential.didcomm.crypto.secret.encryption.KeyEncryption
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class EncryptedFileLocalKeyStoreDurabilityTest {
    private val masterKey = ByteArray(32) { it.toByte() }

    private fun secretFor(kid: String) =
        Secret(
            kid,
            VerificationMethodType.JSON_WEB_KEY_2020,
            VerificationMaterial(VerificationMaterialFormat.JWK, """{"kty":"OKP","kid":"$kid"}"""),
        )

    @Test
    fun `concurrent stores keep every key`(
        @TempDir dir: File,
    ) = runBlocking<Unit> {
        val store = EncryptedFileLocalKeyStore(File(dir, "keys.enc"), masterKey)
        (1..40)
            .map { i -> async(Dispatchers.Default) { store.store("k$i", secretFor("k$i")) } }
            .awaitAll()
        assertEquals(40, EncryptedFileLocalKeyStore(File(dir, "keys.enc"), masterKey).list().size)
    }

    @Test
    fun `an unparsable entry fails loudly instead of being erased by the next store`(
        @TempDir dir: File,
    ) = runBlocking<Unit> {
        val file = File(dir, "keys.enc")
        val enc = KeyEncryption(masterKey).encrypt("""{"bad":{"kid":"bad"}}""".toByteArray())
        val bytes = byteArrayOf(1, 0, 0, 0, 0, 0, 0, enc.iv.size.toByte()) + enc.iv + enc.ciphertext
        file.writeBytes(bytes)
        val store = EncryptedFileLocalKeyStore(file, masterKey)
        assertFailsWith<IllegalStateException> { store.store("good", secretFor("good")) }
        assertContentEquals(bytes, file.readBytes())
    }

    @Test
    fun `a failed move leaves the previous key file intact`(
        @TempDir dir: File,
    ) = runBlocking<Unit> {
        val file = File(dir, "keys.enc")
        val store = EncryptedFileLocalKeyStore(file, masterKey)
        store.store("a", secretFor("a"))
        val before = file.readBytes()
        store.atomicMove = { _, _ -> throw java.io.IOException("simulated") }
        assertFailsWith<IllegalStateException> { store.store("b", secretFor("b")) }
        assertContentEquals(before, file.readBytes())
        assertEquals(listOf("keys.enc"), dir.list()!!.toList())
        assertNotNull(EncryptedFileLocalKeyStore(file, masterKey).get("a"))
    }
}
