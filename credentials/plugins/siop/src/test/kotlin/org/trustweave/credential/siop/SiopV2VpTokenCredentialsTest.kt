package org.trustweave.credential.siop

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.pex.DescriptorMap
import org.trustweave.credential.pex.PresentationSubmission
import org.trustweave.credential.siop.models.SiopV2Session
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Clock

class SiopV2VpTokenCredentialsTest {
    private val holderDid = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuias8sitwHwZbwfAX"
    private val verifierDid = "did:key:z6MkwXG2WjeQnNxSoynSGYU8V9j3QzP3JSqhdmkHc6SaE69X"

    private fun credential() =
        VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.fromString("DegreeCredential")),
            issuer = Issuer.from(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromIri("did:example:subject", mapOf("degree" to JsonPrimitive("BSc"))),
        )

    private fun presentation() =
        VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri(holderDid),
            verifiableCredential = listOf(credential()),
        )

    private fun submission(path: String) =
        PresentationSubmission(
            id = "s",
            definitionId = "d",
            descriptorMap = listOf(DescriptorMap(id = "degree", format = "ldp_vc", path = path)),
        )

    private suspend fun setup(): Triple<SiopV2Service, SiopV2Session, String> {
        val kms = InMemoryKeyManagementService()
        val key = (kms.generateKey(Algorithm.Ed25519) as GenerateKeyResult.Success).keyHandle.id.value
        val service = SiopV2Service(kms = kms, httpClient = okhttp3.OkHttpClient())
        val session =
            service.createAuthorizationRequest(
                clientId = verifierDid,
                responseUri = "https://verifier.example.com/response",
                responseType = "vp_token",
            )
        return Triple(service, session, key)
    }

    @Test
    fun `vp_token embeds the credentials the submission points at`() =
        runBlocking<Unit> {
            val (service, session, key) = setup()
            val response =
                service.buildAuthorizationResponse(
                    session,
                    holderDid,
                    key,
                    presentation(),
                    submission("$.verifiableCredential[0]"),
                )
            val payload =
                Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(assertNotNull(response.vpToken).split(".")[1]))).jsonObject
            val creds = payload["vp"]!!.jsonObject["verifiableCredential"]!!.jsonArray
            assertEquals(1, creds.size)
            assertEquals("did:example:issuer", creds[0].jsonObject["issuer"]!!.jsonPrimitive.content)
            assertEquals(
                "BSc",
                creds[0]
                    .jsonObject["credentialSubject"]!!
                    .jsonObject["degree"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `submission pointing past the credentials is refused`() =
        runBlocking<Unit> {
            val (service, session, key) = setup()
            assertFailsWith<SiopV2Exception> {
                service.buildAuthorizationResponse(session, holderDid, key, presentation(), submission("$.verifiableCredential[3]"))
            }
        }
}
