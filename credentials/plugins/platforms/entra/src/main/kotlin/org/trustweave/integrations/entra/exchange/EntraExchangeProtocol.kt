package org.trustweave.integrations.entra.exchange

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.integrations.entra.EntraException
import org.trustweave.integrations.entra.EntraIssuanceClient
import org.trustweave.integrations.entra.EntraPresentationClient
import org.trustweave.integrations.entra.EntraRequestedCredential
import org.trustweave.integrations.entra.EntraRequestedCredentialConfiguration
import org.trustweave.integrations.entra.EntraValidationConfig
import java.util.UUID

/**
 * Microsoft Entra Verified ID exchange protocol.
 *
 * Bridges TrustWeave's protocol-agnostic [ExchangeRequest] / [ProofExchangeRequest] model
 * to the Entra Verified ID Request Service. Issuance produces a deep-link/QR-code; verification
 * produces a proof request deep-link/QR-code; the credential itself is delivered out-of-band
 * via Entra's webhook callback (parse using [EntraPresentationClient.parseCallbackPayload]).
 *
 * **Supported operations:**
 * - [ExchangeOperation.OFFER_CREDENTIAL] — emits an Entra issuance request envelope
 * - [ExchangeOperation.REQUEST_PROOF] — emits an Entra presentation request envelope
 *
 * Synchronous holder-side request/issue and presentation are delivered asynchronously
 * via webhook and are therefore not implemented here as synchronous SPI calls.
 *
 * **Required options.metadata for OFFER_CREDENTIAL:**
 * - `manifestUrl` (String) — the credential contract manifest URL
 * - `credentialType` (String) — the Entra credential type
 * - `callbackUrl` (String) — webhook URL to receive lifecycle callbacks
 * - `clientName` (String) — display name shown in the wallet
 * - `claims` (JsonObject, optional) — claim map embedded in the issuance request
 * - `state` (String, optional) — opaque correlation state (defaults to a UUID)
 *
 * **Required options.metadata for REQUEST_PROOF:**
 * - `callbackUrl` (String)
 * - `clientName` (String)
 * - `credentialType` (String) — type of credential being requested
 * - `acceptedIssuers` (JsonArray<String>, optional)
 * - `purpose` (String, optional)
 * - `state` (String, optional)
 */
class EntraExchangeProtocol(
    private val issuanceClient: EntraIssuanceClient,
    private val presentationClient: EntraPresentationClient,
) : CredentialExchangeProtocol {

    override val protocolName: ExchangeProtocolName = ExchangeProtocolName("entra")

    override val capabilities: ExchangeProtocolCapabilities = ExchangeProtocolCapabilities(
        supportedOperations = setOf(
            ExchangeOperation.OFFER_CREDENTIAL,
            ExchangeOperation.REQUEST_PROOF,
        ),
        supportsAsync = true,
        supportsMultipleCredentials = true,
        supportsSelectiveDisclosure = false,
        requiresTransportSecurity = true,
    )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
        val md = request.options.metadata
        val manifestUrl = md.requireString("manifestUrl")
        val credentialType = md.requireString("credentialType")
        val callbackUrl = md.requireString("callbackUrl")
        val clientName = md.requireString("clientName")
        val state = md.optString("state") ?: UUID.randomUUID().toString()
        val purpose = md.optString("purpose")
        val claims = md["claims"]
            ?.let { it as? JsonObject }
            ?.mapValues { it.value.jsonPrimitive.content }

        val body = issuanceClient.buildRequestBody(
            credentialType = credentialType,
            manifestUrl = manifestUrl,
            callbackUrl = callbackUrl,
            state = state,
            clientName = clientName,
            claims = claims,
            purpose = purpose,
        )
        val response = issuanceClient.createRequest(body)

        val messageData = buildJsonObject {
            put("requestId", response.requestId)
            put("url", response.url)
            put("expiry", response.expiry)
            response.qrCode?.let { put("qrCode", it) }
        }

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.Offer,
            messageData = messageData,
            metadata = mapOf(
                "requestId" to JsonPrimitive(response.requestId),
                "state" to JsonPrimitive(state),
            ),
        )
    }

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope =
        unsupported("REQUEST_CREDENTIAL")

    override suspend fun issue(
        request: ExchangeRequest.Issue,
    ): Pair<VerifiableCredential, ExchangeMessageEnvelope> = unsupported("ISSUE_CREDENTIAL")

    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        val md = request.options.metadata
        val callbackUrl = md.requireString("callbackUrl")
        val clientName = md.requireString("clientName")
        val credentialType = md.requireString("credentialType")
        val purpose = md.optString("purpose")
        val state = md.optString("state") ?: UUID.randomUUID().toString()
        val acceptedIssuers = (md["acceptedIssuers"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

        val requested = EntraRequestedCredential(
            type = credentialType,
            purpose = purpose,
            acceptedIssuers = acceptedIssuers,
            configuration = EntraRequestedCredentialConfiguration(
                validation = EntraValidationConfig(),
            ),
        )
        val body = presentationClient.buildRequestBody(
            requestedCredentials = listOf(requested),
            callbackUrl = callbackUrl,
            state = state,
            clientName = clientName,
            purpose = purpose,
        )
        val response = presentationClient.createRequest(body)

        val messageData = buildJsonObject {
            put("requestId", response.requestId)
            put("url", response.url)
            put("expiry", response.expiry)
            response.qrCode?.let { put("qrCode", it) }
        }
        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofRequest,
            messageData = messageData,
            metadata = mapOf(
                "requestId" to JsonPrimitive(response.requestId),
                "state" to JsonPrimitive(state),
            ),
        )
    }

    override suspend fun presentProof(
        request: ProofExchangeRequest.Presentation,
    ): Pair<VerifiablePresentation, ExchangeMessageEnvelope> = unsupported("PRESENT_PROOF")

    @Suppress("NOTHING_TO_INLINE")
    private inline fun unsupported(operation: String): Nothing =
        throw TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation $operation not supported by protocol ${protocolName.value}. " +
                "Entra Verified ID delivers this step out-of-band via webhook callbacks.",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to operation,
                "supportedOperations" to capabilities.supportedOperations.map { it.name },
            ),
        )

    private fun Map<String, JsonElement>.requireString(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull
            ?: throw EntraException.MissingOption(option = key)

    private fun Map<String, JsonElement>.optString(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}

/**
 * Convenience builder so callers do not need to construct an [ExchangeOptions] from scratch.
 */
fun entraIssuanceOptions(
    manifestUrl: String,
    credentialType: String,
    callbackUrl: String,
    clientName: String,
    state: String? = null,
    purpose: String? = null,
    claims: Map<String, String> = emptyMap(),
): ExchangeOptions {
    val builder = ExchangeOptions.builder()
        .addMetadata("manifestUrl", manifestUrl)
        .addMetadata("credentialType", credentialType)
        .addMetadata("callbackUrl", callbackUrl)
        .addMetadata("clientName", clientName)
    state?.let { builder.addMetadata("state", it) }
    purpose?.let { builder.addMetadata("purpose", it) }
    if (claims.isNotEmpty()) {
        val claimsJson = buildJsonObject {
            claims.forEach { (k, v) -> put(k, v) }
        }
        builder.addMetadata("claims", claimsJson)
    }
    return builder.build()
}
