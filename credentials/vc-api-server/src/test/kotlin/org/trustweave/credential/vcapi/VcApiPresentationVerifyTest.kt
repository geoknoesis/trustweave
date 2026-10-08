package org.trustweave.credential.vcapi

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.CredentialService
import org.trustweave.credential.CredentialServices
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** `POST /presentations/verify`: holder binding by default and one-time server-issued challenges. */
class VcApiPresentationVerifyTest {
    private val kms = InMemoryKeyManagementService()
    private val didMethod = DidKeyMockMethod(kms)
    private val issuerDocument = runBlocking { didMethod.createDid() }
    private val holderDocument = runBlocking { didMethod.createDid() }

    private val real =
        CredentialServices.createCredentialService(
            kms = kms,
            didResolver =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                },
            formats = listOf(ProofSuiteId.VC_LD),
        )

    private val seen = mutableListOf<VerificationOptions>()

    /** Records the options the route hands to the service; verification itself stays real. */
    private val recording: CredentialService =
        object : CredentialService by real {
            override suspend fun verifyPresentation(
                presentation: VerifiablePresentation,
                trustEvaluator: TrustEvaluator?,
                options: VerificationOptions,
            ): VerificationResult {
                seen += options
                return real.verifyPresentation(presentation, trustEvaluator, options)
            }
        }

    private val vpJson: JsonObject by lazy {
        runBlocking {
            val issued =
                real.issue(
                    org.trustweave.credential.requests.IssuanceRequest(
                        format = ProofSuiteId.VC_LD,
                        issuer =
                            org.trustweave.credential.model.vc.Issuer
                                .fromDid(issuerDocument.id),
                        issuerKeyId = issuerDocument.verificationMethod.first().id,
                        credentialSubject =
                            org.trustweave.credential.model.vc.CredentialSubject(
                                id =
                                    org.trustweave.core.identifiers
                                        .Iri(holderDocument.id.value),
                                claims = emptyMap(),
                            ),
                        type =
                            listOf(
                                org.trustweave.credential.model.CredentialType
                                    .fromString("VerifiableCredential"),
                            ),
                    ),
                )
            val vc = (issued as IssuanceResult.Success).credential
            val vp = real.createPresentation(listOf(vc), PresentationRequest())
            Json { serializersModule = SerializationModule.default }
                .encodeToJsonElement(VerifiablePresentation.serializer(), vp)
                .jsonObject
        }
    }

    private fun app(policy: VcApiVerificationPolicy): Application.() -> Unit =
        {
            install(ContentNegotiation) {
                json(
                    Json {
                        serializersModule = SerializationModule.default
                        ignoreUnknownKeys = true
                    },
                )
            }
            routing { configureVcApiRoutes(recording, null, policy) }
        }

    private suspend fun ApplicationTestBuilder.challenge(): String =
        Json
            .parseToJsonElement(
                client.post("/presentations/challenge") { contentType(ContentType.Application.Json) }.bodyAsText(),
            ).jsonObject["challenge"]!!
            .jsonPrimitive.content

    private suspend fun ApplicationTestBuilder.verify(challenge: String?) =
        client.post("/presentations/verify") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("verifiablePresentation", vpJson)
                    if (challenge != null) putJsonObject("options") { put("challenge", challenge) }
                }.toString(),
            )
        }

    private fun verified(body: String) =
        Json
            .parseToJsonElement(body)
            .jsonObject["verified"]!!
            .jsonPrimitive.boolean

    @Test
    fun `holder binding is enforced by default`() =
        testApplication {
            application(app(VcApiVerificationPolicy()))
            val response = verify(challenge())
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertTrue(seen.single().enforceHolderBinding, "holder binding must be on unless relaxed")
        }

    @Test
    fun `holder binding can be relaxed`() =
        testApplication {
            application(app(VcApiVerificationPolicy(enforceHolderBinding = false)))
            verify(challenge())
            assertFalse(seen.single().enforceHolderBinding)
        }

    @Test
    fun `a challenge can be used once`() =
        testApplication {
            application(app(VcApiVerificationPolicy()))
            val c = challenge()
            verify(c)
            assertEquals(1, seen.size, "the first use must reach the verifier")
            assertEquals(c, seen.single().expectedChallenge)

            val replay = verify(c)
            assertEquals(1, seen.size, "a replayed challenge must not reach the verifier")
            assertFalse(verified(replay.bodyAsText()))
        }

    @Test
    fun `a challenge the server did not issue is refused`() =
        testApplication {
            application(app(VcApiVerificationPolicy()))
            val response = verify("made-up-by-the-caller")
            assertFalse(verified(response.bodyAsText()))
            assertTrue(seen.isEmpty())
        }

    @Test
    fun `a missing challenge is refused unless the policy relaxes it`() =
        testApplication {
            application(app(VcApiVerificationPolicy()))
            assertFalse(verified(verify(null).bodyAsText()))
            assertTrue(seen.isEmpty())
        }

    @Test
    fun `challenge requirement can be relaxed`() =
        testApplication {
            application(app(VcApiVerificationPolicy(requireChallenge = false)))
            verify(null)
            assertEquals(1, seen.size)
        }

    @Test
    fun `an expired challenge is refused`() {
        var now = kotlin.time.Instant.fromEpochSeconds(1_000)
        val clock =
            object : kotlin.time.Clock {
                override fun now() = now
            }
        val store = InMemoryVcApiChallengeStore(ttl = kotlin.time.Duration.parse("1m"), clock = clock)
        val issued = store.issue()
        now = now + kotlin.time.Duration.parse("2m")
        assertFalse(store.consume(issued.challenge))
    }

    @Test
    fun `the store is bounded and issues distinct challenges`() {
        val store = InMemoryVcApiChallengeStore(capacity = 2)
        val a = store.issue()
        val b = store.issue()
        assertNotEquals(a.challenge, b.challenge)
        val failure = runCatching { store.issue() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "a full store must refuse, not evict: $failure")
        assertTrue(store.consume(a.challenge))
        store.issue()
    }
}
