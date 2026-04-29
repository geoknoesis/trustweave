package org.trustweave.credential.didcomm

import org.trustweave.credential.didcomm.crypto.DidCommCrypto
import org.trustweave.credential.didcomm.crypto.DidCommCryptoAdapter
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests placeholder vs didcomm-java adapter wiring.
 */
class CryptoImplementationTest {

    @Test
    fun testPlaceholderCryptoReturnsDummyData() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { didStr ->
            val did = Did(didStr)
            val vmId = VerificationMethodId.parse("$didStr#key-1")
            DidDocument(
                id = did,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = vmId,
                        type = "Ed25519VerificationKey2020",
                        controller = did,
                        publicKeyJwk = mapOf(
                            "kty" to "OKP",
                            "crv" to "Ed25519",
                            "x" to "test-key",
                        ),
                    ),
                ),
                keyAgreement = listOf(vmId),
            )
        }

        val crypto = DidCommCrypto(kms, resolveDid)

        val message = buildJsonObject {
            put("id", "test-123")
            put("type", "test")
            put("body", buildJsonObject {
                put("content", "Hello")
            })
        }

        val envelope = crypto.encrypt(
            message = message,
            fromDid = "did:key:alice",
            fromKeyId = "did:key:alice#key-1",
            toDid = "did:key:bob",
            toKeyId = "did:key:bob#key-1",
        )

        assertNotNull(envelope.protected)
        assertNotNull(envelope.recipients)
        assertNotNull(envelope.ciphertext)
        assertNotNull(envelope.iv)
        assertNotNull(envelope.tag)
    }

    @Test
    fun testAdapterWithPlaceholderCrypto() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { didStr ->
            val did = Did(didStr)
            val vmId = VerificationMethodId.parse("$didStr#key-1")
            DidDocument(
                id = did,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = vmId,
                        type = "Ed25519VerificationKey2020",
                        controller = did,
                        publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519", "x" to "test"),
                    ),
                ),
                keyAgreement = listOf(vmId),
            )
        }

        val adapter = DidCommCryptoAdapter(kms, resolveDid, useDidcommJava = false)

        assertTrue(!adapter.useDidcommJava, "Should use placeholder crypto")

        val message = buildJsonObject {
            put("id", "test-123")
            put("type", "test")
            put("body", buildJsonObject { })
        }

        val envelope = adapter.encrypt(
            message = message,
            fromDid = "did:key:alice",
            fromKeyId = "did:key:alice#key-1",
            toDid = "did:key:bob",
            toKeyId = "did:key:bob#key-1",
        )

        assertNotNull(envelope)
    }

    @Test
    fun testAdapterRequiresSecretResolverWhenDidcommJavaEnabled() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { null }
        val adapter = DidCommCryptoAdapter(kms, resolveDid, useDidcommJava = true, secretResolver = null)
        assertFailsWith<IllegalStateException> {
            val message = buildJsonObject {
                put("id", "test-123")
                put("type", "test")
                put("body", buildJsonObject { })
            }
            adapter.encrypt(
                message = message,
                fromDid = "did:key:alice",
                fromKeyId = "did:key:alice#key-1",
                toDid = "did:key:bob",
                toKeyId = "did:key:bob#key-1",
            )
        }
    }
}
