package org.trustweave.credential.avpmicro.crypto

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class P256DidKeyTest {
    private val vm =
        "did:key:zDnaew8NDU8VgvxWpWWxBeLWaVbGNEuXYyRFk2uLMjCdhxkSU#" +
        "zDnaew8NDU8VgvxWpWWxBeLWaVbGNEuXYyRFk2uLMjCdhxkSU"

    @Test
    fun `decodes verificationMethod to the expected P-256 public point`() {
        val key = P256DidKey.publicKeyFrom(vm)
        val expectedX = BigInteger("c81809c0db9c2cd636eae025100cfdba1bcb33b8d3ca487c475ec7f55759b629", 16)
        assertEquals(expectedX, key.w.affineX)
    }
}
