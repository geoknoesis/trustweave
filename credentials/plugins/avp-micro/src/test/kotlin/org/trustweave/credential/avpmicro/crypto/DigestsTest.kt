package org.trustweave.credential.avpmicro.crypto

import org.trustweave.credential.avpmicro.Vectors
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestsTest {
    @Test
    fun `jcsDigest of the quote matches the authorization quoteDigest`() {
        assertEquals(
            "sha-256:EuScAr2qixUd_K3KSPlSR0HAlToOR7tuPZODlQZMbFg",
            Digests.jcsDigest(Vectors.paymentQuote),
        )
    }
}
