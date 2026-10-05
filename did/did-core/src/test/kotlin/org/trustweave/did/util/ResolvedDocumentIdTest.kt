package org.trustweave.did.util

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResolvedDocumentIdTest {
    @Test
    fun `equal ids match`() = assertNull(ResolvedDocumentId.mismatchReason("did:a:b", "did:a:b"))

    @Test
    fun `different ids mismatch`() {
        assertNotNull(ResolvedDocumentId.mismatchReason("did:a:b", "did:a:c"))
    }

    private val suffix = "Ei" + "A".repeat(44)

    @Test
    fun `canonical form is only accepted when allowed`() {
        assertNotNull(ResolvedDocumentId.mismatchReason("did:ion:$suffix:init", "did:ion:$suffix"))
        assertNull(ResolvedDocumentId.mismatchReason("did:ion:$suffix:init", "did:ion:$suffix", allowCanonicalOfLongForm = true))
        assertNotNull(ResolvedDocumentId.mismatchReason("did:ion:$suffix:init", "did:ion:t", allowCanonicalOfLongForm = true))
    }

    @Test
    fun `a network or anchor prefixed short form is not mistaken for a long form`() {
        // did:ion:test:<suffix> has four segments too, but its "canonical form" is not did:ion:test.
        assertNotNull(ResolvedDocumentId.mismatchReason("did:ion:test:$suffix", "did:ion:test", allowCanonicalOfLongForm = true))
        assertNotNull(ResolvedDocumentId.mismatchReason("did:orb:uAnchor:$suffix", "did:orb:uAnchor", allowCanonicalOfLongForm = true))
    }

    @Test
    fun `case differences are mismatches`() {
        assertNotNull(ResolvedDocumentId.mismatchReason("did:a:b", "did:a:B"))
    }
}
