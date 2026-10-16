package org.trustweave.did.identifiers

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.trustweave.core.identifiers.KeyId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DidIdentifiersTest {
    private val did = Did("did:web:example.com")

    @Test
    fun `a DID exposes its method and method-specific identifier`() {
        assertEquals("web", did.method)
        assertEquals("example.com", did.identifier)
        assertEquals("did:web:example.com", did.toString())
        assertEquals("did:key:z6Mk:extra", Did("did:key:z6Mk:extra").value)
        assertEquals("z6Mk:extra", Did("did:key:z6Mk:extra").identifier)
    }

    @Test
    fun `a DID with a fragment or path is not a bare DID and is rejected`() {
        assertFailsWith<IllegalArgumentException> { Did("did:key:abc#key-1") }
        assertFailsWith<IllegalArgumentException> { Did("did:web:example.com/a/b") }
    }

    @Test
    fun `malformed DIDs are rejected`() {
        listOf("", "key:abc", "did:", "did:key", "did:key:", "did::abc", "did:KEY:abc", "not a did").forEach {
            assertFailsWith<IllegalArgumentException>("'$it' should be rejected") { Did(it) }
        }
    }

    @Test
    fun `a bare DID has no path`() {
        assertEquals(null, did.path)
    }

    @Test
    fun `baseDid of a bare DID is the same instance and its DID URL base is equal`() {
        assertSame(did, did.baseDid)
        assertEquals(did, DidUrl("did:web:example.com/a/b?x=1#frag").did)
    }

    @Test
    fun `plus and with build the same verification method id for a name, a fragment and a key id`() {
        val expected = "did:web:example.com#key-1"
        assertEquals(expected, (did + "key-1").value)
        assertEquals(expected, (did + "#key-1").value)
        assertEquals(expected, (did with "key-1").value)
        assertEquals(expected, (did with KeyId("key-1")).value)
        assertEquals(expected, (did with KeyId("#key-1")).value)
    }

    @Test
    fun `isMethod compares the method name exactly`() {
        assertTrue(did isMethod "web")
        assertFalse(did isMethod "key")
        assertFalse(did isMethod "We")
    }

    @Test
    fun `DIDs sort by their string value`() {
        val sorted = listOf(Did("did:web:b"), Did("did:key:z"), Did("did:web:a")).sorted()
        assertEquals(listOf("did:key:z", "did:web:a", "did:web:b"), sorted.map { it.value })
        assertTrue(Did("did:web:a") < Did("did:web:b"))
    }

    @Test
    fun `DID serializes as a plain string and rejects invalid input with a SerializationException`() {
        assertEquals("\"did:web:example.com\"", Json.encodeToString(DidSerializer, did))
        assertEquals(did, Json.decodeFromString(DidSerializer, "\"did:web:example.com\""))
        assertFailsWith<SerializationException> { Json.decodeFromString(DidSerializer, "\"nope\"") }
        assertEquals("DidUrl", DidUrlSerializer.descriptor.serialName)
    }

    @Test
    fun `verification method id joins a DID and key id and decomposes again`() {
        val id = VerificationMethodId(did, KeyId("key-1"))
        assertEquals("did:web:example.com#key-1", id.value)
        assertEquals("did:web:example.com#key-1", id.toString())
        assertEquals(VerificationMethodId(did, KeyId("#key-1")).value, id.value)
        val (parsedDid, parsedKey) = id.decompose()
        assertEquals(did, parsedDid)
        assertEquals("key-1", parsedKey.value)
    }

    @Test
    fun `parse accepts a full DID URL or a fragment with a base DID`() {
        val full = VerificationMethodId.parse("did:web:example.com#key-1")
        assertEquals("did:web:example.com#key-1", full.value)
        val relative = VerificationMethodId.parse("#key-2", did)
        assertEquals("did:web:example.com#key-2", relative.value)
    }

    @Test
    fun `parse rejects a missing fragment and a relative fragment without a base`() {
        assertFailsWith<IllegalArgumentException> { VerificationMethodId.parse("did:web:example.com") }
        assertFailsWith<IllegalArgumentException> { VerificationMethodId.parse("#key-1") }
        assertFailsWith<IllegalArgumentException> { VerificationMethodId.parse("key-1", did) }
    }

    @Test
    fun `verification method id serializer round-trips and rejects bad input`() {
        val id = VerificationMethodId.parse("did:web:example.com#key-1")
        val json = Json.encodeToString(VerificationMethodIdSerializer, id)
        assertEquals("\"did:web:example.com#key-1\"", json)
        assertEquals(id, Json.decodeFromString(VerificationMethodIdSerializer, json))
        assertFailsWith<SerializationException> { Json.decodeFromString(VerificationMethodIdSerializer, "\"did:web:example.com\"") }
        assertNotEquals(id, VerificationMethodId.parse("did:web:example.com#key-2"))
    }

    @Test
    fun `DID URL serializer keeps the raw string`() {
        val url = DidUrl("did:web:example.com/p?versionId=2#k")
        val json = Json.encodeToString(DidUrlSerializer, url)
        assertEquals("\"did:web:example.com/p?versionId=2#k\"", json)
        assertEquals(url, Json.decodeFromString(DidUrlSerializer, json))
    }
}
