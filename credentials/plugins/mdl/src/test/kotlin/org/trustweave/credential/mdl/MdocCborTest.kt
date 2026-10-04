package org.trustweave.credential.mdl

import com.upokecenter.cbor.CBORObject
import org.trustweave.credential.mdl.engine.MdocCbor
import org.trustweave.credential.mdl.model.DeviceAuth
import org.trustweave.credential.mdl.model.DeviceKeyInfo
import org.trustweave.credential.mdl.model.IssuerSigned
import org.trustweave.credential.mdl.model.IssuerSignedItem
import org.trustweave.credential.mdl.model.MobileDocument
import org.trustweave.credential.mdl.model.MobileSecurityObject
import org.trustweave.credential.mdl.model.ValidityInfo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Direct tests of the CBOR layer: round trips, determinism and fail-loud decoding. */
class MdocCborTest {
    private val salt = ByteArray(16) { it.toByte() }

    private fun item(
        value: Any,
        id: Int = 0,
    ) = IssuerSignedItem(digestId = id, random = salt, elementIdentifier = "family_name", elementValue = value)

    private fun mso(deviceKey: ByteArray = CBORObject.NewOrderedMap().also { it["kty"] = CBORObject.FromObject(2) }.EncodeToBytes()) =
        MobileSecurityObject(
            valueDigests = mapOf("org.iso.18013.5.1" to mapOf(0 to ByteArray(32) { 1 }, 7 to ByteArray(32) { 2 })),
            deviceKeyInfo = DeviceKeyInfo(deviceKey),
            docType = "org.iso.18013.5.1.mDL",
            validityInfo =
                ValidityInfo(
                    signed = Instant.parse("2025-01-01T00:00:00Z"),
                    validFrom = Instant.parse("2025-01-01T00:00:00Z"),
                    validUntil = Instant.parse("2026-01-01T00:00:00Z"),
                    expectedUpdate = Instant.parse("2025-06-01T00:00:00Z"),
                ),
        )

    // ------------------------------------------------------------------ items

    @Test
    fun `item values of every supported type survive a round trip`() {
        for (value in listOf<Any>("Smith", 42L, true, false, byteArrayOf(1, 2, 3), 1.5)) {
            val decoded = MdocCbor.decodeIssuerSignedItem(MdocCbor.encodeIssuerSignedItem(item(value)))
            assertEquals(item(value), decoded, "value $value")
        }
    }

    @Test
    fun `byte array element values compare by content`() {
        assertEquals(item(byteArrayOf(9, 9)), item(byteArrayOf(9, 9)))
        assertEquals(item(byteArrayOf(9, 9)).hashCode(), item(byteArrayOf(9, 9)).hashCode())
        assertNotEquals(item(byteArrayOf(9, 9)), item(byteArrayOf(9, 8)))
    }

    @Test
    fun `item encoding is deterministic, so its digest is stable`() {
        val a = MdocCbor.encodeIssuerSignedItem(item("x"))
        val b = MdocCbor.encodeIssuerSignedItem(item("x"))
        assertContentEquals(a, b)
        assertContentEquals(MdocCbor.digestItem(a), MdocCbor.digestItem(b))
        assertEquals(32, MdocCbor.digestItem(a).size)
        assertEquals(64, MdocCbor.digestItem(a, "SHA-512").size)
        assertNotEquals(MdocCbor.digestItem(a).toList(), MdocCbor.digestItem(MdocCbor.encodeIssuerSignedItem(item("y"))).toList())
    }

    @Test
    fun `salts are 16 random bytes`() {
        val salts = (1..50).map { MdocCbor.generateSalt() }
        assertTrue(salts.all { it.size == 16 })
        assertEquals(salts.size, salts.map { it.toList() }.toSet().size)
    }

    // ------------------------------------------------------------------ MSO

    @Test
    fun `mso round trips digests, doc type and validity`() {
        val original = mso()
        val decoded = MdocCbor.decodeMso(MdocCbor.encodeMso(original))
        assertEquals("1.0", decoded.version)
        assertEquals("SHA-256", decoded.digestAlgorithm)
        assertEquals(original.docType, decoded.docType)
        assertEquals(original.validityInfo, decoded.validityInfo)
        assertEquals(setOf(0, 7), decoded.valueDigests.getValue("org.iso.18013.5.1").keys)
        assertContentEquals(ByteArray(32) { 2 }, decoded.valueDigests.getValue("org.iso.18013.5.1").getValue(7))
        assertContentEquals(original.deviceKeyInfo.deviceKey, decoded.deviceKeyInfo.deviceKey)
    }

    @Test
    fun `an mso without expectedUpdate decodes it as absent`() {
        val noUpdate = mso().let { it.copy(validityInfo = it.validityInfo.copy(expectedUpdate = null)) }
        assertEquals(null, MdocCbor.decodeMso(MdocCbor.encodeMso(noUpdate)).validityInfo.expectedUpdate)
    }

    // ------------------------------------------------------------------ documents

    private fun coseSign1Placeholder(): ByteArray =
        CBORObject
            .NewArray()
            .also {
                it.Add(CBORObject.FromObject(byteArrayOf(0xA1.toByte())))
                it.Add(CBORObject.NewOrderedMap())
                it.Add(CBORObject.FromObject(byteArrayOf(1, 2)))
                it.Add(CBORObject.FromObject(byteArrayOf(3, 4)))
            }.EncodeToBytes()

    @Test
    fun `a document keeps its namespaces, items and issuer auth`() {
        val doc =
            MobileDocument(
                docType = "org.iso.18013.5.1.mDL",
                issuerSigned =
                    IssuerSigned(
                        nameSpaces = mapOf("org.iso.18013.5.1" to listOf(item("Smith", 0), item("Jones", 1))),
                        issuerAuth = coseSign1Placeholder(),
                    ),
            )
        val decoded = MdocCbor.decodeMobileDocument(MdocCbor.encodeMobileDocument(doc))
        assertEquals(doc.docType, decoded.docType)
        assertEquals(doc.issuerSigned, decoded.issuerSigned)
        assertEquals(null, decoded.deviceSigned)
    }

    @Test
    fun `device auth with absent parts is equal to itself`() {
        assertEquals(DeviceAuth(), DeviceAuth())
        assertEquals(DeviceAuth(deviceMac = byteArrayOf(1)), DeviceAuth(deviceMac = byteArrayOf(1)))
        assertNotEquals(DeviceAuth(deviceMac = byteArrayOf(1)), DeviceAuth(deviceSignature = byteArrayOf(1)))
        assertEquals(DeviceAuth().hashCode(), DeviceAuth().hashCode())
    }

    // ------------------------------------------------------------------ fail loud

    private fun assertMalformed(block: () -> Unit) {
        val e = assertFailsWith<MdocException> { block() }
        assertEquals("MALFORMED_MDOC", e.code)
    }

    @Test
    fun `garbage and truncated input is a typed error`() {
        assertMalformed { MdocCbor.decodeIssuerSignedItem(byteArrayOf(0x1F, 0x00)) }
        assertMalformed { MdocCbor.decodeMso(MdocCbor.encodeMso(mso()).copyOf(10)) }
        assertMalformed { MdocCbor.decodeMobileDocument(ByteArray(0)) }
    }

    @Test
    fun `a missing required field is named, not a null pointer`() {
        val noDigest = CBORObject.NewOrderedMap().also { it["random"] = CBORObject.FromObject(salt) }.EncodeToBytes()
        val e = assertFailsWith<MdocException> { MdocCbor.decodeIssuerSignedItem(noDigest) }
        assertTrue(e.message.contains("digestID"), e.message)

        val msoMap = CBORObject.DecodeFromBytes(MdocCbor.encodeMso(mso()))
        msoMap.Remove(CBORObject.FromObject("validityInfo"))
        val msoError = assertFailsWith<MdocException> { MdocCbor.decodeMso(msoMap.EncodeToBytes()) }
        assertTrue(msoError.message.contains("validityInfo"), msoError.message)
    }

    @Test
    fun `a wrong type is a typed error`() {
        val wrong =
            CBORObject.NewOrderedMap().also {
                it["digestID"] = CBORObject.FromObject("not a number")
                it["random"] = CBORObject.FromObject(salt)
                it["elementIdentifier"] = CBORObject.FromObject("x")
                it["elementValue"] = CBORObject.FromObject("v")
            }
        assertMalformed { MdocCbor.decodeIssuerSignedItem(wrong.EncodeToBytes()) }
        assertMalformed { MdocCbor.decodeIssuerSignedItem(CBORObject.FromObject(5).EncodeToBytes()) }
    }

    @Test
    fun `a malformed validity timestamp is a typed error`() {
        val msoMap = CBORObject.DecodeFromBytes(MdocCbor.encodeMso(mso()))
        msoMap["validityInfo"]["validUntil"] = CBORObject.FromObject("yesterday")
        assertMalformed { MdocCbor.decodeMso(msoMap.EncodeToBytes()) }
    }
}
