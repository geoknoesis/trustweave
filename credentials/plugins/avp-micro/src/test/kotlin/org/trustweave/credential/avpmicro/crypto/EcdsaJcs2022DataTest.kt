package org.trustweave.credential.avpmicro.crypto

import org.trustweave.credential.avpmicro.Vectors
import kotlin.test.Test
import kotlin.test.assertEquals

class EcdsaJcs2022DataTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun `payment authorization hashData matches the python harness`() {
        val data = EcdsaJcs2022.verifyData(Vectors.paymentAuthorization)
        assertEquals("c920b38304b3d39c9c85d71397d4f078404b30a6a4287382ce82f5a8d0f37fc5", hex(data.copyOfRange(0, 32)))
        assertEquals("03b45de6019c279a872cfb468373570a2f5f7751b740ee08dbd5ba83ac6afe34", hex(data.copyOfRange(32, 64)))
    }

    @Test
    fun `spending authorization credential hashData matches the python harness`() {
        val data = EcdsaJcs2022.verifyData(Vectors.spendingAuthorizationCredential)
        assertEquals("7926a7bc949c4d9055de69f9cbad715a3fa41ceae9967784ce52e86a28d27f77", hex(data.copyOfRange(0, 32)))
        assertEquals("59fd2b89848dc4411c0e4c557a6e06745b0c3434a77fdbf106ca287a43209c3e", hex(data.copyOfRange(32, 64)))
    }
}
