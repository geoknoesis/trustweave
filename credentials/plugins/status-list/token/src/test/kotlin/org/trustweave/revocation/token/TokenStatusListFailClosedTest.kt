package org.trustweave.revocation.token

import com.nimbusds.jwt.SignedJWT
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.trustweave.core.exception.ConfigException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Fail-closed status lookups and real-key signing of the Token Status List manager. */
class TokenStatusListFailClosedTest {
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:token_fc_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
                maximumPoolSize = 5
            },
        )
    private val kms = InMemoryKeyManagementService()
    private val issuerDid = "did:key:z6MkTokenIssuer"
    private val uri = "https://example.com/statuslists/t"
    private val key = (runBlocking { kms.generateKey(Algorithm.Ed25519) } as GenerateKeyResult.Success).keyHandle
    private val vmId = VerificationMethodId(Did(issuerDid), KeyId("#${key.id.value}"))
    private val manager = TokenStatusListManager(dataSource, kms, issuerDid, uri, 1, vmId)

    @AfterTest
    fun tearDown() = dataSource.close()

    private suspend fun newList(m: TokenStatusListManager) =
        m.createStatusList(issuerDid, StatusPurpose.REVOCATION, 64, "https://example.com/lists/${java.util.UUID.randomUUID()}")

    private fun credentialWithStatus(
        listId: String,
        index: String?,
        id: String? = null,
    ) = VerifiableCredential(
        id =
            id?.let {
                org.trustweave.credential.identifiers
                    .CredentialId(it)
            },
        type = listOf(CredentialType.VerifiableCredential),
        issuer = Issuer.IriIssuer(Iri(issuerDid)),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject(id = Iri("did:example:holder"), claims = emptyMap()),
        credentialStatus =
            CredentialStatus(
                id = StatusListId("$listId#${index ?: "x"}"),
                type = "TokenStatusListEntry",
                statusPurpose = StatusPurpose.REVOCATION,
                statusListIndex = index,
                statusListCredential = StatusListId(listId),
            ),
    )

    // ------------------------------------------------------------------ fail closed

    @Test
    fun `an unknown status list is an error, not a valid status`() =
        runBlocking<Unit> {
            val e =
                assertFailsWith<TrustWeaveException.InvalidState> {
                    manager.checkStatusByIndex(
                        StatusListId("https://example.com/lists/nope"),
                        0,
                    )
                }
            assertEquals("STATUS_LIST_UNAVAILABLE", e.code)
            assertFailsWith<TrustWeaveException.InvalidState> {
                manager.checkStatusByCredentialId(
                    "c",
                    StatusListId("https://example.com/lists/nope"),
                )
            }
            assertFailsWith<TrustWeaveException.InvalidState> {
                manager.checkRevocationStatus(credentialWithStatus("https://example.com/lists/nope", "3"))
            }
        }

    @Test
    fun `an out-of-range index is an error`() =
        runBlocking<Unit> {
            val list = newList(manager)
            assertFalse(manager.checkStatusByIndex(list, 63).revoked)
            for (bad in listOf(-1, 64, 10_000)) {
                val e = assertFailsWith<TrustWeaveException.InvalidOperation>("index $bad") { manager.checkStatusByIndex(list, bad) }
                assertEquals("RANGE_ERROR", e.code)
            }
        }

    @Test
    fun `a status entry whose index cannot be determined is an error`() =
        runBlocking<Unit> {
            val list = newList(manager)
            val e =
                assertFailsWith<TrustWeaveException.InvalidState> {
                    manager.checkRevocationStatus(credentialWithStatus(list.toString(), index = null))
                }
            assertEquals("STATUS_LIST_INDEX_UNKNOWN", e.code)
            val unindexedById =
                assertFailsWith<TrustWeaveException.InvalidState> {
                    manager.checkRevocationStatus(credentialWithStatus(list.toString(), index = null, id = "urn:uuid:never-assigned"))
                }
            assertEquals("STATUS_LIST_INDEX_UNKNOWN", unindexedById.code)
        }

    @Test
    fun `a credential id without an assigned index is not reported as valid`() =
        runBlocking<Unit> {
            val list = newList(manager)
            assertFailsWith<TrustWeaveException.NotFound> { manager.checkStatusByCredentialId("unassigned", list) }
        }

    @Test
    fun `a credential without a status entry is simply not revoked`() =
        runBlocking<Unit> {
            val plain =
                VerifiableCredential(
                    type = listOf(CredentialType.VerifiableCredential),
                    issuer = Issuer.IriIssuer(Iri(issuerDid)),
                    issuanceDate = Clock.System.now(),
                    credentialSubject = CredentialSubject(id = Iri("did:example:h"), claims = emptyMap()),
                )
            assertFalse(manager.checkRevocationStatus(plain).revoked)
        }

    @Test
    fun `revoked status is read back through every lookup path`() =
        runBlocking<Unit> {
            val list = newList(manager)
            val idx = manager.assignCredentialIndex("cred-1", list, null)
            assertTrue(manager.revokeCredential("cred-1", list))
            assertTrue(manager.checkStatusByIndex(list, idx).revoked)
            assertTrue(manager.checkStatusByCredentialId("cred-1", list).revoked)
            assertTrue(manager.checkRevocationStatus(credentialWithStatus(list.toString(), idx.toString())).revoked)
            assertFalse(manager.checkStatusByIndex(list, idx + 1).revoked)
        }

    @Test
    fun `a stored purpose that is not recognised fails closed`() =
        runBlocking<Unit> {
            val list = newList(manager)
            dataSource.connection.use { it.createStatement().execute("UPDATE token_status_lists SET purpose = 'BOGUS'") }
            val e = assertFailsWith<TrustWeaveException.InvalidState> { manager.checkStatusByIndex(list, 0) }
            assertEquals("STATUS_LIST_CORRUPT", e.code)
        }

    @Test
    fun `bits per entry other than 1 or 2 is rejected`() {
        assertFailsWith<IllegalArgumentException> { TokenStatusListManager(dataSource, kms, issuerDid, uri, 3, vmId) }
    }

    // ------------------------------------------------------------------ signing

    @Test
    fun `the token is signed with the configured issuer key and carries its kid`() =
        runBlocking<Unit> {
            val list = newList(manager)
            manager.assignCredentialIndex("c", list, 5)
            manager.revokeCredential("c", list)
            val token = manager.buildStatusListToken(list)
            val jwt = SignedJWT.parse(token.jwt)
            assertEquals("statuslist+jwt", jwt.header.type.toString())
            assertEquals(vmId.value, jwt.header.keyID)

            val x = Base64.getUrlDecoder().decode((key.publicKeyJwk?.get("x") as String))
            val spki = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + x
            val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
            val verified =
                Signature.getInstance("Ed25519").run {
                    initVerify(publicKey)
                    update(jwt.signingInput)
                    verify(jwt.signature.decode())
                }
            assertTrue(verified, "the token must verify under the issuer's published key")
        }

    @Test
    fun `two tokens for one list are signed by the same key, not a fresh one each time`() =
        runBlocking<Unit> {
            val list = newList(manager)
            val a = SignedJWT.parse(manager.buildStatusListToken(list).jwt)
            val b = SignedJWT.parse(manager.buildStatusListToken(list).jwt)
            assertEquals(a.header.keyID, b.header.keyID)
            assertNotEquals(null, a.header.keyID)
        }

    @Test
    fun `without an issuer key a token cannot be built`() =
        runBlocking<Unit> {
            val keyless = TokenStatusListManager(dataSource, kms, issuerDid, uri, 1)
            val list = newList(keyless)
            assertFailsWith<ConfigException> { keyless.buildStatusListToken(list) }
            // status tracking itself keeps working
            keyless.assignCredentialIndex("c", list, null)
            keyless.revokeCredential("c", list)
            assertTrue(keyless.checkStatusByCredentialId("c", list).revoked)
        }

    @Test
    fun `an issuer key of another DID is refused`() =
        runBlocking<Unit> {
            val other =
                TokenStatusListManager(dataSource, kms, issuerDid, uri, 1, VerificationMethodId(Did("did:key:z6MkOther"), KeyId("#k")))
            val list = newList(other)
            assertFailsWith<ConfigException> { other.buildStatusListToken(list) }
        }

    @Test
    fun `an issuer key that is not in the KMS is refused`() =
        runBlocking<Unit> {
            val ghost =
                TokenStatusListManager(dataSource, kms, issuerDid, uri, 1, VerificationMethodId(Did(issuerDid), KeyId("#ghost-key")))
            val list = newList(ghost)
            assertFailsWith<ConfigException> { ghost.buildStatusListToken(list) }
        }

    @Test
    fun `building a token for an unknown list is an error`() =
        runBlocking<Unit> {
            assertFailsWith<IllegalArgumentException> { manager.buildStatusListToken(StatusListId("https://example.com/lists/missing")) }
        }
}
