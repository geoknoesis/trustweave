package org.trustweave.credential.didcomm

import org.trustweave.credential.didcomm.crypto.DidCommCrypto
import org.trustweave.credential.didcomm.crypto.DidCommCryptoAdapter
import org.trustweave.credential.didcomm.models.DidCommEnvelope
import org.trustweave.credential.didcomm.models.DidCommRecipient
import org.trustweave.credential.didcomm.models.DidCommRecipientHeader
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests that the non-didcomm-java crypto path is fail-closed: without an ECDH-capable provider
 * (SecretResolver with real key material) no ciphertext is ever produced and no envelope is ever
 * decrypted. The former placeholder crypto used a constant shared secret and has been removed.
 */
class CryptoImplementationTest {

    private val kms = InMemoryKeyManagementService()
    private val resolveDid: suspend (String) -> DidDocument? = { didStr ->
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

    private val message = buildJsonObject {
        put("id", "test-123")
        put("type", "test")
        put("body", buildJsonObject { put("content", "Hello") })
    }

    private val dummyEnvelope = DidCommEnvelope(
        protected = "e30",
        recipients = listOf(
            DidCommRecipient(
                header = DidCommRecipientHeader(kid = "did:key:bob#key-1"),
                encrypted_key = "AAAA",
            ),
        ),
        iv = "AAAA",
        ciphertext = "AAAA",
        tag = "AAAA",
    )

    @Test
    fun failClosedCryptoNeverEncrypts() = runBlocking {
        val crypto = DidCommCrypto(kms, resolveDid)

        val ex = assertFailsWith<UnsupportedOperationException> {
            crypto.encrypt(
                message = message,
                fromDid = "did:key:alice",
                fromKeyId = "did:key:alice#key-1",
                toDid = "did:key:bob",
                toKeyId = "did:key:bob#key-1",
            )
        }
        assertTrue(
            ex.message.orEmpty().contains("ECDH-capable crypto provider"),
            "Expected fail-closed message, got: ${ex.message}",
        )
    }

    @Test
    fun failClosedCryptoNeverDecrypts() = runBlocking {
        val crypto = DidCommCrypto(kms, resolveDid)

        assertFailsWith<UnsupportedOperationException> {
            crypto.decrypt(
                envelope = dummyEnvelope,
                recipientDid = "did:key:bob",
                recipientKeyId = "did:key:bob#key-1",
                senderDid = "did:key:alice",
            )
        }
        Unit
    }

    @Test
    fun adapterWithoutDidcommJavaFailsClosed() = runBlocking {
        val adapter = DidCommCryptoAdapter(kms, resolveDid, useDidcommJava = false)

        assertFailsWith<UnsupportedOperationException> {
            adapter.encrypt(
                message = message,
                fromDid = "did:key:alice",
                fromKeyId = "did:key:alice#key-1",
                toDid = "did:key:bob",
                toKeyId = "did:key:bob#key-1",
            )
        }
        assertFailsWith<UnsupportedOperationException> {
            adapter.decrypt(
                envelope = dummyEnvelope,
                recipientDid = "did:key:bob",
                recipientKeyId = "did:key:bob#key-1",
                senderDid = "did:key:alice",
            )
        }
        Unit
    }

    @Test
    fun adapterRequiresSecretResolverWhenDidcommJavaEnabled() = runBlocking {
        val adapter = DidCommCryptoAdapter(kms, resolveDid, useDidcommJava = true, secretResolver = null)
        assertFailsWith<IllegalStateException> {
            adapter.encrypt(
                message = message,
                fromDid = "did:key:alice",
                fromKeyId = "did:key:alice#key-1",
                toDid = "did:key:bob",
                toKeyId = "did:key:bob#key-1",
            )
        }
        Unit
    }
}
