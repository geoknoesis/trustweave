package org.trustweave.ethrdid

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies the real Keccak-256 Ethereum address derivation in [EthrDidMethod] against a
 * well-known secp256k1 test vector, and that non-secp256k1 keys are rejected.
 *
 * Vector: the secp256k1 generator point (private key = 1):
 *   x = 79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798
 *   y = 483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8
 *   address (EIP-55) = 0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf
 */
class EthrDidMethodAddressDerivationTest {

    companion object {
        private const val X_HEX = "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"
        private const val Y_HEX = "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8"
        private const val EXPECTED_ADDRESS = "0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf"

        private fun hexToBytes(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i ->
                ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
            }

        private fun b64url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Anchor client stub: anchoring always fails, so documents are stored locally only. */
    private class NoopAnchorClient : BlockchainAnchorClient {
        override suspend fun writePayload(payload: JsonElement, mediaType: String): AnchorResult =
            throw TrustWeaveException.Unknown(message = "no chain in tests")

        override suspend fun readPayload(ref: AnchorRef): AnchorResult =
            throw TrustWeaveException.NotFound(resource = "anchor ${ref.txHash}")
    }

    /** KMS stub returning a fixed, well-known secp256k1 public key. */
    private class FixedSecp256k1Kms(
        private val jwk: Map<String, Any?>? = mapOf(
            "kty" to "EC",
            "crv" to "secp256k1",
            "x" to b64url(hexToBytes(X_HEX)),
            "y" to b64url(hexToBytes(Y_HEX))
        ),
        private val algorithmName: String = "secp256k1"
    ) : KeyManagementService {
        val handle = KeyHandle(KeyId("test-key-1"), algorithmName, publicKeyJwk = jwk)

        override suspend fun getSupportedAlgorithms(): Set<Algorithm> =
            setOf(Algorithm.Secp256k1, Algorithm.Ed25519)

        override suspend fun generateKey(
            algorithm: Algorithm,
            options: Map<String, Any?>
        ): GenerateKeyResult = GenerateKeyResult.Success(handle)

        override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult =
            if (keyId == handle.id) GetPublicKeyResult.Success(handle)
            else GetPublicKeyResult.Failure.KeyNotFound(keyId)

        override suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm?): SignResult =
            SignResult.Failure.Error(keyId, "signing not needed in this test")

        override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult = DeleteKeyResult.NotFound
    }

    private fun newMethod(kms: KeyManagementService): EthrDidMethod =
        EthrDidMethod(
            kms = kms,
            anchorClient = NoopAnchorClient(),
            config = EthrDidConfig.sepolia("http://localhost:8545")
        )

    @Test
    fun `derives the real Keccak-256 Ethereum address from the secp256k1 public key`() = runBlocking {
        val method = newMethod(FixedSecp256k1Kms())

        val document = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))

        assertEquals("did:ethr:sepolia:$EXPECTED_ADDRESS", document.id.value)
    }

    @Test
    fun `rejects Ed25519 keys - Ethereum addresses require secp256k1`(): Unit = runBlocking {
        val method = newMethod(FixedSecp256k1Kms())

        val error = assertFailsWith<IllegalArgumentException> {
            method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        }
        assertTrue(
            error.message!!.contains("secp256k1"),
            "error should explain the secp256k1 requirement, got: ${error.message}"
        )
    }

    @Test
    fun `fails honestly when the KMS exposes no public key JWK - never fabricates an address`(): Unit =
        runBlocking {
            val method = newMethod(FixedSecp256k1Kms(jwk = null))

            assertFailsWith<TrustWeaveException.Unknown> {
                method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))
            }
            Unit
        }

    @Test
    fun `rejects a key handle whose algorithm is not secp256k1`(): Unit = runBlocking {
        // KMS misbehaves and hands back an Ed25519 key even though secp256k1 was requested.
        val method = newMethod(FixedSecp256k1Kms(algorithmName = "Ed25519"))

        assertFailsWith<IllegalArgumentException> {
            method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.SECP256K1))
        }
        Unit
    }
}
