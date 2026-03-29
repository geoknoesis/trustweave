package org.trustweave.did.validation

import org.trustweave.core.util.ValidationResult
import kotlin.test.*

/**
 * Tests for DidValidator (DID 1.1 §3.1 ABNF).
 */
class DidValidatorTest {

    @Test
    fun validSimpleDid() {
        assertTrue(DidValidator.validateFormat("did:key:z6Mkf").isValid())
        assertTrue(DidValidator.validateFormat("did:example:123").isValid())
    }

    @Test
    fun validMethodSpecificIdWithColons() {
        assertTrue(DidValidator.validateFormat("did:example:a:b:c").isValid())
        assertTrue(DidValidator.validateFormat("did:plc:abc:def").isValid())
    }

    @Test
    fun validPctEncoded() {
        assertTrue(DidValidator.validateFormat("did:example:%41%42%43").isValid())
        assertTrue(DidValidator.validateFormat("did:key:z6Mk%2B").isValid())
        assertTrue(DidValidator.validateFormat("did:test:%00%FF").isValid())
    }

    @Test
    fun validIdcharSet() {
        assertTrue(DidValidator.validateFormat("did:e:Abc_1.2-3").isValid())
    }

    @Test
    fun emptyFails() {
        val r = DidValidator.validateFormat("")
        assertTrue(r is ValidationResult.Invalid)
        assertEquals(DidValidator.ErrorCodes.DID_EMPTY, (r as ValidationResult.Invalid).code)
    }

    @Test
    fun missingDidPrefixFails() {
        val r = DidValidator.validateFormat("key:z6Mkf")
        assertTrue(r is ValidationResult.Invalid)
        assertEquals(DidValidator.ErrorCodes.INVALID_DID_FORMAT, (r as ValidationResult.Invalid).code)
    }

    @Test
    fun missingSecondColonFails() {
        val r = DidValidator.validateFormat("did:key")
        assertTrue(r is ValidationResult.Invalid)
    }

    @Test
    fun emptyMethodSpecificIdFails() {
        val r = DidValidator.validateFormat("did:key:")
        assertTrue(r is ValidationResult.Invalid)
    }

    @Test
    fun uppercaseMethodNameFails() {
        val r = DidValidator.validateFormat("did:KEY:z6Mkf")
        assertTrue(r is ValidationResult.Invalid)
    }

    @Test
    fun invalidPctEncodedFails() {
        assertFalse(DidValidator.validateFormat("did:example:%4").isValid())
        assertFalse(DidValidator.validateFormat("did:example:%4G").isValid())
    }

    @Test
    fun extractMethod() {
        assertEquals("key", DidValidator.extractMethod("did:key:z6Mkf"))
        assertEquals("example", DidValidator.extractMethod("did:example:a:b:c"))
    }

    @Test
    fun extractMethodSpecificId() {
        assertEquals("z6Mkf", DidValidator.extractMethodSpecificId("did:key:z6Mkf"))
        assertEquals("a:b:c", DidValidator.extractMethodSpecificId("did:example:a:b:c"))
    }
}
