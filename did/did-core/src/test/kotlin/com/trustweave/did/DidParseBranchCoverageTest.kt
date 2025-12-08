package com.trustweave.did

import com.trustweave.did.identifiers.Did
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for Did constructor.
 * Tests all conditional branches in validation logic.
 */
class DidParseBranchCoverageTest {

    @Test
    fun `test branch parse with valid DID`() {
        val did = Did("did:web:example.com")

        assertEquals("web", did.method)
        assertEquals("did:web:example.com", did.value)
    }

    @Test
    fun `test branch parse does not start with did prefix`() {
        assertFailsWith<IllegalArgumentException> {
            Did("web:example.com")
        }
    }

    @Test
    fun `test branch parse with empty string`() {
        assertFailsWith<IllegalArgumentException> {
            Did("")
        }
    }

    @Test
    fun `test branch parse with only did prefix`() {
        assertFailsWith<IllegalArgumentException> {
            Did("did:")
        }
    }

    @Test
    fun `test branch parse with did prefix but no method`() {
        // "did::" should fail validation
        assertFailsWith<IllegalArgumentException> {
            Did("did::")
        }
    }

    @Test
    fun `test branch parse with method but no id`() {
        // "did:web:" should fail validation
        assertFailsWith<IllegalArgumentException> {
            Did("did:web:")
        }
    }

    @Test
    fun `test branch parse with single colon after did`() {
        assertFailsWith<IllegalArgumentException> {
            Did("did:web")
        }
    }

    @Test
    fun `test branch parse with complex id containing colons`() {
        val did = Did("did:web:example.com:path:to:resource")

        assertEquals("web", did.method)
        assertEquals("did:web:example.com:path:to:resource", did.value)
    }

    @Test
    fun `test branch parse with id containing special characters`() {
        val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

        assertEquals("key", did.method)
        assertTrue(did.value.startsWith("did:key:z6Mk"))
    }

    @Test
    fun `test branch parse with empty method`() {
        // "did::example.com" should fail validation
        assertFailsWith<IllegalArgumentException> {
            Did("did::example.com")
        }
    }

    @Test
    fun `test branch parse with empty id`() {
        // "did:web:" should fail validation
        assertFailsWith<IllegalArgumentException> {
            Did("did:web:")
        }
    }

    @Test
    fun `test branch parse with valid method`() {
        val did = Did("did:web:example.com")

        assertEquals("web", did.method)
        assertEquals("did:web:example.com", did.value)
    }

    @Test
    fun `test branch parse with very long DID`() {
        val longId = "a".repeat(1000)
        val did = Did("did:web:$longId")

        assertEquals("web", did.method)
        assertEquals("did:web:$longId", did.value)
    }

    // toString is tested implicitly through value property access in other tests
}

