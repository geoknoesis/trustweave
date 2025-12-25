package org.trustweave.core.identifiers

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IriTest {
    
    // ========== Construction and Validation ==========
    
    @Test
    fun `should create valid IRI with HTTP URL`() {
        val iri = Iri("http://example.com")
        assertEquals("http://example.com", iri.value)
    }
    
    @Test
    fun `should create valid IRI with HTTPS URL`() {
        val iri = Iri("https://example.com")
        assertEquals("https://example.com", iri.value)
    }
    
    @Test
    fun `should create valid IRI with DID`() {
        val iri = Iri("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertEquals("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", iri.value)
    }
    
    @Test
    fun `should create valid IRI with URN`() {
        val iri = Iri("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
        assertEquals("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6", iri.value)
    }
    
    @Test
    fun `should create valid fragment-only IRI`() {
        val iri = Iri("#key-1")
        assertEquals("#key-1", iri.value)
    }
    
    @Test
    fun `should create valid relative path IRI`() {
        val iri = Iri("path/to/resource")
        assertEquals("path/to/resource", iri.value)
    }
    
    @Test
    fun `should reject blank IRI`() {
        assertThrows<IllegalArgumentException> {
            Iri("")
        }
        assertThrows<IllegalArgumentException> {
            Iri("   ")
        }
    }
    
    @Test
    fun `should reject invalid IRI format`() {
        assertThrows<IllegalArgumentException> {
            Iri(":invalid")
        }
        assertThrows<IllegalArgumentException> {
            Iri("invalid:")
        }
    }
    
    // ========== isUri Property ==========
    
    @Test
    fun `isUri should return true for HTTP URL`() {
        val iri = Iri("http://example.com")
        assertTrue(iri.isUri)
    }
    
    @Test
    fun `isUri should return true for HTTPS URL`() {
        val iri = Iri("https://example.com")
        assertTrue(iri.isUri)
    }
    
    @Test
    fun `isUri should return true for DID`() {
        val iri = Iri("did:key:z6Mk...")
        assertTrue(iri.isUri)
    }
    
    @Test
    fun `isUri should return true for URN`() {
        val iri = Iri("urn:uuid:123")
        assertTrue(iri.isUri)
    }
    
    @Test
    fun `isUri should return false for fragment-only IRI`() {
        val iri = Iri("#key-1")
        assertFalse(iri.isUri)
    }
    
    @Test
    fun `isUri should return false for relative path IRI`() {
        val iri = Iri("path/to/resource")
        assertFalse(iri.isUri)
    }
    
    // ========== isHttpUrl Property ==========
    
    @Test
    fun `isHttpUrl should return true for HTTP URL`() {
        val iri = Iri("http://example.com")
        assertTrue(iri.isHttpUrl)
    }
    
    @Test
    fun `isHttpUrl should return true for HTTPS URL`() {
        val iri = Iri("https://example.com")
        assertTrue(iri.isHttpUrl)
    }
    
    @Test
    fun `isHttpUrl should be case-insensitive for scheme`() {
        val uppercaseHttpIri = Iri("HTTP://example.com")
        val uppercaseHttpsIri = Iri("HTTPS://example.com")
        val mixedCaseHttpIri = Iri("Http://example.com")
        assertTrue(uppercaseHttpIri.isHttpUrl)
        assertTrue(uppercaseHttpsIri.isHttpUrl)
        assertTrue(mixedCaseHttpIri.isHttpUrl)
    }
    
    @Test
    fun `isHttpUrl should return false for DID`() {
        val iri = Iri("did:key:z6Mk...")
        assertFalse(iri.isHttpUrl)
    }
    
    @Test
    fun `isHttpUrl should return false for URN`() {
        val iri = Iri("urn:uuid:123")
        assertFalse(iri.isHttpUrl)
    }
    
    @Test
    fun `isHttpUrl should return false for fragment-only IRI`() {
        val iri = Iri("#key-1")
        assertFalse(iri.isHttpUrl)
    }
    
    // ========== isUrn Property ==========
    
    @Test
    fun `isUrn should return true for URN`() {
        val iri = Iri("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6")
        assertTrue(iri.isUrn)
    }
    
    @Test
    fun `isUrn should be case-insensitive`() {
        val uppercaseUrnIri = Iri("URN:uuid:123")
        val mixedCaseUrnIri = Iri("Urn:uuid:123")
        assertTrue(uppercaseUrnIri.isUrn)
        assertTrue(mixedCaseUrnIri.isUrn)
    }
    
    @Test
    fun `isUrn should return false for HTTP URL`() {
        val iri = Iri("http://example.com")
        assertFalse(iri.isUrn)
    }
    
    @Test
    fun `isUrn should return false for DID`() {
        val iri = Iri("did:key:z6Mk...")
        assertFalse(iri.isUrn)
    }
    
    // ========== isDid Property ==========
    
    @Test
    fun `isDid should return true for DID`() {
        val iri = Iri("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertTrue(iri.isDid)
    }
    
    @Test
    fun `isDid should be case-insensitive`() {
        val uppercaseDidIri = Iri("DID:key:z6Mk...")
        val mixedCaseDidIri = Iri("Did:key:z6Mk...")
        assertTrue(uppercaseDidIri.isDid)
        assertTrue(mixedCaseDidIri.isDid)
    }
    
    @Test
    fun `isDid should return false for HTTP URL`() {
        val iri = Iri("http://example.com")
        assertFalse(iri.isDid)
    }
    
    @Test
    fun `isDid should return false for URN`() {
        val iri = Iri("urn:uuid:123")
        assertFalse(iri.isDid)
    }
    
    // ========== scheme Property ==========
    
    @Test
    fun `scheme should extract HTTP scheme`() {
        val iri = Iri("http://example.com")
        assertEquals("http", iri.scheme)
    }
    
    @Test
    fun `scheme should extract HTTPS scheme`() {
        val iri = Iri("https://example.com")
        assertEquals("https", iri.scheme)
    }
    
    @Test
    fun `scheme should extract DID scheme`() {
        val iri = Iri("did:key:z6Mk...")
        assertEquals("did", iri.scheme)
    }
    
    @Test
    fun `scheme should extract URN scheme`() {
        val iri = Iri("urn:uuid:123")
        assertEquals("urn", iri.scheme)
    }
    
    @Test
    fun `scheme should return empty string for fragment-only IRI`() {
        val iri = Iri("#key-1")
        assertEquals("", iri.scheme)
    }
    
    @Test
    fun `scheme should return empty string for relative path IRI`() {
        val iri = Iri("path/to/resource")
        assertEquals("", iri.scheme)
    }
    
    @Test
    fun `scheme should handle URL with port`() {
        val iri = Iri("http://example.com:8080/path")
        assertEquals("http", iri.scheme)
    }
    
    // ========== fragment Property ==========
    
    @Test
    fun `fragment should extract fragment from IRI`() {
        val iri = Iri("https://example.com/resource#fragment")
        assertEquals("fragment", iri.fragment)
    }
    
    @Test
    fun `fragment should return null when no fragment present`() {
        val iri = Iri("https://example.com/resource")
        assertNull(iri.fragment)
    }
    
    @Test
    fun `fragment should return null for empty fragment`() {
        val iri = Iri("https://example.com/resource#")
        assertNull(iri.fragment)
    }
    
    @Test
    fun `fragment should handle fragment-only IRI`() {
        val iri = Iri("#key-1")
        assertEquals("key-1", iri.fragment)
    }
    
    @Test
    fun `fragment should handle multiple hash characters`() {
        val iri = Iri("https://example.com#frag#ment")
        assertEquals("frag#ment", iri.fragment)
    }
    
    @Test
    fun `fragment should handle complex fragment`() {
        val iri = Iri("https://example.com#fragment-with-special-chars-123")
        assertEquals("fragment-with-special-chars-123", iri.fragment)
    }
    
    // ========== withoutFragment Property ==========
    
    @Test
    fun `withoutFragment should remove fragment`() {
        val iri = Iri("https://example.com/resource#fragment")
        val without = iri.withoutFragment
        assertEquals("https://example.com/resource", without.value)
    }
    
    @Test
    fun `withoutFragment should return same instance when no fragment`() {
        val iri = Iri("https://example.com/resource")
        val without = iri.withoutFragment
        assertTrue(iri === without, "Should return same instance when no fragment")
    }
    
    @Test
    fun `withoutFragment should handle fragment-only IRI`() {
        val iri = Iri("#key-1")
        // Removing fragment from "#key-1" would create empty string, which is invalid
        // So this should throw an exception
        assertThrows<IllegalArgumentException> {
            iri.withoutFragment
        }
    }
    
    @Test
    fun `withoutFragment should handle multiple hash characters`() {
        val iri = Iri("https://example.com#frag#ment")
        val without = iri.withoutFragment
        assertEquals("https://example.com", without.value)
    }
    
    // ========== toString, equals, hashCode ==========
    
    @Test
    fun `toString should return IRI value`() {
        val iri = Iri("https://example.com")
        assertEquals("https://example.com", iri.toString())
    }
    
    @Test
    fun `equals should compare by value`() {
        val firstIri = Iri("https://example.com")
        val secondIri = Iri("https://example.com")
        assertEquals(firstIri, secondIri)
    }
    
    @Test
    fun `equals should return false for different values`() {
        val exampleComIri = Iri("https://example.com")
        val exampleOrgIri = Iri("https://example.org")
        assertFalse(exampleComIri == exampleOrgIri)
    }
    
    @Test
    fun `equals should return true for same instance`() {
        val iri = Iri("https://example.com")
        assertTrue(iri == iri)
    }
    
    @Test
    fun `hashCode should be consistent with equals`() {
        val firstIri = Iri("https://example.com")
        val secondIriWithSameValue = Iri("https://example.com")
        assertEquals(firstIri.hashCode(), secondIriWithSameValue.hashCode())
    }
    
    // ========== Serialization ==========
    
    @Test
    fun `should serialize to JSON string`() {
        val iri = Iri("https://example.com")
        val json = Json.encodeToString(IriSerializer, iri)
        assertEquals("\"https://example.com\"", json)
    }
    
    @Test
    fun `should deserialize from JSON string`() {
        val json = "\"https://example.com\""
        val iri = Json.decodeFromString(IriSerializer, json)
        assertEquals("https://example.com", iri.value)
    }
    
    @Test
    fun `should throw SerializationException for invalid IRI during deserialization`() {
        val json = "\":invalid\""
        assertThrows<SerializationException> {
            Json.decodeFromString(IriSerializer, json)
        }
    }
    
    // ========== Edge Cases ==========
    
    @Test
    fun `should handle IRI with port`() {
        val iri = Iri("http://example.com:8080/path")
        assertEquals("http", iri.scheme)
        assertTrue(iri.isHttpUrl)
    }
    
    @Test
    fun `should handle IRI with query parameters`() {
        val iri = Iri("https://example.com?param=value")
        assertEquals("https", iri.scheme)
        assertTrue(iri.isHttpUrl)
    }
    
    @Test
    fun `should handle IRI with fragment and query`() {
        val iri = Iri("https://example.com?param=value#fragment")
        assertEquals("fragment", iri.fragment)
        assertEquals("https://example.com?param=value", iri.withoutFragment.value)
    }
    
    @Test
    fun `should handle very long IRI`() {
        val longPath = "/" + "a".repeat(1000)
        val iri = Iri("https://example.com$longPath")
        assertEquals("https", iri.scheme)
        assertTrue(iri.isHttpUrl)
    }
    
    @Test
    fun `should handle IRI with special characters in path`() {
        val iri = Iri("https://example.com/path-with-dashes_and_underscores")
        assertEquals("https://example.com/path-with-dashes_and_underscores", iri.value)
    }
    
    @Test
    fun `should handle IRI with only scheme and colon`() {
        // This should fail validation (scheme must be followed by something)
        assertThrows<IllegalArgumentException> {
            Iri("http:")
        }
    }
    
    @Test
    fun `should handle IRI with scheme starting with number`() {
        // Schemes must start with a letter
        assertThrows<IllegalArgumentException> {
            Iri("123scheme:value")
        }
    }
    
    @Test
    fun `should handle IRI with invalid characters in scheme`() {
        assertThrows<IllegalArgumentException> {
            Iri("scheme@invalid:value")
        }
    }
    
    @Test
    fun `fragment should handle IRI ending with hash`() {
        val iri = Iri("https://example.com#")
        assertNull(iri.fragment)
    }
    
    @Test
    fun `fragment should handle IRI with hash in middle`() {
        val iri = Iri("https://example.com#frag#ment")
        assertEquals("frag#ment", iri.fragment)
    }
    
    @Test
    fun `withoutFragment should preserve query parameters`() {
        val iri = Iri("https://example.com?param=value#fragment")
        val without = iri.withoutFragment
        assertEquals("https://example.com?param=value", without.value)
    }
    
    @Test
    fun `should handle IRI with percent encoding`() {
        // Percent encoding is allowed in IRIs
        val iri = Iri("https://example.com/path%20with%20spaces")
        assertEquals("https://example.com/path%20with%20spaces", iri.value)
    }
    
    @Test
    fun `should handle IRI with Unicode characters`() {
        // Basic validation allows Unicode, though full RFC 3987 would normalize
        val unicodePathIri = Iri("https://example.com/路径")
        assertEquals("https://example.com/路径", unicodePathIri.value)
    }
    
    @Test
    fun `should handle IRI with various special characters in scheme`() {
        // Test schemes with allowed special characters
        val plusSchemeIri = Iri("scheme+plus:value")
        val dotSchemeIri = Iri("scheme.dot:value")
        val dashSchemeIri = Iri("scheme-dash:value")
        assertEquals("scheme+plus", plusSchemeIri.scheme)
        assertEquals("scheme.dot", dotSchemeIri.scheme)
        assertEquals("scheme-dash", dashSchemeIri.scheme)
    }
    
    @Test
    fun `should handle IRI with complex fragment containing special characters`() {
        val complexFragmentIri = Iri("https://example.com#fragment-with_underscores.and.dots-123")
        assertEquals("fragment-with_underscores.and.dots-123", complexFragmentIri.fragment)
    }
    
    @Test
    fun `should handle multiple fragments in sequence`() {
        // Test that fragment extraction handles multiple # characters correctly
        val multiHashIri = Iri("https://example.com#first#second#third")
        assertEquals("first#second#third", multiHashIri.fragment)
        assertEquals("https://example.com", multiHashIri.withoutFragment.value)
    }
    
    @Test
    fun `should validate scheme with numbers after first letter`() {
        val schemeWithNumbersIri = Iri("http2:example.com")
        assertEquals("http2", schemeWithNumbersIri.scheme)
        assertTrue(schemeWithNumbersIri.isUri)
    }
}

class KeyIdTest {
    
    // ========== Construction and Validation ==========
    
    @Test
    fun `should create valid KeyId`() {
        val keyId = KeyId("key-1")
        assertEquals("key-1", keyId.value)
    }
    
    @Test
    fun `should create KeyId with fragment`() {
        val keyId = KeyId("#key-1")
        assertEquals("#key-1", keyId.value)
    }
    
    @Test
    fun `should reject blank KeyId`() {
        assertThrows<IllegalArgumentException> {
            KeyId("")
        }
        assertThrows<IllegalArgumentException> {
            KeyId("   ")
        }
    }
    
    @Test
    fun `should reject KeyId with spaces`() {
        assertThrows<IllegalArgumentException> {
            KeyId("key 1")
        }
    }
    
    @Test
    fun `should reject KeyId with newlines`() {
        assertThrows<IllegalArgumentException> {
            KeyId("key\n1")
        }
    }
    
    @Test
    fun `should reject KeyId with tabs`() {
        assertThrows<IllegalArgumentException> {
            KeyId("key\t1")
        }
    }
    
    @Test
    fun `should reject KeyId with carriage return`() {
        assertThrows<IllegalArgumentException> {
            KeyId("key\r1")
        }
    }
    
    @Test
    fun `should reject KeyId with form feed`() {
        assertThrows<IllegalArgumentException> {
            KeyId("key\u000C1")
        }
    }
    
    // ========== isFragment Property ==========
    
    @Test
    fun `isFragment should return true for fragment KeyId`() {
        val keyId = KeyId("#key-1")
        assertTrue(keyId.isFragment)
    }
    
    @Test
    fun `isFragment should return false for non-fragment KeyId`() {
        val keyId = KeyId("key-1")
        assertFalse(keyId.isFragment)
    }
    
    // ========== fragmentValue Property ==========
    
    @Test
    fun `fragmentValue should remove hash from fragment KeyId`() {
        val keyId = KeyId("#key-1")
        assertEquals("key-1", keyId.fragmentValue)
    }
    
    @Test
    fun `fragmentValue should return value for non-fragment KeyId`() {
        val keyId = KeyId("key-1")
        assertEquals("key-1", keyId.fragmentValue)
    }
    
    @Test
    fun `fragmentValue should handle empty fragment`() {
        val keyId = KeyId("#")
        assertEquals("", keyId.fragmentValue)
    }
    
    // ========== toString ==========
    
    @Test
    fun `toString should return KeyId value`() {
        val keyId = KeyId("key-1")
        assertEquals("key-1", keyId.toString())
    }
    
    // ========== Serialization ==========
    
    @Test
    fun `should serialize KeyId to JSON string`() {
        val keyId = KeyId("key-1")
        val json = Json.encodeToString(KeyIdSerializer, keyId)
        assertEquals("\"key-1\"", json)
    }
    
    @Test
    fun `should deserialize KeyId from JSON string`() {
        val json = "\"key-1\""
        val keyId = Json.decodeFromString(KeyIdSerializer, json)
        assertEquals("key-1", keyId.value)
    }
    
    @Test
    fun `should throw SerializationException for invalid KeyId during deserialization`() {
        val json = "\"key 1\""
        assertThrows<SerializationException> {
            Json.decodeFromString(KeyIdSerializer, json)
        }
    }
    
    // ========== Edge Cases ==========
    
    @Test
    fun `should handle KeyId with special characters`() {
        val keyId = KeyId("key-1_with.special-chars")
        assertEquals("key-1_with.special-chars", keyId.value)
    }
    
    @Test
    fun `should handle very long KeyId`() {
        val longKey = "key-" + "a".repeat(1000)
        val keyId = KeyId(longKey)
        assertEquals(longKey, keyId.value)
    }
}

class IdentifiersExtensionsTest {
    
    // ========== toIriOrNull Extension ==========
    
    @Test
    fun `toIriOrNull should return Iri for valid HTTP URL`() {
        val iri = "http://example.com".toIriOrNull()
        assertNotNull(iri)
        assertEquals("http://example.com", iri?.value)
    }
    
    @Test
    fun `toIriOrNull should return Iri for valid HTTPS URL`() {
        val iri = "https://example.com".toIriOrNull()
        assertNotNull(iri)
        assertEquals("https://example.com", iri?.value)
    }
    
    @Test
    fun `toIriOrNull should return Iri for valid DID`() {
        val iri = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK".toIriOrNull()
        assertNotNull(iri)
        assertEquals("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", iri?.value)
    }
    
    @Test
    fun `toIriOrNull should return Iri for valid URN`() {
        val iri = "urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6".toIriOrNull()
        assertNotNull(iri)
        assertEquals("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6", iri?.value)
    }
    
    @Test
    fun `toIriOrNull should return Iri for fragment-only IRI`() {
        val iri = "#key-1".toIriOrNull()
        assertNotNull(iri)
        assertEquals("#key-1", iri?.value)
    }
    
    @Test
    fun `toIriOrNull should return null for blank string`() {
        val iri = "".toIriOrNull()
        assertNull(iri)
    }
    
    @Test
    fun `toIriOrNull should return null for whitespace only`() {
        val iri = "   ".toIriOrNull()
        assertNull(iri)
    }
    
    @Test
    fun `toIriOrNull should return null for invalid format`() {
        val invalidIriStartingWithColon = ":invalid".toIriOrNull()
        val invalidIriEndingWithColon = "invalid:".toIriOrNull()
        assertNull(invalidIriStartingWithColon)
        assertNull(invalidIriEndingWithColon)
    }
    
    @Test
    fun `toIriOrNull should return null for empty scheme`() {
        val iri = ":".toIriOrNull()
        assertNull(iri)
    }
    
    // ========== toKeyIdOrNull Extension ==========
    
    @Test
    fun `toKeyIdOrNull should return KeyId for valid string`() {
        val keyId = "key-1".toKeyIdOrNull()
        assertNotNull(keyId)
        assertEquals("key-1", keyId?.value)
    }
    
    @Test
    fun `toKeyIdOrNull should return KeyId for fragment string`() {
        val keyId = "#key-1".toKeyIdOrNull()
        assertNotNull(keyId)
        assertEquals("#key-1", keyId?.value)
    }
    
    @Test
    fun `toKeyIdOrNull should return null for blank string`() {
        val keyId = "".toKeyIdOrNull()
        assertNull(keyId)
    }
    
    @Test
    fun `toKeyIdOrNull should return null for whitespace only`() {
        val keyId = "   ".toKeyIdOrNull()
        assertNull(keyId)
    }
    
    @Test
    fun `toKeyIdOrNull should return null for string with space`() {
        val keyId = "key 1".toKeyIdOrNull()
        assertNull(keyId)
    }
    
    @Test
    fun `toKeyIdOrNull should return null for string with newline`() {
        val keyId = "key\n1".toKeyIdOrNull()
        assertNull(keyId)
    }
    
    @Test
    fun `toKeyIdOrNull should return null for string with tabs`() {
        val keyId = "key\t1".toKeyIdOrNull()
        assertNull(keyId)
    }
    
    @Test
    fun `toKeyIdOrNull should return null for string with carriage return`() {
        val keyId = "key\r1".toKeyIdOrNull()
        assertNull(keyId)
    }
    
    @Test
    fun `toKeyIdOrNull should handle special characters`() {
        val keyId = "key-1_with.special-chars".toKeyIdOrNull()
        assertNotNull(keyId)
        assertEquals("key-1_with.special-chars", keyId?.value)
    }
}

