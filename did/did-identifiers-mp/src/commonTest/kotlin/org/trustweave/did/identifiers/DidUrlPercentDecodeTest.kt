package org.trustweave.did.identifiers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DidUrlPercentDecodeTest {
    private fun params(query: String) = DidUrl("did:example:123?$query").parameters

    @Test
    fun `a signed escape is not a hex escape and the parameter is rejected`() {
        assertNull(params("x=%+3")["x"])
        assertNull(params("x=%-1")["x"])
        assertNull(params("x=%+3&y=ok")["x"])
        assertEquals("ok", params("x=%+3&y=ok")["y"])
    }

    @Test
    fun `a truncated or non-hex escape rejects the parameter`() {
        assertNull(params("x=%4")["x"])
        assertNull(params("x=%zz")["x"])
    }

    @Test
    fun `invalid UTF-8 is rejected instead of being replaced`() {
        assertFalse("x" in params("x=%FF"))
        assertFalse("x" in params("x=%C3"))
        assertFalse("x" in params("x=%C0%AF"))
    }

    @Test
    fun `valid escapes still decode`() {
        assertEquals("café", params("x=caf%C3%A9")["x"])
        assertEquals("a b", params("x=a%20b")["x"])
        assertEquals("/resume.pdf", params("x=%2Fresume.pdf")["x"])
    }
}
