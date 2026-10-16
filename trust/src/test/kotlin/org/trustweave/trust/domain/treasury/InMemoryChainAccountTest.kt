package org.trustweave.trust.domain.treasury

import kotlinx.coroutines.runBlocking
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InMemoryChainAccountTest {
    private val chain = "eip155:5"

    private fun account(
        kms: KeyManagementService,
        keyId: String,
    ) = InMemoryChainAccount(chain, "0xabc", KmsKeyRef("mem", keyId), InMemoryBlockchainAnchorClient(chain), kms)

    @Test
    fun `balance is reported as unknown and fee estimate comes from the anchor client`() =
        runBlocking {
            val account = account(InMemoryKeyManagementService(), "k")

            assertEquals(TokenAmount.unknown(chain), account.balance())
            assertEquals(TokenAmount.unknown(chain), account.estimateFee(OperationDescriptor("anchor", chain)))
        }

    @Test
    fun `signing uses the referenced KMS key and keeps the chain id`() =
        runBlocking {
            val kms = InMemoryKeyManagementService()
            val handle = (kms.generateKey(Algorithm.Ed25519) as GenerateKeyResult.Success).keyHandle
            val account = account(kms, handle.id.value)
            val bytes = "tx-bytes".encodeToByteArray()

            val signed = account.sign(UnsignedTx(chain, bytes))

            assertEquals(chain, signed.chainId)
            val expected = (kms.sign(handle.id, bytes) as SignResult.Success).signature
            assertContentEquals(expected, signed.bytes)
            assertEquals(64, signed.bytes.size, "Ed25519 signatures are 64 bytes")
        }

    @Test
    fun `a transaction for another chain is refused before any signing`() =
        runBlocking {
            val account = account(InMemoryKeyManagementService(), "k")

            val error = assertFailsWith<IllegalArgumentException> { account.sign(UnsignedTx("eip155:1", byteArrayOf(1))) }

            assertTrue(error.message!!.contains("eip155:1") && error.message!!.contains(chain), error.message)
        }

    @Test
    fun `a missing key surfaces as a signing failure naming the key`() =
        runBlocking {
            val account = account(InMemoryKeyManagementService(), "absent-key")

            val error = assertFailsWith<IllegalStateException> { account.sign(UnsignedTx(chain, byteArrayOf(1))) }

            assertEquals("Signing failed: key not found: absent-key", error.message)
        }

    @Test
    fun `other KMS failures are mapped to signing failures with the reason`() =
        runBlocking {
            fun kmsReturning(result: SignResult) =
                object : KeyManagementService by InMemoryKeyManagementService() {
                    override suspend fun sign(
                        keyId: KeyId,
                        data: ByteArray,
                        algorithm: Algorithm?,
                    ) = result
                }

            val unsupported =
                assertFailsWith<IllegalStateException> {
                    account(kmsReturning(SignResult.Failure.UnsupportedAlgorithm(KeyId("k"), null, Algorithm.Ed25519, "wrong curve")), "k")
                        .sign(UnsignedTx(chain, byteArrayOf(1)))
                }
            assertEquals("Signing failed: unsupported algorithm: wrong curve", unsupported.message)

            val mismatch =
                assertFailsWith<IllegalStateException> {
                    account(kmsReturning(SignResult.Failure.UnsupportedAlgorithm(KeyId("k"), null, Algorithm.Ed25519)), "k")
                        .sign(UnsignedTx(chain, byteArrayOf(1)))
                }
            assertEquals("Signing failed: unsupported algorithm: algorithm mismatch", mismatch.message)

            val error =
                assertFailsWith<IllegalStateException> {
                    account(kmsReturning(SignResult.Failure.Error(KeyId("k"), "hsm busy")), "k").sign(UnsignedTx(chain, byteArrayOf(1)))
                }
            assertEquals("Signing failed: hsm busy", error.message)
        }

    @Test
    fun `transaction value objects compare by byte content`() {
        assertEquals(UnsignedTx(chain, byteArrayOf(1, 2), "sep"), UnsignedTx(chain, byteArrayOf(1, 2), "sep"))
        assertEquals(UnsignedTx(chain, byteArrayOf(1, 2)).hashCode(), UnsignedTx(chain, byteArrayOf(1, 2)).hashCode())
        assertNotEquals(UnsignedTx(chain, byteArrayOf(1, 2)), UnsignedTx(chain, byteArrayOf(1, 3)))
        assertNotEquals(UnsignedTx(chain, byteArrayOf(1)), UnsignedTx(chain, byteArrayOf(1), "other"))
        assertNotEquals(UnsignedTx(chain, byteArrayOf(1)), UnsignedTx("other", byteArrayOf(1)))
        assertFalse(UnsignedTx(chain, byteArrayOf(1)).equals("not a tx"))

        assertEquals(SignedTx(chain, byteArrayOf(9)), SignedTx(chain, byteArrayOf(9)))
        assertEquals(SignedTx(chain, byteArrayOf(9)).hashCode(), SignedTx(chain, byteArrayOf(9)).hashCode())
        assertNotEquals(SignedTx(chain, byteArrayOf(9)), SignedTx(chain, byteArrayOf(8)))
        assertNotEquals(SignedTx(chain, byteArrayOf(9)), SignedTx("x", byteArrayOf(9)))
        assertFalse(SignedTx(chain, byteArrayOf(9)).equals(null))
    }
}
