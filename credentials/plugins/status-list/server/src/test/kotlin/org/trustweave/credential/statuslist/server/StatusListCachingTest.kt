package org.trustweave.credential.statuslist.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
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
import org.trustweave.revocation.bitstring.BitstringStatusListManager
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusListCachingTest {
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

    @Test
    fun `status list responses are cacheable, revalidatable and signed once per list state`() =
        testApplication {
            val dataSource =
                JdbcDataSource().apply {
                    setURL("jdbc:h2:mem:status-cache-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
                }
            val issuer = "did:key:issuer"
            val engine = CountingEngine()
            val manager =
                BitstringStatusListManager(
                    dataSource,
                    InMemoryKeyManagementService(),
                    issuer,
                    1,
                    engine,
                    VerificationMethodId(Did(issuer), KeyId("#issuer-key-1")),
                )
            val listId = runBlocking { manager.createStatusList(issuer, StatusPurpose.REVOCATION) }
            application {
                install(ContentNegotiation) { json() }
                routing { configureStatusListRoutes(manager, null) }
            }

            val first = client.get("/status-lists/$listId")
            assertEquals(HttpStatusCode.OK, first.status, first.bodyAsText())
            val cacheControl = first.headers["Cache-Control"]
            assertTrue(cacheControl != null && "max-age=" in cacheControl, "Cache-Control: $cacheControl")
            val etag = first.headers["ETag"]
            assertTrue(etag != null, "an ETag lets verifiers revalidate cheaply")

            val again = client.get("/status-lists/$listId")
            assertEquals(first.bodyAsText(), again.bodyAsText())
            assertEquals(1, engine.issued, "an unchanged list is signed once, not once per request")

            val revalidated = client.get("/status-lists/$listId") { header("If-None-Match", etag) }
            assertEquals(HttpStatusCode.NotModified, revalidated.status)
        }
}
