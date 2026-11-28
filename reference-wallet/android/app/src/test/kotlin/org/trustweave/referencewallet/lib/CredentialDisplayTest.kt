package org.trustweave.referencewallet.lib

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for Android consumer display, honest badges, and claim-value extraction. */
class CredentialDisplayTest {
    private val testJson = Json { encodeDefaults = true }

    @Test
    fun `formatIssuerLabel never claims arbitrary did key is verified`() {
        val did = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH"
        val label = CredentialDisplay.formatIssuerLabel(did)
        assertFalse(label.contains("verified", ignoreCase = true))
        assertEquals("Issuer · z6MkpT…vktH", label)
        assertEquals("example.edu", CredentialDisplay.formatIssuerLabel("did:web:example.edu"))
        assertEquals("Acme University", CredentialDisplay.formatIssuerLabel(did, "Acme University"))
    }

    @Test
    fun `humanClaimName formats camelCase and snake_case`() {
        assertEquals("Full Name", CredentialDisplay.humanClaimName("fullName"))
        assertEquals("Graduation Date", CredentialDisplay.humanClaimName("graduation_date"))
    }

    @Test
    fun `extractShareableClaimDetails surfaces actual claim values and honest expiry badges`() {
        val now = 1_700_000_000L
        val dName = SdJwt.createObjectDisclosure("fullName", JsonPrimitive("Alice Johnson"))
        val dYear = SdJwt.createObjectDisclosure("graduationYear", JsonPrimitive(2026))
        val header = Crypto.b64uEncodeString("""{"alg":"EdDSA","typ":"vc+sd-jwt"}""")
        val payloadObj = buildJsonObject {
            put("iss", "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH")
            put("sub", "did:key:z6MkHolder")
            put("vct", "BachelorOfScience")
            put("exp", now + 365L * 24L * 3600L)
            put("_sd", buildJsonArray { add(JsonPrimitive(dName.hash)); add(JsonPrimitive(dYear.hash)) })
        }
        val payload = Crypto.b64uEncodeString(payloadObj.toString())
        val compact = "$header.$payload.sig~${dName.raw}~${dYear.raw}~"

        val cred = Storage.StoredCredential(
            id = "c1",
            format = "vc+sd-jwt",
            credential = compact,
            receivedAt = "2026-01-01T00:00:00Z",
            issuerDid = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH",
            subjectDid = "did:key:z6MkHolder",
            type = listOf("BachelorOfScience"),
            previewTitle = "Bachelor of Science",
            previewSubtitle = "Alice Johnson",
            selectivelyDisclosable = listOf("fullName", "graduationYear"),
            trustSource = "backend",
        )

        val details = CredentialDisplay.extractShareableClaimDetails(cred)
        assertEquals(2, details.size)
        assertEquals("Full Name", details[0].label)
        assertEquals("Alice Johnson", details[0].value)
        assertEquals("Graduation Year", details[1].label)
        assertEquals("2026", details[1].value)

        assertEquals("Trusted issuer", CredentialDisplay.statusBadge(cred, now).label)
        assertEquals("Confirmed by you", CredentialDisplay.statusBadge(cred.copy(trustSource = "confirmed"), now).label)
        assertEquals("Expiring soon", CredentialDisplay.statusBadge(cred, now + 360L * 24L * 3600L).label)
        assertEquals("Expired", CredentialDisplay.statusBadge(cred, now + 400L * 24L * 3600L).label)
    }

    @Test
    fun `extractPortraitBytes decodes base64 data URI and returns null when absent`() {
        val sampleBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        val dataUri = "data:image/png;base64,${java.util.Base64.getEncoder().encodeToString(sampleBytes)}"
        val dPortrait = SdJwt.createObjectDisclosure("portrait", JsonPrimitive(dataUri))
        val header = Crypto.b64uEncodeString("""{"alg":"EdDSA","typ":"vc+sd-jwt"}""")
        val payloadObj = buildJsonObject {
            put("iss", "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH")
            put("sub", "did:key:z6MkHolder")
            put("vct", "EmployeeBadge")
            put("_sd", buildJsonArray { add(JsonPrimitive(dPortrait.hash)) })
        }
        val compact = "$header.${Crypto.b64uEncodeString(payloadObj.toString())}.sig~${dPortrait.raw}~"
        val cred = Storage.StoredCredential(
            id = "c2",
            format = "vc+sd-jwt",
            credential = compact,
            receivedAt = "2026-01-01T00:00:00Z",
            issuerDid = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH",
            subjectDid = "did:key:z6MkHolder",
            type = listOf("EmployeeBadge"),
            previewTitle = "Employee Badge",
        )
        val decoded = CredentialDisplay.extractPortraitBytes(cred)
        assertNotNull(decoded)
        assertArrayEquals(sampleBytes, decoded)

        val noPortrait = cred.copy(credential = "$header.${Crypto.b64uEncodeString("""{"vct":"Plain"}""")}.sig~")
        assertNull(CredentialDisplay.extractPortraitBytes(noPortrait))
    }

    @Test
    fun `NdefParser decodes RTD-URI prefixes and RTD-Text records`() {
        val httpsBytes = byteArrayOf(0x04) + "verifier.example/present".toByteArray(Charsets.UTF_8)
        assertEquals("https://verifier.example/present", NdefParser.decodeUriPayload(httpsBytes))

        val customScheme = byteArrayOf(0x00) + "openid4vp://?request_uri=https%3A%2F%2Fv.example".toByteArray(Charsets.UTF_8)
        assertEquals("openid4vp://?request_uri=https%3A%2F%2Fv.example", NdefParser.decodeUriPayload(customScheme))

        val textRecord = byteArrayOf(0x02) + "enopenid-credential-offer://?credential_offer_uri=https%3A%2F%2Fi.example".toByteArray(Charsets.UTF_8)
        assertEquals(
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fi.example",
            NdefParser.decodeTextPayload(textRecord),
        )
        assertNull(NdefParser.decodeUriPayload(byteArrayOf()))
        assertNull(NdefParser.decodeTextPayload(byteArrayOf(0x05, 0x65, 0x6E)))
    }

    @Test
    fun `CredentialBackupPayload never marks private keys included`() {
        val backup = Wallet.CredentialBackupPayload(
            exportedAt = "2026-01-01T00:00:00Z",
            holderDid = "did:key:z6MkHolder",
            containsPrivateKeys = false,
            credentials = listOf(Wallet.BackupItem(format = "vc+sd-jwt", credential = "a.b.c~")),
        )
        val encoded = testJson.encodeToString(Wallet.CredentialBackupPayload.serializer(), backup)
        assertTrue(encoded.contains("\"containsPrivateKeys\":false"))
        assertFalse(encoded.contains("softwarePrivateKey", ignoreCase = true))
        val parsed = testJson.decodeFromString(Wallet.CredentialBackupPayload.serializer(), encoded)
        assertEquals("trustweave-credential-backup-v1", parsed.schema)
        assertFalse(parsed.containsPrivateKeys)
        assertEquals(1, parsed.credentials.size)
    }
}
