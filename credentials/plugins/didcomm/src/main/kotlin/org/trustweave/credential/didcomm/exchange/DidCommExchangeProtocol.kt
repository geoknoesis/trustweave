package org.trustweave.credential.didcomm.exchange

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.credential.didcomm.DidCommService
import org.trustweave.credential.didcomm.protocol.CredentialProtocol
import org.trustweave.credential.didcomm.protocol.ProofProtocol
import org.trustweave.credential.didcomm.protocol.RequestedAttribute
import org.trustweave.credential.didcomm.protocol.RequestedPredicate
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.didcomm.protocol.AttributeRestriction as DidCommAttributeRestriction
import org.trustweave.credential.didcomm.protocol.ProofRequest as DidCommProofRequest

/**
 * DIDComm V2 implementation of CredentialExchangeProtocol.
 *
 * Provides credential exchange operations using DIDComm V2 messaging protocol.
 * Supports all exchange operations: offer, request, issue, proof request, and proof presentation.
 *
 * **Example Usage:**
 * ```kotlin
 * val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid, secretResolver)
 * val protocol = DidCommExchangeProtocol(didCommService)
 *
 * val registry = ExchangeProtocolRegistries.default()
 * registry.register(protocol)
 *
 * val (vc, envelope) = registry.issue(ExchangeProtocolName.DidComm, request)
 * ```
 */
class DidCommExchangeProtocol(
    private val didCommService: DidCommService,
) : CredentialExchangeProtocol {
    override val protocolName = ExchangeProtocolName.DidComm

    override val capabilities =
        ExchangeProtocolCapabilities(
            supportedOperations =
                setOf(
                    ExchangeOperation.OFFER_CREDENTIAL,
                    ExchangeOperation.REQUEST_CREDENTIAL,
                    ExchangeOperation.ISSUE_CREDENTIAL,
                    ExchangeOperation.REQUEST_PROOF,
                    ExchangeOperation.PRESENT_PROOF,
                ),
            supportsAsync = true,
            supportsMultipleCredentials = true,
            supportsSelectiveDisclosure = true,
            requiresTransportSecurity = true,
        )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
        // Extract DID strings from typed DIDs
        val fromDid = request.issuerDid.value
        val toDid = request.holderDid.value

        // Extract thread ID from options
        val thid =
            request.options.metadata["thid"]
                ?.jsonPrimitive
                ?.content

        // Create DIDComm credential offer message
        val message =
            CredentialProtocol.createCredentialOffer(
                fromDid = fromDid,
                toDid = toDid,
                credentialPreview = request.credentialPreview,
                thid = thid,
            )

        // Convert DIDComm message to JSON
        val messageJson = message.toJsonObject()

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Offer,
            messageData = messageJson,
            metadata =
                mapOf(
                    "messageId" to JsonPrimitive(message.id),
                    "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                    "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                    "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true)),
                ),
        )
    }

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope {
        // Extract DID strings from typed DIDs
        val fromDid = request.holderDid.value
        val toDid = request.issuerDid.value

        // Get the offer message to extract thread ID
        // Note: offerId is in the request, but we need to resolve it to get the message
        // This may require accessing didCommService.getMessage(request.offerId.value)
        val thid =
            request.options.metadata["thid"]
                ?.jsonPrimitive
                ?.content
                ?: throw IllegalArgumentException("Thread ID (thid) required in options.metadata for DIDComm request")

        // Create DIDComm credential request message
        val message =
            CredentialProtocol.createCredentialRequest(
                fromDid = fromDid,
                toDid = toDid,
                thid = thid,
            )

        // Convert DIDComm message to JSON
        val messageJson = message.toJsonObject()

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Request,
            messageData = messageJson,
            metadata =
                mapOf(
                    "messageId" to JsonPrimitive(message.id),
                    "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                    "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                    "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true)),
                ),
        )
    }

    override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> {
        // Extract DID strings from typed DIDs
        val fromDid = request.issuerDid.value
        val toDid = request.holderDid.value

        // Get the request message to extract thread ID
        val thid =
            request.options.metadata["thid"]
                ?.jsonPrimitive
                ?.content
                ?: throw IllegalArgumentException("Thread ID (thid) required in options.metadata for DIDComm issue")

        // Create DIDComm credential issue message
        // Note: CredentialProtocol.createCredentialIssue expects Credential type
        // This needs to be updated to accept VerifiableCredential
        // For now, we'll need to convert or update the protocol helper
        val message =
            CredentialProtocol.createCredentialIssue(
                fromDid = fromDid,
                toDid = toDid,
                credential = request.credential, // This may need conversion
                thid = thid,
            )

        // Convert DIDComm message to JSON
        val messageJson = Json.encodeToJsonElement(message) as JsonObject

        val envelope =
            ExchangeMessageEnvelope(
                protocolName = protocolName,
                messageType = ExchangeMessageType.Issue,
                messageData = messageJson,
                metadata =
                    mapOf(
                        "messageId" to JsonPrimitive(message.id),
                        "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                        "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                        "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true)),
                    ),
            )

        return Pair(request.credential, envelope)
    }

    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        // Extract DID strings from typed DIDs
        val fromDid = request.verifierDid.value
        val toDid = request.proverDid.value

        // Convert protocol-agnostic proof request to DIDComm-specific format
        val didCommProofRequest =
            DidCommProofRequest(
                name = request.proofRequest.name,
                version = request.proofRequest.version,
                requestedAttributes =
                    request.proofRequest.requestedAttributes.mapValues { (key, attr) ->
                        RequestedAttribute(
                            name = attr.name,
                            restrictions =
                                attr.restrictions.map { restriction ->
                                    DidCommAttributeRestriction(
                                        issuerDid = restriction.issuerDid?.value,
                                        schemaId = restriction.schemaId?.value,
                                        credentialDefinitionId = restriction.metadata["credentialDefinitionId"]?.jsonPrimitive?.content,
                                    )
                                },
                        )
                    },
                requestedPredicates =
                    parseRequestedPredicates(
                        request.proofRequest.options.metadata["requestedPredicates"]
                            ?: request.options.metadata["requestedPredicates"],
                    ),
                goalCode =
                    request.options.metadata["goalCode"]
                        ?.jsonPrimitive
                        ?.content,
                willConfirm =
                    request.options.metadata["willConfirm"]
                        ?.jsonPrimitive
                        ?.boolean ?: true,
            )

        // Extract thread ID from options
        val thid =
            request.options.metadata["thid"]
                ?.jsonPrimitive
                ?.content

        // Create DIDComm proof request message
        val message =
            ProofProtocol.createProofRequest(
                fromDid = fromDid,
                toDid = toDid,
                proofRequest = didCommProofRequest,
                thid = thid,
            )

        // Convert DIDComm message to JSON
        val messageJson = message.toJsonObject()

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofRequest,
            messageData = messageJson,
            metadata =
                mapOf(
                    "messageId" to JsonPrimitive(message.id),
                    "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                    "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                    "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true)),
                ),
        )
    }

    override suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        // Extract DID strings from typed DIDs
        val fromDid = request.proverDid.value
        val toDid = request.verifierDid.value

        // Get the proof request message to extract thread ID
        val thid =
            request.options.metadata["thid"]
                ?.jsonPrimitive
                ?.content
                ?: throw IllegalArgumentException("Thread ID (thid) required in options.metadata for DIDComm presentation")

        // Create DIDComm proof presentation message
        // Note: ProofProtocol.createProofPresentation expects Credential type
        // This needs to be updated to accept VerifiablePresentation
        val message =
            ProofProtocol.createProofPresentation(
                fromDid = fromDid,
                toDid = toDid,
                presentation = request.presentation, // This may need conversion
                thid = thid,
            )

        // Convert DIDComm message to JSON
        val messageJson = Json.encodeToJsonElement(message) as JsonObject

        val envelope =
            ExchangeMessageEnvelope(
                protocolName = protocolName,
                messageType = ExchangeMessageType.ProofPresentation,
                messageData = messageJson,
                metadata =
                    mapOf(
                        "messageId" to JsonPrimitive(message.id),
                        "fromKeyId" to (request.options.metadata["fromKeyId"] ?: JsonNull),
                        "toKeyId" to (request.options.metadata["toKeyId"] ?: JsonNull),
                        "encrypt" to (request.options.metadata["encrypt"] ?: JsonPrimitive(true)),
                    ),
            )

        return Pair(request.presentation, envelope)
    }

    /**
     * Reads predicates from `options.metadata["requestedPredicates"]`:
     * `{ "<referent>": { "name": "age", "p_type": ">=", "p_value": 18, "restrictions": [ ... ] } }`
     * (camelCase `pType` / `pValue` / `issuerDid` / `schemaId` / `credentialDefinitionId` are also
     * accepted). A malformed entry is rejected — dropping a requested predicate would let a proof
     * satisfy less than the verifier asked for.
     */
    private fun parseRequestedPredicates(element: JsonElement?): Map<String, RequestedPredicate> {
        if (element == null || element is JsonNull) return emptyMap()
        val obj =
            element as? JsonObject
                ?: throw IllegalArgumentException("requestedPredicates must be a JSON object keyed by referent")
        return obj.mapValues { (referent, value) ->
            val p =
                value as? JsonObject
                    ?: throw IllegalArgumentException("requestedPredicates.$referent must be an object")

            fun str(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { k -> (p[k] as? JsonPrimitive)?.takeIf { it.isString }?.content }
            val name = str("name") ?: throw IllegalArgumentException("requestedPredicates.$referent.name is required")
            val pType =
                str("p_type", "pType")
                    ?: throw IllegalArgumentException("requestedPredicates.$referent.p_type is required")
            require(pType in SUPPORTED_PREDICATE_TYPES) {
                "requestedPredicates.$referent.p_type '$pType' is not one of $SUPPORTED_PREDICATE_TYPES"
            }
            val pValue =
                listOf("p_value", "pValue").firstNotNullOfOrNull { k ->
                    (p[k] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
                } ?: throw IllegalArgumentException("requestedPredicates.$referent.p_value must be an integer")
            val restrictions =
                (p["restrictions"] as? JsonArray).orEmpty().map { r ->
                    val ro =
                        r as? JsonObject
                            ?: throw IllegalArgumentException("requestedPredicates.$referent.restrictions entries must be objects")

                    fun rs(vararg keys: String): String? =
                        keys.firstNotNullOfOrNull { k -> (ro[k] as? JsonPrimitive)?.takeIf { it.isString }?.content }
                    DidCommAttributeRestriction(
                        issuerDid = rs("issuerDid", "issuer_did"),
                        schemaId = rs("schemaId", "schema_id"),
                        credentialDefinitionId = rs("credentialDefinitionId", "cred_def_id"),
                    )
                }
            RequestedPredicate(name = name, pType = pType, pValue = pValue, restrictions = restrictions)
        }
    }

    private companion object {
        val SUPPORTED_PREDICATE_TYPES = setOf(">=", ">", "<=", "<", "==")
    }
}
