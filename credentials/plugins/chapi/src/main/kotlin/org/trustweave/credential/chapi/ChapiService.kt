package org.trustweave.credential.chapi

import org.trustweave.credential.chapi.models.*
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.*

/**
 * CHAPI (Credential Handler API) service.
 *
 * CHAPI is a browser-based API for credential wallet interactions.
 * This service provides a server-side wrapper for CHAPI operations,
 * generating CHAPI-compatible messages that can be used in browser environments.
 *
 * **CHAPI Flow:**
 * 1. Issuer creates credential offer (wallet.store())
 * 2. Verifier requests proof (wallet.get())
 * 3. Wallet handles the interaction
 *
 * **Example Usage:**
 * ```kotlin
 * val service = ChapiService()
 *
 * val offer = service.createCredentialOffer(
 *     issuerDid = "did:key:issuer",
 *     credentialPreview = preview
 * )
 * ```
 *
 * **Browser Integration:**
 * In a browser environment, use the generated CHAPI messages with:
 * ```javascript
 * navigator.credentials.store(offer.chapiMessage)
 * navigator.credentials.get(proofRequest.chapiMessage)
 * ```
 */
class ChapiService {
    private val offers = mutableMapOf<String, ChapiOffer>()
    private val proofRequests = mutableMapOf<String, ChapiProofRequest>()

    /**
     * Creates a CHAPI credential offer.
     *
     * The offer can be used with `navigator.credentials.store()` in the browser.
     *
     * @param issuerDid Issuer DID
     * @param credentialPreview Credential preview
     * @return CHAPI offer
     */
    suspend fun createCredentialOffer(
        issuerDid: String,
        credentialPreview: CredentialPreview
    ): ChapiOffer = withContext(Dispatchers.IO) {
        val offerId = UUID.randomUUID().toString()

        // Create CHAPI-compatible credential request
        // Format: https://w3c.github.io/webappsec-credential-management/#credential
        val chapiMessage = buildJsonObject {
            put("@context", JsonArray(listOf(
                JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
                JsonPrimitive("https://w3id.org/credential-handler/v1")
            )))
            put("type", JsonArray(listOf(
                JsonPrimitive("VerifiableCredential"),
                JsonPrimitive("CredentialOffer")
            )))
            put("credentialPreview", buildJsonObject {
                put("@type", "https://didcomm.org/issue-credential/3.0/credential-preview")
                put("attributes", JsonArray(
                    credentialPreview.attributes.map { attr ->
                        buildJsonObject {
                            put("name", attr.name)
                            put("mime-type", attr.mimeType ?: "text/plain")
                            put("value", attr.value)
                        }
                    }
                ))
            })
            put("issuer", issuerDid)
            credentialPreview.options.metadata["goalCode"]?.let { 
                if (it is kotlinx.serialization.json.JsonPrimitive) {
                    put("goalCode", it.content)
                } else {
                    put("goalCode", it)
                }
            }
            credentialPreview.options.metadata["replacementId"]?.let { 
                if (it is kotlinx.serialization.json.JsonPrimitive) {
                    put("replacementId", it.content)
                } else {
                    put("replacementId", it)
                }
            }
        }

        val offer = ChapiOffer(
            offerId = offerId,
            issuerDid = issuerDid,
            credentialPreview = credentialPreview,
            chapiMessage = chapiMessage
        )

        offers[offerId] = offer
        offer
    }

    /**
     * Stores a credential via CHAPI.
     *
     * This creates a CHAPI message that can be used with `navigator.credentials.store()`.
     *
     * @param credential The verifiable credential to store
     * @param holderDid Holder DID
     * @return Store result
     */
    suspend fun storeCredential(
        credential: VerifiableCredential,
        holderDid: String
    ): ChapiStoreResult = withContext(Dispatchers.IO) {
        val credentialId = credential.id?.value ?: UUID.randomUUID().toString()

        // Create CHAPI-compatible credential
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val credentialJson = json.encodeToJsonElement(
            VerifiableCredential.serializer(),
            credential
        )

        val chapiMessage = buildJsonObject {
            put("@context", JsonArray(listOf(
                JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
                JsonPrimitive("https://w3id.org/credential-handler/v1")
            )))
            put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
            put("credential", credentialJson)
        }

        ChapiStoreResult(
            credentialId = credentialId,
            holderDid = holderDid,
            credential = credential,
            chapiMessage = chapiMessage
        )
    }

    /**
     * Creates a CHAPI proof request.
     *
     * The request can be used with `navigator.credentials.get()` in the browser.
     *
     * @param verifierDid Verifier DID
     * @param requestedAttributes Requested attributes
     * @param requestedPredicates Requested predicates
     * @return CHAPI proof request
     */
    suspend fun createProofRequest(
        verifierDid: String,
        requestedAttributes: Map<String, org.trustweave.credential.exchange.request.AttributeRequest>,
        requestedPredicates: Map<String, org.trustweave.credential.exchange.request.AttributeRequest>
    ): ChapiProofRequest = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()

        // Create CHAPI-compatible proof request
        val chapiMessage = buildJsonObject {
            put("@context", JsonArray(listOf(
                JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
                JsonPrimitive("https://w3id.org/credential-handler/v1")
            )))
            put("type", JsonArray(listOf(JsonPrimitive("VerifiablePresentationRequest"))))
            put("verifier", verifierDid)
            put("requestedAttributes", JsonObject(
                requestedAttributes.mapValues { (_, attr) ->
                    buildJsonObject {
                        put("name", attr.name)
                        put("restrictions", JsonArray(
                            attr.restrictions.map { restriction ->
                                buildJsonObject {
                                    restriction.issuerDid?.let { put("issuer", JsonPrimitive(it.toString())) }
                                    restriction.schemaId?.let { put("schema_id", JsonPrimitive(it.toString())) }
                                    restriction.metadata["credentialDefinitionId"]?.let { put("cred_def_id", it) }
                                }
                            }
                        ))
                    }
                }
            ))
            put("requestedPredicates", JsonObject(
                requestedPredicates.mapValues { (_, pred) ->
                    buildJsonObject {
                        put("name", pred.name)
                        // Predicate type and value should be in restrictions metadata
                        pred.restrictions.firstOrNull()?.let { restriction ->
                            restriction.metadata["p_type"]?.let { put("p_type", it) }
                            restriction.metadata["p_value"]?.let { put("p_value", it) }
                        }
                        put("restrictions", JsonArray(
                            pred.restrictions.map { restriction ->
                                buildJsonObject {
                                    restriction.issuerDid?.let { put("issuer", JsonPrimitive(it.toString())) }
                                    restriction.schemaId?.let { put("schema_id", JsonPrimitive(it.toString())) }
                                    restriction.metadata["credentialDefinitionId"]?.let { put("cred_def_id", it) }
                                }
                            }
                        ))
                    }
                }
            ))
        }

        val proofRequest = ChapiProofRequest(
            requestId = requestId,
            verifierDid = verifierDid,
            requestedAttributes = requestedAttributes,
            requestedPredicates = requestedPredicates,
            chapiMessage = chapiMessage
        )

        proofRequests[requestId] = proofRequest
        proofRequest
    }

    /**
     * Presents a proof via CHAPI.
     *
     * This creates a CHAPI message that can be used with `navigator.credentials.get()`.
     *
     * @param presentation The verifiable presentation
     * @param verifierDid Verifier DID
     * @return Presentation result
     */
    suspend fun presentProof(
        presentation: VerifiablePresentation,
        verifierDid: String
    ): ChapiPresentationResult = withContext(Dispatchers.IO) {
        val presentationId = UUID.randomUUID().toString()

        // Create CHAPI-compatible presentation
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val presentationJson = json.encodeToJsonElement(
            VerifiablePresentation.serializer(),
            presentation
        )

        val chapiMessage = buildJsonObject {
            put("@context", JsonArray(listOf(
                JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
                JsonPrimitive("https://w3id.org/credential-handler/v1")
            )))
            put("type", JsonArray(listOf(JsonPrimitive("VerifiablePresentation"))))
            put("presentation", presentationJson)
            put("verifier", verifierDid)
        }

        ChapiPresentationResult(
            presentationId = presentationId,
            presentation = presentation,
            verifierDid = verifierDid,
            chapiMessage = chapiMessage
        )
    }
}

