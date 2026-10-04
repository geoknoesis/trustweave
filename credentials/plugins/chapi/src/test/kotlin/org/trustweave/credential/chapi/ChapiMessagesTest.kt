package org.trustweave.credential.chapi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.request.AttributeRequest
import org.trustweave.credential.exchange.request.AttributeRestriction
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The CHAPI messages are what a browser hands to `navigator.credentials`, so their shape is the contract. */
class ChapiMessagesTest {
    private val service = ChapiService()
    private val chapiContext = "https://w3id.org/credential-handler/v1"

    private fun JsonObject.contexts() = getValue("@context").jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.types() = getValue("type").jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `a credential offer carries the CHAPI context, issuer and the preview attributes`() =
        runBlocking<Unit> {
            val preview =
                CredentialPreview(
                    attributes =
                        listOf(
                            CredentialAttribute("name", "Alice"),
                            CredentialAttribute("photo", "abc", mimeType = "image/png"),
                        ),
                )
            val offer = service.createCredentialOffer("did:key:issuer", preview)
            val message = offer.chapiMessage
            assertTrue(chapiContext in message.contexts())
            assertEquals(listOf("VerifiableCredential", "CredentialOffer"), message.types())
            assertEquals("did:key:issuer", message.getValue("issuer").jsonPrimitive.content)
            val attributes =
                message
                    .getValue("credentialPreview")
                    .jsonObject
                    .getValue("attributes")
                    .jsonArray
            assertEquals(
                listOf("name", "photo"),
                attributes.map {
                    it.jsonObject
                        .getValue("name")
                        .jsonPrimitive.content
                },
            )
            // The default MIME type is text/plain unless the attribute names one.
            assertEquals(
                listOf("text/plain", "image/png"),
                attributes.map {
                    it.jsonObject
                        .getValue("mime-type")
                        .jsonPrimitive.content
                },
            )
        }

    @Test
    fun `each offer gets its own id`() =
        runBlocking<Unit> {
            val preview = CredentialPreview(attributes = emptyList())
            val a = service.createCredentialOffer("did:key:issuer", preview)
            val b = service.createCredentialOffer("did:key:issuer", preview)
            assertNotEquals(a.offerId, b.offerId)
        }

    @Test
    fun `a proof request names the verifier and carries issuer restrictions as plain DIDs`() =
        runBlocking<Unit> {
            val request =
                service.createProofRequest(
                    verifierDid = "did:key:verifier",
                    requestedAttributes =
                        mapOf(
                            "a" to
                                AttributeRequest(
                                    name = "degree",
                                    restrictions = listOf(AttributeRestriction(issuerDid = Did("did:key:issuer"))),
                                ),
                        ),
                    requestedPredicates =
                        mapOf(
                            "p" to
                                AttributeRequest(
                                    name = "age",
                                    restrictions =
                                        listOf(
                                            AttributeRestriction(
                                                metadata = mapOf("p_type" to JsonPrimitive(">="), "p_value" to JsonPrimitive(18)),
                                            ),
                                        ),
                                ),
                        ),
                )
            val message = request.chapiMessage
            assertEquals(listOf("VerifiablePresentationRequest"), message.types())
            assertEquals("did:key:verifier", message.getValue("verifier").jsonPrimitive.content)
            val restriction =
                message
                    .getValue("requestedAttributes")
                    .jsonObject
                    .getValue("a")
                    .jsonObject
                    .getValue("restrictions")
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("did:key:issuer", restriction.getValue("issuer").jsonPrimitive.content)
            val predicate =
                message
                    .getValue("requestedPredicates")
                    .jsonObject
                    .getValue("p")
                    .jsonObject
            assertEquals(">=", predicate.getValue("p_type").jsonPrimitive.content)
            assertEquals("18", predicate.getValue("p_value").jsonPrimitive.content)
        }

    @Test
    fun `an empty proof request still yields well-formed empty maps`() =
        runBlocking<Unit> {
            val message = service.createProofRequest("did:key:v", emptyMap(), emptyMap()).chapiMessage
            assertEquals(0, message.getValue("requestedAttributes").jsonObject.size)
            assertEquals(0, message.getValue("requestedPredicates").jsonObject.size)
            assertTrue(message.getValue("@context") is JsonArray)
        }

    private fun credential(id: String? = "urn:uuid:cred-1") =
        VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:key:issuer")),
            issuanceDate =
                kotlinx.datetime.Clock.System
                    .now(),
            credentialSubject = CredentialSubject(id = Iri("did:key:holder"), claims = emptyMap()),
        )

    @Test
    fun `storing a credential embeds it and keeps its id, or mints one when it has none`() =
        runBlocking<Unit> {
            val stored = service.storeCredential(credential(), "did:key:holder")
            assertEquals("urn:uuid:cred-1", stored.credentialId)
            assertEquals("did:key:holder", stored.holderDid)
            val embedded = stored.chapiMessage.getValue("credential").jsonObject
            assertEquals("urn:uuid:cred-1", embedded.getValue("id").jsonPrimitive.content)
            assertTrue(chapiContext in stored.chapiMessage.contexts())

            val anonymous = service.storeCredential(credential(id = null), "did:key:holder")
            assertTrue(anonymous.credentialId.isNotBlank())
        }

    @Test
    fun `presenting a proof embeds the presentation and names the verifier`() =
        runBlocking<Unit> {
            val presentation =
                VerifiablePresentation(
                    type = listOf(CredentialType.fromString("VerifiablePresentation")),
                    holder = Iri("did:key:holder"),
                    verifiableCredential = listOf(credential()),
                )
            val result = service.presentProof(presentation, "did:key:verifier")
            assertEquals(
                "did:key:verifier",
                result.chapiMessage
                    .getValue("verifier")
                    .jsonPrimitive.content,
            )
            assertEquals(listOf("VerifiablePresentation"), result.chapiMessage.types())
            assertEquals(
                "did:key:holder",
                result.chapiMessage
                    .getValue("presentation")
                    .jsonObject
                    .getValue("holder")
                    .jsonPrimitive.content,
            )
        }
}
