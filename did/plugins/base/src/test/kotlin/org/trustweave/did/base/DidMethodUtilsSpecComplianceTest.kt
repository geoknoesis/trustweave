package org.trustweave.did.base

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Spec-compliance tests for [DidMethodUtils]:
 * - did:web percent-decoding and port encoding (W3C did:web)
 * - multicodec table including x25519-pub (0xec)
 * - SEC1 EC point compression/decompression required by the did:key multicodec entries
 */
class DidMethodUtilsSpecComplianceTest {

    @Nested
    inner class PercentDecoding {

        @Test
        fun `plain segment is returned unchanged`() {
            assertEquals("example.com", DidMethodUtils.percentDecode("example.com"))
        }

        @Test
        fun `percent-encoded port separator is decoded`() {
            assertEquals("example.com:8080", DidMethodUtils.percentDecode("example.com%3A8080"))
        }

        @Test
        fun `lowercase hex digits are decoded`() {
            assertEquals("example.com:3000", DidMethodUtils.percentDecode("example.com%3a3000"))
        }

        @Test
        fun `malformed percent-encoding is rejected`() {
            assertThrows<IllegalArgumentException> { DidMethodUtils.percentDecode("example.com%3") }
            assertThrows<IllegalArgumentException> { DidMethodUtils.percentDecode("example.com%zz80") }
        }

        @Test
        fun `signed hex digits are rejected`() {
            // toIntOrNull(16) accepts a sign — "%+3" must NOT decode as 0x03
            assertThrows<IllegalArgumentException> { DidMethodUtils.percentDecode("%+3") }
            assertThrows<IllegalArgumentException> { DidMethodUtils.percentDecode("%-1") }
            assertThrows<IllegalArgumentException> { DidMethodUtils.percentDecode("example.com%+3A") }
        }
    }

    @Nested
    inner class WebDidBuilding {

        @Test
        fun `bare domain produces plain did`() {
            assertEquals("did:web:example.com", DidMethodUtils.buildWebDid("example.com"))
        }

        @Test
        fun `domain with port percent-encodes the colon`() {
            assertEquals("did:web:example.com%3A8080", DidMethodUtils.buildWebDid("example.com:8080"))
        }

        @Test
        fun `domain with port and path`() {
            assertEquals(
                "did:web:example.com%3A8080:user:alice",
                DidMethodUtils.buildWebDid("example.com:8080", "user:alice")
            )
        }
    }

    @Nested
    inner class Multicodec {

        @Test
        fun `x25519 prefix is varint 0xec 0x01`() {
            assertContentEquals(
                byteArrayOf(0xec.toByte(), 0x01),
                DidMethodUtils.getMulticodecPrefix("X25519")
            )
        }

        @Test
        fun `x25519 prefixed key is parsed`() {
            val keyBytes = ByteArray(32) { (it + 1).toByte() }
            val parsed = DidMethodUtils.parseMulticodecKey(byteArrayOf(0xec.toByte(), 0x01) + keyBytes)
            assertEquals("X25519", parsed?.first)
            assertContentEquals(keyBytes, parsed?.second)
        }

        @Test
        fun `unknown prefix returns null`() {
            assertNull(DidMethodUtils.parseMulticodecKey(byteArrayOf(0x55, 0x55, 0x01)))
        }

        @Test
        fun `x25519 maps to key agreement verification method type`() {
            assertEquals(
                "X25519KeyAgreementKey2020",
                DidMethodUtils.algorithmToVerificationMethodType("X25519")
            )
        }
    }

    @Nested
    inner class EcPointCompression {

        // Standard generator points (SEC 2 / FIPS 186-4) used as known-good curve points.
        private val secp256k1Gx = "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798"
        private val secp256k1Gy = "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8" // even
        private val p256Gx = "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296"
        private val p256Gy = "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5" // odd
        private val p384Gx =
            "AA87CA22BE8B05378EB1C71EF320AD746E1D3B628BA79B9859F741E082542A385502F25DBF55296C3A545E3872760AB7"
        private val p384Gy =
            "3617DE4A96262C6F5D9E98BF9292DC29F8F41DBD289A147CE9DA3113B5F0B8C00A60B1CE1D7E819D7A431D7C90EA0E5F" // odd
        private val p521Gx =
            "00C6858E06B70404E9CD9E3ECB662395B4429C648139053FB521F828AF606B4D3DBAA14B5E77EFE759" +
                "28FE1DC127A2FFA8DE3348B3C1856A429BF97E7E31C2E5BD66"
        private val p521Gy =
            "011839296A789A3BC0045C8A5FB42C7D1BD998F54449579B446817AFBD17273E662C97EE72995EF426" +
                "40C550B9013FAD0761353C7086A272C24088BE94769FD16650" // even

        private fun hex(s: String): ByteArray =
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        private fun uncompressed(x: String, y: String): ByteArray =
            byteArrayOf(0x04) + hex(x) + hex(y)

        private fun assertRoundTrip(algorithm: String, x: String, y: String, yIsOdd: Boolean) {
            val point = uncompressed(x, y)
            val compressed = DidMethodUtils.compressEcPublicKey(algorithm, point)

            val expectedPrefix: Byte = if (yIsOdd) 0x03 else 0x02
            assertEquals(expectedPrefix, compressed[0], "compressed prefix for $algorithm")
            assertContentEquals(hex(x), compressed.copyOfRange(1, compressed.size))

            val decompressed = DidMethodUtils.decompressEcPublicKey(algorithm, compressed)
            assertContentEquals(point, decompressed, "decompress(compress(point)) for $algorithm")
        }

        @Test
        fun `secp256k1 compress and decompress round-trip`() {
            assertRoundTrip("secp256k1", secp256k1Gx, secp256k1Gy, yIsOdd = false)
        }

        @Test
        fun `P-256 compress and decompress round-trip`() {
            assertRoundTrip("P-256", p256Gx, p256Gy, yIsOdd = true)
        }

        @Test
        fun `P-384 compress and decompress round-trip`() {
            assertRoundTrip("P-384", p384Gx, p384Gy, yIsOdd = true)
        }

        @Test
        fun `P-521 compress and decompress round-trip`() {
            assertRoundTrip("P-521", p521Gx, p521Gy, yIsOdd = false)
        }

        @Test
        fun `already compressed point passes through compress`() {
            val compressed = byteArrayOf(0x02) + hex(secp256k1Gx)
            assertContentEquals(
                compressed,
                DidMethodUtils.compressEcPublicKey("secp256k1", compressed)
            )
        }

        @Test
        fun `legacy uncompressed point passes through decompress`() {
            val point = uncompressed(secp256k1Gx, secp256k1Gy)
            assertContentEquals(point, DidMethodUtils.decompressEcPublicKey("secp256k1", point))
        }

        @Test
        fun `x not on curve is rejected`() {
            // x = 5 is not the x-coordinate of any secp256k1 point (x^3 + 7 is a non-residue)
            val badK1 = byteArrayOf(0x02) + ByteArray(31) + byteArrayOf(0x05)
            assertThrows<IllegalArgumentException> {
                DidMethodUtils.decompressEcPublicKey("secp256k1", badK1)
            }
            // x = 1 is not the x-coordinate of any P-256 point
            val badP256 = byteArrayOf(0x02) + ByteArray(31) + byteArrayOf(0x01)
            assertThrows<IllegalArgumentException> {
                DidMethodUtils.decompressEcPublicKey("P-256", badP256)
            }
        }

        @Test
        fun `invalid length is rejected`() {
            assertThrows<IllegalArgumentException> {
                DidMethodUtils.compressEcPublicKey("P-256", ByteArray(10))
            }
            assertThrows<IllegalArgumentException> {
                DidMethodUtils.decompressEcPublicKey("P-256", ByteArray(10))
            }
        }

        @Test
        fun `non-EC algorithm is rejected`() {
            assertThrows<IllegalArgumentException> {
                DidMethodUtils.compressEcPublicKey("Ed25519", ByteArray(33))
            }
            assertNull(DidMethodUtils.ecCoordinateSize("Ed25519"))
            assertEquals(32, DidMethodUtils.ecCoordinateSize("secp256k1"))
            assertEquals(66, DidMethodUtils.ecCoordinateSize("P-521"))
        }
    }

    @Nested
    inner class ResolutionMetadata {

        @Test
        fun `createSuccessResolutionResult carries deactivated flag`() {
            val document = DidMethodUtils.buildDidDocument(
                did = "did:test:123",
                verificationMethod = listOf(
                    DidMethodUtils.createVerificationMethod(
                        did = "did:test:123",
                        keyHandle = org.trustweave.kms.KeyHandle(
                            id = org.trustweave.core.identifiers.KeyId("key-1"),
                            algorithm = "Ed25519",
                            publicKeyMultibase = "z6Mk"
                        ),
                        algorithm = "Ed25519"
                    )
                )
            )

            val active = DidMethodUtils.createSuccessResolutionResult(document, "test")
                as org.trustweave.did.resolver.DidResolutionResult.Success
            assertEquals(false, active.documentMetadata.deactivated)

            val deactivated = DidMethodUtils.createSuccessResolutionResult(
                document, "test", deactivated = true
            ) as org.trustweave.did.resolver.DidResolutionResult.Success
            assertTrue(deactivated.documentMetadata.deactivated)
        }
    }
}
