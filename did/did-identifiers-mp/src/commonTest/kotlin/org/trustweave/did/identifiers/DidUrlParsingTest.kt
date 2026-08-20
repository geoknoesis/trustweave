package org.trustweave.did.identifiers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidUrlParsingTest {

    @Test
    fun `a bare DID has no path query or fragment`() {
        val url = DidUrl("did:example:123")
        assertEquals(Did("did:example:123"), url.did)
        assertNull(url.path)
        assertNull(url.query)
        assertNull(url.fragment)
    }

    @Test
    fun `path query and fragment are separated in RFC 3986 order`() {
        val url = DidUrl("did:example:123/a/b?x=1#frag")
        assertEquals("a/b", url.path)
        assertEquals("x=1", url.query)
        assertEquals("frag", url.fragment)
    }

    @Test
    fun `a query without a path is parsed`() {
        val url = DidUrl("did:example:123?versionId=3")
        assertNull(url.path)
        assertEquals("versionId=3", url.query)
        assertEquals("3", url.versionId)
    }

    @Test
    fun `spec DID parameters are exposed by name`() {
        val url = DidUrl("did:example:123?service=files&relativeRef=%2Fresume.pdf")
        assertEquals("files", url.service)
        assertEquals("/resume.pdf", url.relativeRef)
    }

    @Test
    fun `serviceType and versionTime are exposed`() {
        val url = DidUrl("did:example:123?serviceType=LinkedDomains&versionTime=2021-05-10T17:00:00Z")
        assertEquals("LinkedDomains", url.serviceType)
        assertEquals("2021-05-10T17:00:00Z", url.versionTime)
    }

    @Test
    fun `percent-encoded unreserved characters are decoded in parameter values`() {
        assertEquals("a b", DidUrl("did:example:123?x=a%20b").parameters["x"])
    }

    @Test
    fun `multi-byte percent-encoded characters decode as UTF-8`() {
        assertEquals("café", DidUrl("did:example:123?x=caf%C3%A9").parameters["x"])
    }

    @Test
    fun `duplicate parameters are flagged`() {
        assertTrue(DidUrl("did:example:123?service=files&service=agent").hasDuplicateParameters)
        assertFalse(DidUrl("did:example:123?service=files").hasDuplicateParameters)
    }

    @Test
    fun `the first occurrence wins for a duplicate parameter's value`() {
        val url = DidUrl("did:example:123?service=files&service=agent")
        assertEquals("files", url.service)
        assertEquals("files", url.parameters["service"])
    }

    @Test
    fun `a fragment only DID URL is parsed`() {
        val url = DidUrl("did:example:123#keys-1")
        assertEquals("keys-1", url.fragment)
        assertNull(url.query)
        assertNull(url.path)
    }
}
