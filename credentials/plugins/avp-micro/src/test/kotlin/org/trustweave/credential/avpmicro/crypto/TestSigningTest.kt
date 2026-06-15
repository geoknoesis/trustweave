package org.trustweave.credential.avpmicro.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class TestSigningTest {
    @Test
    fun `seed keys reproduce the harness did keys`() {
        assertEquals(
            "did:key:zDnaew8NDU8VgvxWpWWxBeLWaVbGNEuXYyRFk2uLMjCdhxkSU",
            TestSigning.didKey("agent-buyer-01"),
        )
        assertEquals(
            "did:key:zDnaenNXPt8JM5YYhrjp23T2ZsgGjyEhVVSC7dhFjbQwdrxEN",
            TestSigning.didKey("service-tool-api"),
        )
    }
}
