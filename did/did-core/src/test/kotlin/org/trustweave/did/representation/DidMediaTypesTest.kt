package org.trustweave.did.representation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DidMediaTypesTest {
    @Test
    fun `spec media types have their CR values`() {
        assertEquals("application/did", DidMediaTypes.DID)
        assertEquals("application/did-resolution", DidMediaTypes.DID_RESOLUTION)
        assertEquals("application/did-url-dereferencing", DidMediaTypes.DID_URL_DEREFERENCING)
    }

    @Test
    fun `legacy representation types remain available`() {
        assertEquals("application/did+ld+json", DidMediaTypes.DID_LD_JSON)
        assertEquals("application/did+json", DidMediaTypes.DID_JSON)
    }

    @Test
    fun `application-did is the first supported document type`() {
        assertEquals(DidMediaTypes.DID, DidMediaTypes.SUPPORTED_DOCUMENT_TYPES.first())
    }

    @Test
    fun `supported document types accept CR and legacy forms`() {
        assertTrue(DidMediaTypes.isSupportedDocumentType("application/did"))
        assertTrue(DidMediaTypes.isSupportedDocumentType("application/did+ld+json"))
        assertTrue(DidMediaTypes.isSupportedDocumentType("application/did+json"))
        assertTrue(DidMediaTypes.isSupportedDocumentType("application/json"))
        assertFalse(DidMediaTypes.isSupportedDocumentType("application/did+cbor"))
    }

    @Test
    fun `media type matching ignores parameters and case`() {
        assertTrue(DidMediaTypes.isSupportedDocumentType("APPLICATION/DID; profile=\"https://www.w3.org/ns/did\""))
    }

    @Test
    fun `the legacy producer constant now delegates to DidMediaTypes`() {
        assertEquals(DidMediaTypes.DID, APPLICATION_DID_MEDIA_TYPE)
    }
}
