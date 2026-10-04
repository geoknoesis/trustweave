package org.trustweave.did.util

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.dsl.UniversalResolverBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionTest {
    @Test
    fun `url keeps scheme host and port only`() {
        assertEquals("https://a.b:8443", Redaction.url("https://u:p@a.b:8443/v2/KEY?k=KEY"))
        assertEquals("https://a.b", Redaction.url("https://a.b/v2/KEY"))
        assertEquals("<redacted-url>", Redaction.url("not a url KEY"))
        assertEquals("null", Redaction.url(null))
    }

    @Test
    fun `DidCreationOptions toString does not leak additionalProperties values`() {
        val text = DidCreationOptions(additionalProperties = mapOf("privateKey" to "TOPSECRET", "apiKey" to "TOPSECRET")).toString()
        assertFalse(text.contains("TOPSECRET"), text)
        assertTrue(text.contains("privateKey"), text)
    }

    @Test
    fun `UniversalResolverBuilder toString does not leak apiKey or url path`() {
        val b = UniversalResolverBuilder("https://r.example.org/path/TOPSECRET")
        b.apiKey = "TOPSECRET"
        assertFalse(b.toString().contains("TOPSECRET"), b.toString())
    }
}
