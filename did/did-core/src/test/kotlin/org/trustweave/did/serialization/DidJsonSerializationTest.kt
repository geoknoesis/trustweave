package org.trustweave.did.serialization

import kotlinx.serialization.SerializationException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DidJsonSerializationTest {
    private val json = DidJsonSerialization.json()
    private val did = Did("did:example:123")

    private fun document(jwk: Map<String, Any?>?) =
        DidDocument(
            id = did,
            verificationMethod =
                listOf(
                    VerificationMethod(
                        id = VerificationMethodId.parse("#k", did),
                        type = "JsonWebKey2020",
                        controller = did,
                        publicKeyJwk = jwk,
                    ),
                ),
        )

    @Test
    fun `a document with a public JWK round-trips`() {
        val jwk = mapOf("kty" to "OKP", "crv" to "Ed25519", "x" to "abc", "key_ops" to listOf("verify"))
        val text = json.encodeToString(DidDocument.serializer(), document(jwk))
        assertTrue("\"crv\":\"Ed25519\"" in text, text)
        assertEquals(
            jwk,
            json
                .decodeFromString(DidDocument.serializer(), text)
                .verificationMethod
                .single()
                .publicKeyJwk,
        )
    }

    @Test
    fun `without the module the same document cannot be encoded`() {
        assertFailsWith<SerializationException> {
            kotlinx.serialization.json.Json
                .encodeToString(DidDocument.serializer(), document(mapOf("kty" to "OKP")))
        }
    }

    @Test
    fun `values that are not JSON are refused instead of stringified`() {
        assertFailsWith<SerializationException> {
            json.encodeToString(DidDocument.serializer(), document(mapOf("x" to Any())))
        }
    }

    @Test
    fun `a null-valued member survives`() {
        val text = json.encodeToString(DidDocument.serializer(), document(mapOf("kty" to "EC", "d" to null)))
        assertTrue(
            json
                .decodeFromString(DidDocument.serializer(), text)
                .verificationMethod
                .single()
                .publicKeyJwk!!
                .containsKey("d"),
        )
    }
}
