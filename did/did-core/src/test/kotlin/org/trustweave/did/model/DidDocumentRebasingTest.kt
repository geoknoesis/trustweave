package org.trustweave.did.model

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DidDocumentRebasingTest {
    private val from = Did("did:ethr:0xabc")
    private val to = Did("did:polygon:0xabc")
    private val other = Did("did:web:example.com")

    private fun vm(
        did: Did,
        controller: Did,
    ) = VerificationMethod(
        id = VerificationMethodId.parse("#k1", did),
        type = "EcdsaSecp256k1VerificationKey2019",
        controller = controller,
        publicKeyMultibase = "zabc",
    )

    @Test
    fun `rewrites id, controllers, key ids and relationship references together`() {
        val ref = VerificationMethodId.parse("#k1", from)
        val doc =
            DidDocument(
                id = from,
                controller = listOf(from, other),
                verificationMethod = listOf(vm(from, from), vm(from, other).copy(id = VerificationMethodId.parse("#k2", from))),
                authentication = listOf(ref),
                assertionMethod = listOf(ref),
                keyAgreement = listOf(ref),
                capabilityInvocation = listOf(ref),
                capabilityDelegation = listOf(ref),
                service =
                    listOf(
                        DidService(
                            "${from.value}#svc",
                            listOf("LinkedDomains"),
                            ServiceEndpoint.Url("https://example.com"),
                        ),
                    ),
            )

        val rebased = doc.rebasedTo(from, to)

        assertEquals(to, rebased.id)
        assertEquals(listOf(to, other), rebased.controller)
        assertEquals("did:polygon:0xabc#k1", rebased.verificationMethod[0].id.value)
        assertEquals(to, rebased.verificationMethod[0].controller)
        // A controller that names a different DID is external and stays untouched.
        assertEquals(other, rebased.verificationMethod[1].controller)
        listOf(
            rebased.authentication,
            rebased.assertionMethod,
            rebased.keyAgreement,
            rebased.capabilityInvocation,
            rebased.capabilityDelegation,
        ).forEach { assertEquals("did:polygon:0xabc#k1", it.single().value) }
        assertEquals("did:polygon:0xabc#svc", rebased.service.single().id)
        assertFalse("did:ethr" in rebased.toString())
    }
}
