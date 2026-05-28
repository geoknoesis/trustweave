package org.trustweave.credential.eudiw

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EuPidCredentialTest {

    // -------------------------------------------------------------------------
    // toClaims()
    // -------------------------------------------------------------------------

    @Test
    fun `toClaims contains all mandatory required claims`() {
        val pid = minimalPid()
        val claims = pid.toClaims()

        assertTrue(EudiwConstants.CLAIM_FAMILY_NAME in claims, "family_name must be present")
        assertTrue(EudiwConstants.CLAIM_GIVEN_NAME in claims, "given_name must be present")
        assertTrue(EudiwConstants.CLAIM_BIRTH_DATE in claims, "birth_date must be present")
        assertTrue(EudiwConstants.CLAIM_ISSUING_AUTHORITY in claims, "issuing_authority must be present")
        assertTrue(EudiwConstants.CLAIM_ISSUING_COUNTRY in claims, "issuing_country must be present")
        assertTrue(EudiwConstants.CLAIM_ISSUANCE_DATE in claims, "issuance_date must be present")
        assertTrue(EudiwConstants.CLAIM_EXPIRY_DATE in claims, "expiry_date must be present")
    }

    @Test
    fun `toClaims maps field values correctly`() {
        val pid = minimalPid()
        val claims = pid.toClaims()

        assertEquals("Smith", claims[EudiwConstants.CLAIM_FAMILY_NAME])
        assertEquals("Alice", claims[EudiwConstants.CLAIM_GIVEN_NAME])
        assertEquals("1990-06-15", claims[EudiwConstants.CLAIM_BIRTH_DATE])
        assertEquals("Ministry of Interior", claims[EudiwConstants.CLAIM_ISSUING_AUTHORITY])
        assertEquals("DE", claims[EudiwConstants.CLAIM_ISSUING_COUNTRY])
        assertEquals("2024-01-01", claims[EudiwConstants.CLAIM_ISSUANCE_DATE])
        assertEquals("2029-01-01", claims[EudiwConstants.CLAIM_EXPIRY_DATE])
    }

    @Test
    fun `toClaims omits null optional fields`() {
        val pid = minimalPid()
        val claims = pid.toClaims()

        assertFalse(EudiwConstants.CLAIM_AGE_OVER_18 in claims)
        assertFalse(EudiwConstants.CLAIM_AGE_IN_YEARS in claims)
        assertFalse(EudiwConstants.CLAIM_AGE_BIRTH_YEAR in claims)
        assertFalse(EudiwConstants.CLAIM_UNIQUE_ID in claims)
        assertFalse(EudiwConstants.CLAIM_DOCUMENT_NUMBER in claims)
        assertFalse(EudiwConstants.CLAIM_NATIONALITY in claims)
        assertFalse(EudiwConstants.CLAIM_GENDER in claims)
        assertFalse(EudiwConstants.CLAIM_BIRTH_PLACE in claims)
        assertFalse(EudiwConstants.CLAIM_BIRTH_COUNTRY in claims)
        assertFalse(EudiwConstants.CLAIM_RESIDENT_ADDRESS in claims)
        assertFalse(EudiwConstants.CLAIM_RESIDENT_COUNTRY in claims)
        assertFalse(EudiwConstants.CLAIM_RESIDENT_CITY in claims)
        assertFalse(EudiwConstants.CLAIM_RESIDENT_POSTAL_CODE in claims)
    }

    @Test
    fun `toClaims includes optional fields when set`() {
        val pid = minimalPid().copy(
            ageOver18 = true,
            ageInYears = 34,
            ageBirthYear = 1990,
            uniqueId = "DE-12345",
            documentNumber = "DOC-001",
            nationality = "DE",
            gender = 2,
            birthPlace = "Berlin",
            birthCountry = "DE",
            residentAddress = "Unter den Linden 1, 10117 Berlin",
            residentCountry = "DE",
            residentCity = "Berlin",
            residentPostalCode = "10117",
        )
        val claims = pid.toClaims()

        assertEquals(true, claims[EudiwConstants.CLAIM_AGE_OVER_18])
        assertEquals(34, claims[EudiwConstants.CLAIM_AGE_IN_YEARS])
        assertEquals(1990, claims[EudiwConstants.CLAIM_AGE_BIRTH_YEAR])
        assertEquals("DE-12345", claims[EudiwConstants.CLAIM_UNIQUE_ID])
        assertEquals("DOC-001", claims[EudiwConstants.CLAIM_DOCUMENT_NUMBER])
        assertEquals("DE", claims[EudiwConstants.CLAIM_NATIONALITY])
        assertEquals(2, claims[EudiwConstants.CLAIM_GENDER])
        assertEquals("Berlin", claims[EudiwConstants.CLAIM_BIRTH_PLACE])
        assertEquals("DE", claims[EudiwConstants.CLAIM_BIRTH_COUNTRY])
        assertEquals("Unter den Linden 1, 10117 Berlin", claims[EudiwConstants.CLAIM_RESIDENT_ADDRESS])
        assertEquals("DE", claims[EudiwConstants.CLAIM_RESIDENT_COUNTRY])
        assertEquals("Berlin", claims[EudiwConstants.CLAIM_RESIDENT_CITY])
        assertEquals("10117", claims[EudiwConstants.CLAIM_RESIDENT_POSTAL_CODE])
    }

    // -------------------------------------------------------------------------
    // EuPidIssuanceProfile.validateClaims()
    // -------------------------------------------------------------------------

    @Test
    fun `validateClaims succeeds when all required claims are present`() {
        val claims = minimalPid().toClaims()
        val result = EuPidIssuanceProfile.validateClaims(claims)

        assertTrue(result.valid)
        assertTrue(result.missingClaims.isEmpty())
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `validateClaims fails when family_name is missing`() {
        val claims = minimalPid().toClaims().toMutableMap().apply {
            remove(EudiwConstants.CLAIM_FAMILY_NAME)
        }
        val result = EuPidIssuanceProfile.validateClaims(claims)

        assertFalse(result.valid)
        assertTrue(EudiwConstants.CLAIM_FAMILY_NAME in result.missingClaims)
        assertTrue(result.violations.isNotEmpty())
    }

    @Test
    fun `validateClaims fails when multiple mandatory claims are missing`() {
        val claims = emptyMap<String, Any>()
        val result = EuPidIssuanceProfile.validateClaims(claims)

        assertFalse(result.valid)
        assertEquals(EuPidIssuanceProfile.REQUIRED_CLAIMS.size, result.missingClaims.size)
    }

    @Test
    fun `validateClaims fails specifically for expiry_date missing`() {
        val claims = minimalPid().toClaims().toMutableMap().apply {
            remove(EudiwConstants.CLAIM_EXPIRY_DATE)
        }
        val result = EuPidIssuanceProfile.validateClaims(claims)

        assertFalse(result.valid)
        assertTrue(EudiwConstants.CLAIM_EXPIRY_DATE in result.missingClaims)
    }

    // -------------------------------------------------------------------------
    // EudiwOid4VciProfile.validateFormat()
    // -------------------------------------------------------------------------

    @Test
    fun `validateFormat accepts vc+sd-jwt`() {
        val result = EudiwOid4VciProfile.validateFormat(EudiwConstants.CREDENTIAL_FORMAT_SD_JWT_VC)
        assertTrue(result.valid)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `validateFormat accepts mso_mdoc`() {
        val result = EudiwOid4VciProfile.validateFormat(EudiwConstants.CREDENTIAL_FORMAT_MSO_MDOC)
        assertTrue(result.valid)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `validateFormat rejects jwt_vc_json`() {
        val result = EudiwOid4VciProfile.validateFormat("jwt_vc_json")
        assertFalse(result.valid)
        assertTrue(result.violations.isNotEmpty())
        assertTrue(result.violations.first().contains("jwt_vc_json"))
    }

    @Test
    fun `validateFormat rejects ldp_vc`() {
        val result = EudiwOid4VciProfile.validateFormat("ldp_vc")
        assertFalse(result.valid)
        assertTrue(result.violations.isNotEmpty())
    }

    @Test
    fun `validateFormat rejects empty string`() {
        val result = EudiwOid4VciProfile.validateFormat("")
        assertFalse(result.valid)
    }

    // -------------------------------------------------------------------------
    // EudiwOid4VciProfile.validateAlgorithm()
    // -------------------------------------------------------------------------

    @Test
    fun `validateAlgorithm accepts ES256`() {
        assertTrue(EudiwOid4VciProfile.validateAlgorithm("ES256").valid)
    }

    @Test
    fun `validateAlgorithm accepts EdDSA`() {
        assertTrue(EudiwOid4VciProfile.validateAlgorithm("EdDSA").valid)
    }

    @Test
    fun `validateAlgorithm rejects RS256`() {
        val result = EudiwOid4VciProfile.validateAlgorithm("RS256")
        assertFalse(result.valid)
        assertTrue(result.violations.first().contains("RS256"))
    }

    // -------------------------------------------------------------------------
    // EudiwOid4VciProfile.validateCredentialOffer()
    // -------------------------------------------------------------------------

    @Test
    fun `validateCredentialOffer accepts openid-credential-offer scheme`() {
        val result = EudiwOid4VciProfile.validateCredentialOffer(
            "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example.com%2Foffer%2F123",
        )
        assertTrue(result.valid)
    }

    @Test
    fun `validateCredentialOffer rejects https scheme`() {
        val result = EudiwOid4VciProfile.validateCredentialOffer(
            "https://issuer.example.com/offer/123",
        )
        assertFalse(result.valid)
        assertTrue(result.violations.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // EudiwOid4VpProfile
    // -------------------------------------------------------------------------

    @Test
    fun `validateResponseMode accepts direct_post`() {
        assertTrue(EudiwOid4VpProfile.validateResponseMode("direct_post").valid)
    }

    @Test
    fun `validateResponseMode accepts direct_post jwt`() {
        assertTrue(EudiwOid4VpProfile.validateResponseMode("direct_post.jwt").valid)
    }

    @Test
    fun `validateResponseMode rejects fragment`() {
        assertFalse(EudiwOid4VpProfile.validateResponseMode("fragment").valid)
    }

    @Test
    fun `validateResponseMode rejects null`() {
        assertFalse(EudiwOid4VpProfile.validateResponseMode(null).valid)
    }

    @Test
    fun `validateClientIdScheme accepts did`() {
        assertTrue(EudiwOid4VpProfile.validateClientIdScheme("did").valid)
    }

    @Test
    fun `validateClientIdScheme accepts verifier_attestation`() {
        assertTrue(EudiwOid4VpProfile.validateClientIdScheme("verifier_attestation").valid)
    }

    @Test
    fun `validateClientIdScheme rejects unknown scheme`() {
        assertFalse(EudiwOid4VpProfile.validateClientIdScheme("redirect_uri").valid)
    }

    @Test
    fun `validateClientIdScheme rejects null`() {
        assertFalse(EudiwOid4VpProfile.validateClientIdScheme(null).valid)
    }

    // -------------------------------------------------------------------------
    // WalletTrustEvidence serialization
    // -------------------------------------------------------------------------

    @Test
    fun `WalletTrustEvidence serializes with correct snake_case field names`() {
        val wte = WalletTrustEvidence(
            walletInstanceId = "wallet-instance-001",
            walletProviderId = "did:example:wallet-provider",
            walletAttestationKey = """{"kty":"EC","crv":"P-256","x":"abc","y":"def"}""",
            securityLevel = WalletSecurityLevel.HIGH,
            certificationAuthority = "did:example:ca",
            certificationScheme = "EUDI-CC",
            certificationRef = "cert-ref-001",
            walletCapabilities = WalletCapabilities(
                formats = listOf("vc+sd-jwt", "mso_mdoc"),
                cryptographicSuites = listOf("ES256"),
                keyStorage = listOf("hardware_key_storage"),
                proofTypes = listOf("jwt"),
                userAuthentication = listOf("biometric"),
            ),
            issuedAt = 1700000000L,
            expiresAt = 1800000000L,
        )

        val serialized = Json { encodeDefaults = false }.encodeToString(
            WalletTrustEvidence.serializer(),
            wte,
        )

        assertTrue(serialized.contains("\"wallet_instance_id\""))
        assertTrue(serialized.contains("\"wallet_provider_id\""))
        assertTrue(serialized.contains("\"wallet_attestation_key\""))
        assertTrue(serialized.contains("\"security_level\""))
        assertTrue(serialized.contains("\"certification_authority\""))
        assertTrue(serialized.contains("\"certification_scheme\""))
        assertTrue(serialized.contains("\"wallet_capabilities\""))
        assertTrue(serialized.contains("\"cryptographic_suites\""))
        assertTrue(serialized.contains("\"key_storage\""))
        assertTrue(serialized.contains("\"proof_types\""))
        assertTrue(serialized.contains("\"user_authentication\""))
        assertTrue(serialized.contains("\"iat\""))
        assertTrue(serialized.contains("\"exp\""))
        // Enum value must be lowercase
        assertTrue(serialized.contains("\"high\""))
    }

    @Test
    fun `WalletSecurityLevel enum serializes to lowercase`() {
        val json = Json.Default
        assertEquals("\"high\"", json.encodeToString(WalletSecurityLevel.serializer(), WalletSecurityLevel.HIGH))
        assertEquals("\"substantial\"", json.encodeToString(WalletSecurityLevel.serializer(), WalletSecurityLevel.SUBSTANTIAL))
        assertEquals("\"low\"", json.encodeToString(WalletSecurityLevel.serializer(), WalletSecurityLevel.LOW))
    }

    // -------------------------------------------------------------------------
    // EuPidIssuanceProfile constants
    // -------------------------------------------------------------------------

    @Test
    fun `PID_VC_TYPES starts with VerifiableCredential`() {
        assertEquals("VerifiableCredential", EuPidIssuanceProfile.PID_VC_TYPES.first())
        assertTrue(EudiwConstants.PID_VC_TYPE in EuPidIssuanceProfile.PID_VC_TYPES)
    }

    @Test
    fun `PID_VC_CONTEXTS includes EUDIW context`() {
        assertTrue(EudiwConstants.EUDIW_CONTEXT in EuPidIssuanceProfile.PID_VC_CONTEXTS)
    }

    // -------------------------------------------------------------------------
    // EuPidCredential JSON round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `EuPidCredential serializes and deserializes to JSON`() {
        val pid = minimalPid().copy(
            ageOver18 = true,
            nationality = "DE",
            gender = 1,
        )
        val json = Json { encodeDefaults = false }
        val serialized = json.encodeToString(EuPidCredential.serializer(), pid)
        val deserialized = json.decodeFromString(EuPidCredential.serializer(), serialized)

        assertEquals(pid, deserialized)
        assertTrue(serialized.contains("\"family_name\""))
        assertTrue(serialized.contains("\"given_name\""))
        assertTrue(serialized.contains("\"birth_date\""))
        assertTrue(serialized.contains("\"issuing_authority\""))
        assertTrue(serialized.contains("\"issuing_country\""))
        assertTrue(serialized.contains("\"issuance_date\""))
        assertTrue(serialized.contains("\"expiry_date\""))
        assertTrue(serialized.contains("\"age_over_18\""))
        assertTrue(serialized.contains("\"nationality\""))
        assertTrue(serialized.contains("\"gender\""))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun minimalPid(): EuPidCredential = EuPidCredential(
        familyName = "Smith",
        givenName = "Alice",
        birthDate = "1990-06-15",
        issuingAuthority = "Ministry of Interior",
        issuingCountry = "DE",
        issuanceDate = "2024-01-01",
        expiryDate = "2029-01-01",
    )
}
