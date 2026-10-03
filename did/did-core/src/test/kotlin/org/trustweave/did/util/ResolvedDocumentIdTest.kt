package org.trustweave.did.util

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResolvedDocumentIdTest {
    @Test
    fun `equal ids match`() = assertNull(ResolvedDocumentId.mismatchReason("did:a:b", "did:a:b"))

    @Test
    fun `different ids mismatch`() = assertNotNull(ResolvedDocumentId.mismatchReason("did:a:b", "did:a:c"))

    @Test
    fun `canonical form is only accepted when allowed`() {
        assertNotNull(ResolvedDocumentId.mismatchReason("did:ion:s:init", "did:ion:s"))
        assertNull(ResolvedDocumentId.mismatchReason("did:ion:s:init", "did:ion:s", allowCanonicalOfLongForm = true))
        assertNotNull(ResolvedDocumentId.mismatchReason("did:ion:s:init", "did:ion:t", allowCanonicalOfLongForm = true))
    }

    @Test
    fun `case differences are mismatches`() = assertNotNull(ResolvedDocumentId.mismatchReason("did:a:b", "did:a:B"))
}
