package org.trustweave.trust.services

import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** getKeyId must return the issuer's assertion key, not whatever key happens to be listed first. */
class SigningKeySelectionTest {
    private val did = Did("did:example:issuer")
    private val agreement = VerificationMethodId.parse("${did.value}#x25519", did)
    private val auth = VerificationMethodId.parse("${did.value}#auth", did)
    private val assertion = VerificationMethodId.parse("${did.value}#assert", did)

    private fun vm(
        id: VerificationMethodId,
        type: String = "JsonWebKey2020",
    ) = VerificationMethod(id = id, type = type, controller = did)

    @Test
    fun `prefers the assertionMethod key over earlier verification methods`() {
        val doc =
            DidDocument(
                id = did,
                verificationMethod = listOf(vm(agreement, "X25519KeyAgreementKey2020"), vm(auth), vm(assertion)),
                authentication = listOf(auth),
                assertionMethod = listOf(assertion),
                keyAgreement = listOf(agreement),
            )
        assertEquals(assertion.value, selectSigningKeyId(doc))
    }

    @Test
    fun `falls back to authentication when there is no assertionMethod`() {
        val doc =
            DidDocument(
                id = did,
                verificationMethod = listOf(vm(agreement), vm(auth)),
                authentication = listOf(auth),
                keyAgreement = listOf(agreement),
            )
        assertEquals(auth.value, selectSigningKeyId(doc))
    }

    @Test
    fun `falls back to the first non key-agreement method when no relationship is declared`() {
        val doc =
            DidDocument(
                id = did,
                verificationMethod = listOf(vm(agreement), vm(assertion)),
                keyAgreement = listOf(agreement),
            )
        assertEquals(assertion.value, selectSigningKeyId(doc))
    }

    @Test
    fun `fails when the document only has key-agreement keys or no keys`() {
        assertFailsWith<IllegalStateException> {
            selectSigningKeyId(DidDocument(id = did, verificationMethod = listOf(vm(agreement)), keyAgreement = listOf(agreement)))
        }
        assertFailsWith<IllegalStateException> { selectSigningKeyId(DidDocument(id = did)) }
    }
}
