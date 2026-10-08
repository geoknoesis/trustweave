package org.trustweave.revocation.bitstring

import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration

/** The signed status list credential is reused until the list changes, not re-signed per request. */
class SignedStatusListCacheTest {
    private val issuerDid = "did:key:z6MkTestCacheIssuer"
    private val issuerKeyId = VerificationMethodId(Did(issuerDid), KeyId("#issuer-key-1"))

    private class CountingEngine : ProofEngine {
        var issued = 0
        override val format = ProofSuiteId.VC_LD
        override val formatName = "Counting VC-LD (test)"
        override val formatVersion = "test"
        override val capabilities = ProofEngineCapabilities()

        override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
            issued++
            return VerifiableCredential(
                type = request.type,
                issuer = request.issuer,
                issuanceDate = request.issuedAt,
                credentialSubject = request.credentialSubject,
                proof =
                    CredentialProof.LinkedDataProof(
                        type = "Ed25519Signature2020",
                        created = request.issuedAt,
                        verificationMethod = request.issuerKeyId!!.value,
                        proofPurpose = "assertionMethod",
                        proofValue = "ztest-signature",
                    ),
            )
        }

        override suspend fun verify(
            credential: VerifiableCredential,
            options: VerificationOptions,
        ): VerificationResult = throw UnsupportedOperationException()
    }

    private fun manager(
        engine: ProofEngine,
        cacheTtl: Duration? = null,
    ): BitstringStatusListManager {
        val dataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:signed-cache-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            }
        val kms = InMemoryKeyManagementService()
        return if (cacheTtl == null) {
            BitstringStatusListManager(dataSource, kms, issuerDid, 1, engine, issuerKeyId)
        } else {
            BitstringStatusListManager(
                dataSource,
                kms,
                issuerDid,
                1,
                engine,
                issuerKeyId,
                null,
                null,
                signedStatusListCacheTtl = cacheTtl,
            )
        }
    }

    @Test
    fun `an unchanged list is signed once and a changed list is signed afresh`() =
        runBlocking<Unit> {
            val engine = CountingEngine()
            val manager = manager(engine)
            val listId = manager.createStatusList(issuerDid, StatusPurpose.REVOCATION)
            manager.assignCredentialIndex("cached-cred", listId)

            val first = manager.buildStatusListVc(listId)
            val second = manager.buildStatusListVc(listId)
            assertEquals(1, engine.issued, "an unchanged list must not be re-signed on every request")
            assertEquals(first, second)

            manager.revokeCredential("cached-cred", listId)
            val third = manager.buildStatusListVc(listId)
            assertEquals(2, engine.issued, "a changed list must be signed afresh")
            assertNotEquals(first.credentialSubject.claims["encodedList"], third.credentialSubject.claims["encodedList"])
        }

    @Test
    fun `a zero ttl disables the reuse`() =
        runBlocking<Unit> {
            val engine = CountingEngine()
            val manager = manager(engine, Duration.ZERO)
            val listId = manager.createStatusList(issuerDid, StatusPurpose.REVOCATION)
            manager.buildStatusListVc(listId)
            manager.buildStatusListVc(listId)
            assertEquals(2, engine.issued)
        }
}
