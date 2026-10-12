package org.trustweave.credential.identifiers

import org.trustweave.core.identifiers.Iri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IdentifierParsingExtensionsTest {
    @Test
    fun `credential style parsing returns null for blank or malformed input and value otherwise`() {
        assertEquals("urn:uuid:1", "urn:uuid:1".toCredentialIdOrNull()?.value)
        assertNull("".toCredentialIdOrNull())
        assertNull("   ".toCredentialIdOrNull())
        assertEquals("did:key:abc", "did:key:abc".toIssuerIdOrNull()?.value)
        assertNull("".toIssuerIdOrNull())
        assertEquals("https://x.example/list/1", "https://x.example/list/1".toStatusListIdOrNull()?.value)
        assertNull(" ".toStatusListIdOrNull())
        assertEquals("https://x.example/schema", "https://x.example/schema".toSchemaIdOrNull()?.value)
        assertNull("".toSchemaIdOrNull())
    }

    @Test
    fun `iri narrowing preserves the value`() {
        val iri = Iri("https://example.com/credentials/1")
        assertEquals(iri.value, iri.asCredentialIdOrNull()?.value)
        assertEquals(iri.value, iri.asIssuerIdOrNull()?.value)
        assertEquals(iri.value, iri.requireCredentialId().value)
        assertEquals(iri.value, iri.requireIssuerId().value)
    }

    @Test
    fun `credential id and issuer id list mapping drops invalid entries and keeps order`() {
        val input = listOf("urn:a", "", "urn:b", "  ")
        assertEquals(listOf("urn:a", "urn:b"), input.mapToCredentialIdOrNull().map { it.value })
        assertEquals(listOf("urn:a", "urn:b"), input.mapToIssuerIdOrNull().map { it.value })
        assertEquals(emptyList(), emptyList<String>().mapToCredentialIdOrNull())

        val iris = listOf(Iri("urn:x"), Iri("did:key:y"))
        assertEquals(listOf("urn:x", "did:key:y"), iris.filterCredentialIds().map { it.value })
        assertEquals(listOf("urn:x", "did:key:y"), iris.filterIssuerIds().map { it.value })
    }

    @Test
    fun `protocol name parsing enforces lowercase alphanumeric with hyphens`() {
        assertEquals(ExchangeProtocolName.DidComm, "didcomm".toExchangeProtocolNameOrNull())
        assertEquals("siop-v2", "siop-v2".toExchangeProtocolNameOrNull()?.value)
        assertNull("DidComm".toExchangeProtocolNameOrNull())
        assertNull("did comm".toExchangeProtocolNameOrNull())
        assertNull("invalid-protocol!".toExchangeProtocolNameOrNull())
        assertNull("".toExchangeProtocolNameOrNull())

        assertEquals(ExchangeProtocolName.Oidc4Vci, "oidc4vci".requireExchangeProtocolName())
        val e = assertFailsWith<IllegalArgumentException> { "Bad_Name".requireExchangeProtocolName() }
        assertEquals(
            "String 'Bad_Name' is not a valid protocol name. Must be lowercase alphanumeric with hyphens.",
            e.message,
        )
    }

    @Test
    fun `offer request issue and presentation ids reject blank and require throws with the input`() {
        assertEquals("o-1", "o-1".toOfferIdOrNull()?.value)
        assertNull(" ".toOfferIdOrNull())
        assertEquals("r-1", "r-1".toRequestIdOrNull()?.value)
        assertNull("".toRequestIdOrNull())
        assertEquals("i-1", "i-1".toIssueIdOrNull()?.value)
        assertNull("".toIssueIdOrNull())
        assertEquals("p-1", "p-1".toPresentationIdOrNull()?.value)
        assertNull("\t".toPresentationIdOrNull())

        assertNotNull("o-1".requireOfferId())
        assertNotNull("r-1".requireRequestId())
        assertNotNull("i-1".requireIssueId())
        assertNotNull("p-1".requirePresentationId())
        assertEquals("String '' is not a valid OfferId", assertFailsWith<IllegalArgumentException> { "".requireOfferId() }.message)
        assertEquals("String ' ' is not a valid RequestId", assertFailsWith<IllegalArgumentException> { " ".requireRequestId() }.message)
        assertEquals("String '' is not a valid IssueId", assertFailsWith<IllegalArgumentException> { "".requireIssueId() }.message)
        assertEquals(
            "String '' is not a valid PresentationId",
            assertFailsWith<IllegalArgumentException> { "".requirePresentationId() }.message,
        )
    }

    @Test
    fun `protocol identifier list mapping filters invalid entries`() {
        assertEquals(
            listOf("didcomm", "oidc4vci"),
            listOf("didcomm", "oidc4vci", "invalid-protocol!").mapToExchangeProtocolNameOrNull().map { it.value },
        )
        assertEquals(listOf("a", "b"), listOf("a", "", "b").mapToOfferIdOrNull().map { it.value })
        assertEquals(listOf("a"), listOf("a", " ").mapToRequestIdOrNull().map { it.value })
        assertEquals(listOf("a"), listOf("", "a").mapToIssueIdOrNull().map { it.value })
        assertEquals(listOf("a"), listOf("a", "").mapToPresentationIdOrNull().map { it.value })
    }
}
