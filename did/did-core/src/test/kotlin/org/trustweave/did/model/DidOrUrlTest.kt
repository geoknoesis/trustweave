package org.trustweave.did.model

import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidOrUrlTest {

    @Test
    fun parse_did() {
        val d = DidOrUrl.parse("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertTrue(d is DidOrUrl.AsDid)
        assertEquals("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", (d as DidOrUrl.AsDid).did.value)
    }

    @Test
    fun parse_https_url() {
        val u = DidOrUrl.parse("https://example.com/blog")
        assertTrue(u is DidOrUrl.AsUrl)
        assertEquals("https://example.com/blog", (u as DidOrUrl.AsUrl).url)
    }

    @Test
    fun parse_urn() {
        val u = DidOrUrl.parse("urn:uuid:550e8400-e29b-41d4-a716-446655440000")
        assertTrue(u is DidOrUrl.AsUrl)
    }

    @Test
    fun tryParse_invalid_returns_null() {
        assertNull(DidOrUrl.tryParse(""))
        assertNull(DidOrUrl.tryParse("not-a-url"))
        assertNull(DidOrUrl.tryParse("//relative-only"))
    }

    @Test
    fun parse_blank_throws() {
        assertFailsWith<IllegalArgumentException> { DidOrUrl.parse("   ") }
    }

    @Test
    fun toStringValue_roundTrip() {
        val didStr = "did:web:example.com"
        assertEquals(didStr, DidOrUrl.AsDid(Did(didStr)).toStringValue())
        assertEquals("https://a.test/x", DidOrUrl.AsUrl("https://a.test/x").toStringValue())
    }
}
