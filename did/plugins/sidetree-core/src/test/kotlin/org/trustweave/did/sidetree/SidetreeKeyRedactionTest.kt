package org.trustweave.did.sidetree

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SidetreeKeyRedactionTest {
    @Test
    fun `generated key pair toString does not print the private scalar`() {
        val pair = SidetreeP256KeyPair.generate()
        val d = pair.privateJwk["d"].toString()
        assertTrue(d.length > 10)
        assertFalse(d in pair.toString(), pair.toString())
        assertTrue(pair.publicJwk["x"].toString() in pair.toString())
    }

    @Test
    fun `stored key pair toString does not print private scalars`() {
        val a = SidetreeP256KeyPair.generate()
        val b = SidetreeP256KeyPair.generate()
        val text = SidetreeKeyPair(a.privateJwk, a.publicJwk, b.privateJwk, b.publicJwk).toString()
        assertFalse(a.privateJwk["d"].toString() in text)
        assertFalse(b.privateJwk["d"].toString() in text)
    }
}
