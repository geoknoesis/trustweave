package org.trustweave.did.identifiers

import org.trustweave.core.identifiers.Iri
import org.trustweave.did.model.VerificationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DidIdentifiersExtensionsBehaviourTest {
    private val did = Did("did:example:alice")

    @Test
    fun `toDidOrNull parses valid DIDs and rejects bad input without throwing`() {
        assertEquals(did, "did:example:alice".toDidOrNull())
        assertNull("".toDidOrNull())
        assertNull("alice".toDidOrNull())
        assertNull("did:example:".toDidOrNull())
        assertNull("did::alice".toDidOrNull())
        assertNull("https://example.com".toDidOrNull())
    }

    @Test
    fun `toDidOrNull rejects a DID URL with a fragment since that is not a bare DID`() {
        assertNull("did:example:alice#key-1".toDidOrNull())
    }

    @Test
    fun `toVerificationMethodIdOrNull handles absolute, relative and invalid input`() {
        assertEquals("did:example:alice#key-1", "did:example:alice#key-1".toVerificationMethodIdOrNull()?.value)
        assertEquals("did:example:alice#key-1", "#key-1".toVerificationMethodIdOrNull(did)?.value)
        assertNull("#key-1".toVerificationMethodIdOrNull())
        assertNull("did:example:alice".toVerificationMethodIdOrNull())
        assertNull("garbage".toVerificationMethodIdOrNull(did))
    }

    @Test
    fun `asDidOrNull narrows DID IRIs only`() {
        assertEquals(did, Iri("did:example:alice").asDidOrNull())
        assertNull(Iri("https://example.com/x").asDidOrNull())
        assertNull(Iri("did:example:").asDidOrNull())
    }

    @Test
    fun `requireDid returns the DID or explains the failure`() {
        assertEquals(did, Iri("did:example:alice").requireDid())
        val error = assertFailsWith<IllegalArgumentException> { Iri("https://example.com/x").requireDid() }
        assertEquals("IRI 'https://example.com/x' is not a valid DID", error.message)
    }

    @Test
    fun `extractKeyId strips the fragment marker and is null safe`() {
        val vmId = VerificationMethodId.parse("did:example:alice#key-1")
        assertEquals("key-1", vmId.extractKeyId())

        val vm = VerificationMethod(vmId, "Ed25519VerificationKey2020", did, publicKeyMultibase = "z6Mk")
        assertEquals("key-1", vm.extractKeyId())
        val none: VerificationMethod? = null
        assertNull(none.extractKeyId())
    }
}
