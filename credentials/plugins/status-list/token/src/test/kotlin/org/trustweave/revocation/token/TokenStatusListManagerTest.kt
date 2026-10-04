package org.trustweave.revocation.token

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.util.Base64
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TokenStatusListManagerTest {
    private lateinit var dataSource: DataSource
    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var manager: TokenStatusListManager

    private val issuerDid = "did:key:z6MkTestTokenIssuer"
    private val statusListUri = "https://example.com/statuslists/test"

    @BeforeTest
    fun setUp() {
        val config =
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:token_test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
                maximumPoolSize = 5
            }
        dataSource = HikariDataSource(config)
        kms = InMemoryKeyManagementService()
        val key =
            runBlocking { kms.generateKey(org.trustweave.kms.Algorithm.Ed25519) }
                as org.trustweave.kms.results.GenerateKeyResult.Success
        manager =
            TokenStatusListManagerFactory.create(
                dataSource = dataSource,
                kms = kms,
                issuerDid = issuerDid,
                statusListUri = statusListUri,
                bitsPerEntry = 1,
                issuerKeyId =
                    org.trustweave.did.identifiers.VerificationMethodId(
                        org.trustweave.did.identifiers
                            .Did(issuerDid),
                        org.trustweave.core.identifiers
                            .KeyId("#${key.keyHandle.id.value}"),
                    ),
            )
    }

    @AfterTest
    fun tearDown() {
        (dataSource as? HikariDataSource)?.close()
    }

    // -------------------------------------------------------------------------
    // JWT structure
    // -------------------------------------------------------------------------

    @Test
    fun `buildStatusListToken returns a three-part JWT string`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            val token = manager.buildStatusListToken(statusListId)
            assertTrue(token.jwt.isNotBlank(), "JWT must not be blank")

            val parts = token.jwt.split(".")
            assertEquals(3, parts.size, "Compact JWT must have exactly 3 dot-separated parts")
        }

    @Test
    fun `buildStatusListToken JWT header contains typ statuslist+jwt`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            val token = manager.buildStatusListToken(statusListId)

            // Decode header manually (base64url, no padding)
            val headerJson = String(Base64.getUrlDecoder().decode(token.jwt.substringBefore(".")), Charsets.UTF_8)
            assertTrue(headerJson.contains("statuslist+jwt"), "Header 'typ' must be 'statuslist+jwt'")
        }

    @Test
    fun `buildStatusListToken JWT payload contains required claims`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            val token = manager.buildStatusListToken(statusListId)

            val parts = token.jwt.split(".")
            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)

            assertTrue(payloadJson.contains("\"iss\""), "Payload must contain 'iss'")
            assertTrue(payloadJson.contains("\"sub\""), "Payload must contain 'sub'")
            assertTrue(payloadJson.contains("\"iat\""), "Payload must contain 'iat'")
            assertTrue(payloadJson.contains("\"status_list\""), "Payload must contain 'status_list'")
            assertTrue(payloadJson.contains("\"bits\""), "status_list must contain 'bits'")
            assertTrue(payloadJson.contains("\"lst\""), "status_list must contain 'lst'")
        }

    @Test
    fun `buildStatusListToken JWT payload issuer matches issuerDid`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            val token = manager.buildStatusListToken(statusListId)

            val parts = token.jwt.split(".")
            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            assertTrue(payloadJson.contains(issuerDid), "Payload 'iss' must match issuerDid")
        }

    @Test
    fun `buildStatusListToken status_list lst is valid base64url without padding`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                    size = 8,
                )
            val token = manager.buildStatusListToken(statusListId)

            val parts = token.jwt.split(".")
            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)

            val lstStart = payloadJson.indexOf("\"lst\":\"") + "\"lst\":\"".length
            val lstEnd = payloadJson.indexOf("\"", lstStart)
            val lst = payloadJson.substring(lstStart, lstEnd)

            assertFalse(lst.contains('='), "lst must not contain padding")
            // Must be decodable
            val decoded = TokenStatusListCodec.decode(lst)
            assertTrue(decoded.isNotEmpty(), "Decoded lst must not be empty")
            assertEquals(0x78, Base64.getUrlDecoder().decode(lst)[0].toInt() and 0xFF, "lst must be ZLIB-compressed")
            assertEquals(1, decoded.size, "8-entry list at 1 bit/entry = 1 byte")
        }

    @Test
    fun `buildStatusListToken with ttlSeconds includes exp claim`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            val token = manager.buildStatusListToken(statusListId, ttlSeconds = 3600L)

            val parts = token.jwt.split(".")
            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            assertTrue(payloadJson.contains("\"exp\""), "Payload must contain 'exp' when ttlSeconds is set")
            assertTrue(payloadJson.contains("\"ttl\""), "Payload must contain 'ttl' when ttlSeconds is set")
        }

    // -------------------------------------------------------------------------
    // Bit-packing round-trip (1 bit per entry)
    // -------------------------------------------------------------------------

    @Test
    fun `revokeCredential sets the correct bit in the status array`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                    size = 8,
                )
            manager.assignCredentialIndex("cred-bit-0", statusListId) // index 0
            manager.assignCredentialIndex("cred-bit-1", statusListId) // index 1

            manager.revokeCredential("cred-bit-0", statusListId)

            val token = manager.buildStatusListToken(statusListId)
            val parts = token.jwt.split(".")
            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)

            val lstStart = payloadJson.indexOf("\"lst\":\"") + "\"lst\":\"".length
            val lstEnd = payloadJson.indexOf("\"", lstStart)
            val arr = TokenStatusListCodec.decode(payloadJson.substring(lstStart, lstEnd))

            // Bit 0 of byte 0 must be set
            assertTrue((arr[0].toInt() and 0x01) != 0, "Bit 0 must be set for revoked credential at index 0")
            // Bit 1 of byte 0 must be clear
            assertTrue((arr[0].toInt() and 0x02) == 0, "Bit 1 must be clear for non-revoked credential at index 1")
        }

    @Test
    fun `unrevokeCredential clears the bit`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            manager.assignCredentialIndex("cred-unrevoke-tok", statusListId)
            manager.revokeCredential("cred-unrevoke-tok", statusListId)
            manager.unrevokeCredential("cred-unrevoke-tok", statusListId)

            val status = manager.checkStatusByCredentialId("cred-unrevoke-tok", statusListId)
            assertFalse(status.revoked, "Should not be revoked after unrevokeCredential")
        }

    // -------------------------------------------------------------------------
    // Bit-packing round-trip (2 bits per entry)
    // -------------------------------------------------------------------------

    @Test
    fun `2-bit mode revoke and suspend set independent bits`() =
        runBlocking<Unit> {
            val manager2 =
                TokenStatusListManagerFactory.create(
                    dataSource = dataSource,
                    kms = kms,
                    issuerDid = issuerDid,
                    statusListUri = statusListUri,
                    bitsPerEntry = 2,
                )

            val statusListId =
                manager2.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                    size = 4,
                )
            manager2.assignCredentialIndex("dual-cred", statusListId)
            manager2.revokeCredential("dual-cred", statusListId)
            manager2.suspendCredential("dual-cred", statusListId)

            val status = manager2.checkStatusByCredentialId("dual-cred", statusListId)
            assertTrue(status.revoked, "Should be revoked")
            assertTrue(status.suspended, "Should also be suspended in 2-bit mode")
        }

    // -------------------------------------------------------------------------
    // Revocation check
    // -------------------------------------------------------------------------

    @Test
    fun `checkStatusByCredentialId returns not revoked for fresh credential`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            manager.assignCredentialIndex("fresh-cred", statusListId)
            val status = manager.checkStatusByCredentialId("fresh-cred", statusListId)
            assertFalse(status.revoked, "Fresh credential must not be revoked")
            assertFalse(status.suspended, "Fresh credential must not be suspended")
        }

    @Test
    fun `revokeCredentials batch sets all entries`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            val ids = listOf("batch-tok-1", "batch-tok-2", "batch-tok-3")
            ids.forEach { manager.assignCredentialIndex(it, statusListId) }

            val results = manager.revokeCredentials(ids, statusListId)
            ids.forEach { id ->
                assertEquals(true, results[id], "revokeCredentials result for $id must be true")
                assertTrue(
                    manager.checkStatusByCredentialId(id, statusListId).revoked,
                    "$id must be revoked",
                )
            }
        }

    // -------------------------------------------------------------------------
    // Statistics & metadata
    // -------------------------------------------------------------------------

    @Test
    fun `getStatusListStatistics reflects usedIndices and revokedCount`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                    size = 16,
                )
            manager.assignCredentialIndex("stat-a", statusListId)
            manager.assignCredentialIndex("stat-b", statusListId)
            manager.revokeCredential("stat-a", statusListId)

            val stats = manager.getStatusListStatistics(statusListId)
            assertNotNull(stats)
            assertEquals(2, stats.usedIndices)
            assertEquals(1, stats.revokedCount)
        }

    // -------------------------------------------------------------------------
    // Delete & expand
    // -------------------------------------------------------------------------

    @Test
    fun `deleteStatusList returns true and removes the entry`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                )
            assertTrue(manager.deleteStatusList(statusListId))
            assertEquals(null, manager.getStatusList(statusListId))
        }

    @Test
    fun `expandStatusList increases recorded size`() =
        runBlocking<Unit> {
            val statusListId =
                manager.createStatusList(
                    issuerDid = issuerDid,
                    purpose = StatusPurpose.REVOCATION,
                    size = 8,
                )
            manager.expandStatusList(statusListId, additionalSize = 8)
            val metadata = manager.getStatusList(statusListId)
            assertNotNull(metadata)
            assertTrue(metadata.size >= 16, "Size must be at least 16 after expansion")
        }

    // -------------------------------------------------------------------------
    // ES256 signing and ttl policy
    // -------------------------------------------------------------------------

    /** In-memory KMS holding a JCA P-256 key; signs as P1363 (`r || s`) or, when asked, DER. */
    private class P256Kms(
        private val derSignatures: Boolean = false,
    ) : org.trustweave.kms.KeyManagementService by InMemoryKeyManagementService() {
        private val pairs = HashMap<org.trustweave.core.identifiers.KeyId, java.security.KeyPair>()

        override suspend fun getSupportedAlgorithms() = setOf(org.trustweave.kms.Algorithm.P256)

        override suspend fun generateKey(
            algorithm: org.trustweave.kms.Algorithm,
            options: Map<String, Any?>,
        ): org.trustweave.kms.results.GenerateKeyResult {
            val pair =
                java.security.KeyPairGenerator
                    .getInstance("EC")
                    .apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }
                    .generateKeyPair()
            val id =
                org.trustweave.core.identifiers
                    .KeyId("p256-${pairs.size}")
            pairs[id] = pair
            val point = (pair.public as java.security.interfaces.ECPublicKey).w

            fun b64(v: java.math.BigInteger) =
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    v.toByteArray().let {
                        if (it.size >
                            32
                        ) {
                            it.copyOfRange(it.size - 32, it.size)
                        } else {
                            ByteArray(32 - it.size) + it
                        }
                    },
                )
            return org.trustweave.kms.results.GenerateKeyResult.Success(
                org.trustweave.kms.KeyHandle(
                    id,
                    "P-256",
                    mapOf("kty" to "EC", "crv" to "P-256", "x" to b64(point.affineX), "y" to b64(point.affineY)),
                ),
            )
        }

        override suspend fun getPublicKey(keyId: org.trustweave.core.identifiers.KeyId): org.trustweave.kms.results.GetPublicKeyResult {
            val pair =
                pairs[keyId] ?: return org.trustweave.kms.results.GetPublicKeyResult.Failure
                    .KeyNotFound(keyId)
            val point = (pair.public as java.security.interfaces.ECPublicKey).w

            fun b64(v: java.math.BigInteger) =
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    v.toByteArray().let {
                        if (it.size >
                            32
                        ) {
                            it.copyOfRange(it.size - 32, it.size)
                        } else {
                            ByteArray(32 - it.size) + it
                        }
                    },
                )
            return org.trustweave.kms.results.GetPublicKeyResult.Success(
                org.trustweave.kms.KeyHandle(
                    keyId,
                    "P-256",
                    mapOf("kty" to "EC", "crv" to "P-256", "x" to b64(point.affineX), "y" to b64(point.affineY)),
                ),
            )
        }

        override suspend fun sign(
            keyId: org.trustweave.core.identifiers.KeyId,
            data: ByteArray,
            algorithm: org.trustweave.kms.Algorithm?,
        ): org.trustweave.kms.results.SignResult {
            val pair =
                pairs[keyId] ?: return org.trustweave.kms.results.SignResult.Failure
                    .KeyNotFound(keyId)
            val der =
                java.security.Signature
                    .getInstance("SHA256withECDSA")
                    .apply {
                        initSign(pair.private)
                        update(data)
                    }.sign()
            val out =
                if (derSignatures) {
                    der
                } else {
                    org.trustweave.kms.util.EcdsaSignatureCodec.derToP1363(
                        der,
                        org.trustweave.kms.Algorithm.P256,
                    )
                }
            return org.trustweave.kms.results.SignResult
                .Success(out)
        }
    }

    private fun managerWithKey(
        algorithm: org.trustweave.kms.Algorithm,
        defaultTtlSeconds: Long? = null,
        requireTtl: Boolean = false,
        useKms: org.trustweave.kms.KeyManagementService = kms,
    ): TokenStatusListManager {
        val key =
            runBlocking { useKms.generateKey(algorithm) } as org.trustweave.kms.results.GenerateKeyResult.Success
        return TokenStatusListManagerFactory.create(
            dataSource = dataSource,
            kms = useKms,
            issuerDid = issuerDid,
            statusListUri = statusListUri,
            issuerKeyId =
                org.trustweave.did.identifiers.VerificationMethodId(
                    org.trustweave.did.identifiers
                        .Did(issuerDid),
                    org.trustweave.core.identifiers
                        .KeyId("#${key.keyHandle.id.value}"),
                ),
            defaultTtlSeconds = defaultTtlSeconds,
            requireTtl = requireTtl,
        )
    }

    private fun assertEs256Token(
        p256: P256Kms,
        derSignatures: Boolean,
    ) = runBlocking<Unit> {
        val es = managerWithKey(org.trustweave.kms.Algorithm.P256, useKms = p256)
        val id = es.createStatusList(issuerDid = issuerDid, purpose = StatusPurpose.REVOCATION)
        val token = es.buildStatusListToken(id)
        val jws =
            com.nimbusds.jwt.SignedJWT
                .parse(token.jwt)
        assertEquals(com.nimbusds.jose.JWSAlgorithm.ES256, jws.header.algorithm)
        assertEquals(64, jws.signature.decode().size, "ES256 signature must be raw r||s (der=$derSignatures)")
        val kid = jws.header.keyID.substringAfter('#')
        val pub =
            p256.getPublicKey(
                org.trustweave.core.identifiers
                    .KeyId(kid),
            ) as org.trustweave.kms.results.GetPublicKeyResult.Success
        val ecKey =
            com.nimbusds.jose.jwk.ECKey
                .parse(checkNotNull(pub.keyHandle.publicKeyJwk).mapValues { it.value.toString() })
        assertTrue(
            jws.verify(
                com.nimbusds.jose.crypto
                    .ECDSAVerifier(ecKey),
            ),
            "ES256 signature must verify",
        )
    }

    @Test
    fun `a P-256 issuer key yields an ES256 token whose signature verifies`() = assertEs256Token(P256Kms(), false)

    @Test
    fun `a DER signature from the KMS is transcoded to raw ES256`() = assertEs256Token(P256Kms(derSignatures = true), true)

    @Test
    fun `an unsupported issuer key type is refused`() =
        runBlocking<Unit> {
            val k = InMemoryKeyManagementService()
            val m = managerWithKey(org.trustweave.kms.Algorithm.Secp256k1, useKms = k)
            val id = m.createStatusList(issuerDid = issuerDid, purpose = StatusPurpose.REVOCATION)
            org.junit.jupiter.api
                .assertThrows<org.trustweave.core.exception.ConfigException> { m.buildStatusListToken(id) }
        }

    @Test
    fun `requireTtl refuses a token without any ttl and accepts one with a default`() =
        runBlocking<Unit> {
            val strict = managerWithKey(org.trustweave.kms.Algorithm.Ed25519, requireTtl = true)
            val id = strict.createStatusList(issuerDid = issuerDid, purpose = StatusPurpose.REVOCATION)
            org.junit.jupiter.api.assertThrows<org.trustweave.core.exception.ConfigException> {
                strict.buildStatusListToken(id)
            }
            assertTrue(strict.buildStatusListToken(id, ttlSeconds = 60).jwt.isNotBlank())

            val withDefault = managerWithKey(org.trustweave.kms.Algorithm.Ed25519, defaultTtlSeconds = 120, requireTtl = true)
            val id2 = withDefault.createStatusList(issuerDid = issuerDid, purpose = StatusPurpose.REVOCATION)
            val payload = String(Base64.getUrlDecoder().decode(withDefault.buildStatusListToken(id2).jwt.split(".")[1]))
            assertTrue(payload.contains("\"ttl\":120"), payload)
            assertTrue(payload.contains("\"exp\""), payload)
        }

    @Test
    fun `a non-positive ttl is rejected`() =
        runBlocking<Unit> {
            val id = manager.createStatusList(issuerDid = issuerDid, purpose = StatusPurpose.REVOCATION)
            org.junit.jupiter.api
                .assertThrows<IllegalArgumentException> { manager.buildStatusListToken(id, ttlSeconds = 0) }
        }

    @Test
    fun `a database failure while deleting a list is an error, not false`() =
        runBlocking<Unit> {
            val id = manager.createStatusList(issuerDid = issuerDid, purpose = StatusPurpose.REVOCATION)
            dataSource.connection.use { conn ->
                conn.createStatement().execute("DROP TABLE token_credential_indices")
            }
            org.junit.jupiter.api.assertThrows<Exception> { manager.deleteStatusList(id) }
        }

    @Test
    fun `deleting an unknown list is false`() =
        runBlocking<Unit> {
            val id = manager.createStatusList(issuerDid = issuerDid, purpose = StatusPurpose.REVOCATION)
            assertTrue(manager.deleteStatusList(id))
            assertFalse(manager.deleteStatusList(id))
        }
}
