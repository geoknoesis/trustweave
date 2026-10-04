package org.trustweave.core.identifiers

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdentifiersTest {
    @Test
    fun acceptsAbsoluteFragmentAndRelativeIris() {
        listOf("https://example.com/a#b", "did:key:z6Mk", "urn:uuid:1234", "#key-1", "relative/path", "mailto:a@b.c").forEach {
            assertEquals(it, Iri(it).value)
        }
    }

    @Test
    fun rejectsBlankAndMalformedIris() {
        listOf("", "   ", ":nope", "1http://x", "scheme:", "bad scheme:x", "has space").forEach {
            assertFailsWith<IllegalArgumentException>(it) { Iri(it) }
        }
    }

    @Test
    fun classifiesSchemesCaseInsensitively() {
        assertTrue(Iri("HTTPS://example.com").isHttpUrl)
        assertTrue(Iri("http://example.com").isHttpUrl)
        assertTrue(Iri("DID:key:abc").isDid)
        assertTrue(Iri("urn:uuid:1").isUrn)
        assertFalse(Iri("did:key:abc").isHttpUrl)
        assertFalse(Iri("https://example.com").isDid)
        assertEquals("did", Iri("did:key:abc").scheme)
    }

    @Test
    fun fragmentOnlyAndRelativeIrisAreNotUris() {
        assertFalse(Iri("#frag").isUri)
        assertEquals("", Iri("#frag").scheme)
        assertFalse(Iri("relative/path").isUri)
        assertTrue(Iri("did:key:abc").isUri)
    }

    @Test
    fun fragmentAndWithoutFragment() {
        val iri = Iri("did:key:abc#key-1")
        assertEquals("key-1", iri.fragment)
        assertEquals(Iri("did:key:abc"), iri.withoutFragment)
        assertNull(Iri("did:key:abc#").fragment)
        val plain = Iri("did:key:abc")
        assertNull(plain.fragment)
        assertSame(plain, plain.withoutFragment)
    }

    @Test
    fun equalityIsByExactValue() {
        assertEquals(Iri("did:key:abc"), Iri("did:key:abc"))
        assertEquals(Iri("did:key:abc").hashCode(), Iri("did:key:abc").hashCode())
        assertNotEquals(Iri("did:key:abc"), Iri("did:key:ABC"))
        assertEquals("did:key:abc", Iri("did:key:abc").toString())
    }

    @Test
    fun iriSerializesAsPlainStringAndRejectsInvalidInputOnDecode() {
        assertEquals("\"https://example.com/x\"", Json.encodeToString(IriSerializer, Iri("https://example.com/x")))
        assertEquals(Iri("did:key:abc"), Json.decodeFromString(IriSerializer, "\"did:key:abc\""))
        assertFailsWith<SerializationException> { Json.decodeFromString(IriSerializer, "\"has space\"") }
    }

    @Test
    fun keyIdValidation() {
        assertEquals("key-1", KeyId("key-1").value)
        assertFailsWith<IllegalArgumentException> { KeyId("") }
        assertFailsWith<IllegalArgumentException> { KeyId("  ") }
        assertFailsWith<IllegalArgumentException> { KeyId("key 1") }
        assertFailsWith<IllegalArgumentException> { KeyId("key\t1") }
    }

    @Test
    fun keyIdFragmentHandling() {
        assertTrue(KeyId("#key-1").isFragment)
        assertEquals("key-1", KeyId("#key-1").fragmentValue)
        assertFalse(KeyId("key-1").isFragment)
        assertEquals("key-1", KeyId("key-1").fragmentValue)
    }

    @Test
    fun keyIdSerializerRoundTripsAndRejectsInvalid() {
        assertEquals("\"key-1\"", Json.encodeToString(KeyIdSerializer, KeyId("key-1")))
        assertEquals(KeyId("key-1"), Json.decodeFromString(KeyIdSerializer, "\"key-1\""))
        assertFailsWith<SerializationException> { Json.decodeFromString(KeyIdSerializer, "\"a b\"") }
    }

    @Test
    fun safeParsingReturnsNullInsteadOfThrowing() {
        assertEquals(Iri("https://example.com"), "https://example.com".toIriOrNull())
        assertNull(":invalid".toIriOrNull())
        assertNull("".toIriOrNull())
        assertEquals(KeyId("key-1"), "key-1".toKeyIdOrNull())
        assertNull("key 1".toKeyIdOrNull())
        assertNull("   ".toKeyIdOrNull())
    }
}
