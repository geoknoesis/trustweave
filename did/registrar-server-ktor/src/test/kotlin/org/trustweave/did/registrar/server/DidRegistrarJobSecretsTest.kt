package org.trustweave.did.registrar.server

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.DidState
import org.trustweave.did.registrar.model.KeyMaterial
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.model.Secret
import org.trustweave.did.registrar.model.UpdateDidOptions
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidRegistrarJobSecretsTest {
    private val hostileJobId = "../../other-callers-job"

    private val pending =
        DidRegistrationResponse(
            jobId = hostileJobId,
            didState =
                DidState(
                    state = OperationState.WAIT,
                    did = "did:example:1",
                    secret =
                        Secret(
                            keys =
                                listOf(
                                    KeyMaterial(
                                        id = "k",
                                        privateKeyJwk = buildJsonObject { put("d", "PRIVATE-SCALAR") },
                                    ),
                                ),
                            recoveryKey = "RECOVERY-KEY",
                            updateKey = "UPDATE-KEY",
                        ),
                ),
        )

    private val registrar =
        object : DidRegistrar {
            override suspend fun createDid(
                method: String,
                options: CreateDidOptions,
            ) = pending

            override suspend fun updateDid(
                did: String,
                document: DidDocument,
                options: UpdateDidOptions,
            ) = pending

            override suspend fun deactivateDid(
                did: String,
                options: DeactivateDidOptions,
            ) = pending
        }

    @Test
    fun `the caller gets the secret once, the job store never holds it, and the key is not upstream-chosen`() =
        testApplication {
            val jobs = InMemoryJobStorage()
            application {
                install(ContentNegotiation) { json(registrarJson()) }
                routing { configureDidRegistrarRoutes(registrar, jobs) }
            }
            val response =
                client.post("/1.0/dids") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"method":"example","options":{"returnSecrets":true}}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue("PRIVATE-SCALAR" in body && "RECOVERY-KEY" in body, "the original caller still receives the secret: $body")

            assertNull(jobs.get(hostileJobId), "an upstream-supplied job id must not be the storage key")
            val jobId = Regex("\"jobId\"\\s*:\\s*\"([^\"]+)\"").find(body)!!.groupValues[1]
            assertFalse(jobId == hostileJobId)
            val stored = assertNotNull(jobs.get(jobId))
            assertNull(stored.didState.secret)
        }
}
