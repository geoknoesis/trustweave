package org.trustweave.keydid

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyDidMethodKeyValidationTest {
    private val method = KeyDidMethod(InMemoryKeyManagementService())

    private val p256Prefix = byteArrayOf(0x80.toByte(), 0x24)
    private val k1Prefix = byteArrayOf(0xe7.toByte(), 0x01)

    private fun didOf(bytes: ByteArray) = Did("did:key:z" + bytes.encodeBase58())

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val p256Gx = hex("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296")

    private fun assertInvalidDid(did: Did) =
        runBlocking {
            val result = method.resolveDid(did)
            assertTrue(result is DidResolutionResult.Failure.InvalidFormat, "expected invalidDid, got $result")
        }

    @Test
    fun `x not on the curve fails resolution`() {
        var x = 1
        while (true) {
            val candidate = ByteArray(32).also { it[31] = x.toByte() }
            val bad = runCatching { DidMethodUtils.decompressEcPublicKey("SECP256K1", byteArrayOf(0x02) + candidate) }.isFailure
            if (bad) {
                assertInvalidDid(didOf(k1Prefix + byteArrayOf(0x02) + candidate))
                return
            }
            x++
        }
    }

    @Test
    fun `wrong EC length fails resolution`() {
        assertInvalidDid(didOf(p256Prefix + byteArrayOf(0x02) + p256Gx.copyOf(20)))
        assertInvalidDid(didOf(p256Prefix + ByteArray(5)))
    }

    @Test
    fun `uncompressed point that is not on the curve fails resolution`() {
        assertInvalidDid(didOf(p256Prefix + byteArrayOf(0x04) + p256Gx + ByteArray(32) { 1 }))
    }

    @Test
    fun `a valid compressed P-256 key still resolves`() =
        runBlocking {
            val result = method.resolveDid(didOf(p256Prefix + byteArrayOf(0x03) + p256Gx))
            assertTrue(result is DidResolutionResult.Success, "$result")
        }

    @Test
    fun `parseMulticodecKey returns null for an off-curve EC key`() {
        assertNull(DidMethodUtils.parseMulticodecKey(p256Prefix + byteArrayOf(0x04) + p256Gx + ByteArray(32) { 1 }))
    }

    @Test
    fun `resolving a did-key does not retain the derived document`() =
        runBlocking {
            val result = method.resolveDid(didOf(p256Prefix + byteArrayOf(0x03) + p256Gx))
            assertTrue(result is DidResolutionResult.Success)
            assertEquals(0, cachedCount(method))
        }

    @Test
    fun `resolving many distinct did-keys keeps the cache empty`() =
        runBlocking {
            repeat(50) { i ->
                val key = ByteArray(32) { (it + i).toByte() }
                method.resolveDid(didOf(byteArrayOf(0xed.toByte(), 0x01) + key))
            }
            assertEquals(0, cachedCount(method))
        }

    private fun cachedCount(m: AbstractDidMethod): Int {
        val f = AbstractDidMethod::class.java.getDeclaredField("documents").apply { isAccessible = true }
        return (f.get(m) as Map<*, *>).size
    }
}
