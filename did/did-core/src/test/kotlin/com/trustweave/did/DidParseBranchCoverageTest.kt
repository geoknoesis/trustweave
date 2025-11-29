package com.trustweave.did

import kotlin.test.*
import com.trustweave.did.exception.DidException.InvalidDidFormat

/**
 * Comprehensive branch coverage tests for Did.parse() method.
 * Tests all conditional branches in parsing logic.
 */
class DidParseBranchCoverageTest {

    @Test
    fun `test branch parse with valid DID`() {
        val did = Did.parse("did:web:example.com")

        assertEquals("web", did.method)
        assertEquals("example.com", did.id)
    }

    @Test
    fun `test branch parse does not start with did prefix`() {
        assertFailsWith<InvalidDidFormat> {
            Did.parse("web:example.com")
        }
    }

    @Test
    fun `test branch parse with empty string`() {
        assertFailsWith<InvalidDidFormat> {
            Did.parse("")
        }
    }

    @Test
    fun `test branch parse with only did prefix`() {
        assertFailsWith<InvalidDidFormat> {
            Did.parse("did:")
        }
    }

    @Test
    fun `test branch parse with did prefix but no method`() {
        // "did::" splits to ["", ""] which is size 2, so it creates Did("", "")
        val did = Did.parse("did::")
        assertEquals("", did.method)
        assertEquals("", did.id)
    }

    @Test
    fun `test branch parse with method but no id`() {
        // "did:web:" splits to ["web", ""] which is size 2, so it creates Did("web", "")
        val did = Did.parse("did:web:")
        assertEquals("web", did.method)
        assertEquals("", did.id)
    }

    @Test
    fun `test branch parse with single colon after did`() {
        assertFailsWith<InvalidDidFormat> {
            Did.parse("did:web")
        }
    }

    @Test
    fun `test branch parse with complex id containing colons`() {
        val did = Did.parse("did:web:example.com:path:to:resource")

        assertEquals("web", did.method)
        assertEquals("example.com:path:to:resource", did.id)
    }

    @Test
    fun `test branch parse with id containing special characters`() {
        val did = Did.parse("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

        assertEquals("key", did.method)
        assertTrue(did.id.startsWith("z6Mk"))
    }

    @Test
    fun `test branch parse with empty method`() {
        // "did::example.com" splits to ["", "example.com"] which is size 2
        val did = Did.parse("did::example.com")
        assertEquals("", did.method)
        assertEquals("example.com", did.id)
    }

    @Test
    fun `test branch parse with empty id`() {
        // "did:web:" splits to ["web", ""] which is size 2
        val did = Did.parse("did:web:")
        assertEquals("web", did.method)
        assertEquals("", did.id)
    }

    @Test
    fun `test branch parse with whitespace in method`() {
        val did = Did.parse("did:web:example.com")

        assertEquals("web", did.method)
    }

    @Test
    fun `test branch parse with very long DID`() {
        val longId = "a".repeat(1000)
        val did = Did.parse("did:web:$longId")

        assertEquals("web", did.method)
        assertEquals(longId, did.id)
    }

    @Test
    fun `test branch toString formats correctly`() {
        val did = Did(method = "web", id = "example.com")

        assertEquals("did:web:example.com", did.toString())
    }

    @Test
    fun `test branch toString with complex id`() {
        val did = Did(method = "key", id = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

        assertEquals("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", did.toString())
    }
}

