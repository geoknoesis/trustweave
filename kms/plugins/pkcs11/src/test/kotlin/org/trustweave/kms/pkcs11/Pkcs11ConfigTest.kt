package org.trustweave.kms.pkcs11

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

/**
 * Unit tests for [Pkcs11Config].
 *
 * **Note:** End-to-end testing of the PKCS#11 KMS requires SoftHSM2 in CI
 * (see `docs/architecture/eidas-qes-design.md` §9). That is intentionally out of scope for
 * these MVP unit tests, which cover only configuration-level invariants and SPI behavior.
 */
class Pkcs11ConfigTest {

    @Test
    fun `init rejects blank library path`() {
        val ex = assertThrows<IllegalArgumentException> {
            Pkcs11Config(libraryPath = "")
        }
        assert(ex.message!!.contains("libraryPath"))
    }

    @Test
    fun `init rejects whitespace-only library path`() {
        assertThrows<IllegalArgumentException> {
            Pkcs11Config(libraryPath = "   ")
        }
    }

    @Test
    fun `defaults are populated`() {
        val cfg = Pkcs11Config(libraryPath = "/usr/lib/softhsm/libsofthsm2.so")
        assertEquals(0, cfg.slot)
        assertEquals("TrustWeave-PKCS11", cfg.providerName)
        assertNull(cfg.pin)
        assertFalse(cfg.enableSoftDelete)
    }

    @Test
    fun `toString redacts PIN`() {
        val cfg = Pkcs11Config(
            libraryPath = "/lib/p11.so",
            pin = charArrayOf('1', '2', '3', '4'),
        )
        val s = cfg.toString()
        assertFalse(s.contains("1234"), "toString must not expose PIN; was: $s")
        assert(s.contains("***"))
    }

    @Test
    fun `equals uses contentEquals for pin`() {
        val a = Pkcs11Config(libraryPath = "/lib/p11.so", pin = charArrayOf('a', 'b'))
        val b = Pkcs11Config(libraryPath = "/lib/p11.so", pin = charArrayOf('a', 'b'))
        val c = Pkcs11Config(libraryPath = "/lib/p11.so", pin = charArrayOf('a', 'c'))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
