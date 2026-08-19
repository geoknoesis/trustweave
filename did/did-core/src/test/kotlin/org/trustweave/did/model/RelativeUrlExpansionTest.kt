package org.trustweave.did.model

import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RelativeUrlExpansionTest {

    private val did = Did("did:example:123456789abcdefghi")

    private fun documentWithService(serviceId: String) = DidDocument(
        id = did,
        service = listOf(
            DidService(
                id = serviceId,
                type = listOf("VerifiableCredentialService"),
                serviceEndpoint = ServiceEndpoint.Url("https://example.com/vc/")
            )
        )
    )

    @Test
    fun `a fragment service id is expanded against the document id`() {
        val expanded = documentWithService("#vcs").expandRelativeDidUrls()
        assertEquals("did:example:123456789abcdefghi#vcs", expanded.service.first().id)
    }

    @Test
    fun `a relative path service id is expanded against the document id`() {
        val expanded = documentWithService("some/path").expandRelativeDidUrls()
        assertEquals("did:example:123456789abcdefghi/some/path", expanded.service.first().id)
    }

    @Test
    fun `an absolute DID URL service id is unchanged`() {
        val expanded = documentWithService("did:example:other#vcs").expandRelativeDidUrls()
        assertEquals("did:example:other#vcs", expanded.service.first().id)
    }

    @Test
    fun `an absolute http service id is unchanged`() {
        val expanded = documentWithService("https://example.com/svc").expandRelativeDidUrls()
        assertEquals("https://example.com/svc", expanded.service.first().id)
    }

    @Test
    fun `a document with nothing to expand is returned unchanged`() {
        val document = DidDocument(id = did)
        assertSame(document, document.expandRelativeDidUrls())
    }
}
