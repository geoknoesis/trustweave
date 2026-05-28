package org.trustweave.credential.oidc4vp.exchange

import org.trustweave.credential.exchange.*
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.oidc4vp.Oidc4VpService
import org.trustweave.credential.oidc4vp.models.PermissionRequest
import org.trustweave.credential.oidc4vp.models.PresentableCredential
import org.trustweave.credential.oidc4vp.session.InMemorySessionStore
import org.trustweave.credential.oidc4vp.session.SessionStore
import kotlinx.serialization.json.*

/**
 * OIDC4VP (OpenID for Verifiable Presentations) implementation of
 * [CredentialExchangeProtocol].
 *
 * Supports [ExchangeOperation.REQUEST_PROOF] and [ExchangeOperation.PRESENT_PROOF].
 * Issuance operations are not supported — use OID4VCI for those.
 *
 * ## Flow
 * 1. Verifier shares an `openid4vp://` authorization URL (QR code, deep link, etc.)
 * 2. Wallet calls `requestProof()` with the URL — the service parses the request and
 *    stores the [PermissionRequest] keyed by its `requestId`.
 * 3. Wallet selects credentials and calls `presentProof()` with the `requestId`,
 *    selected credential IDs, and holder key info.
 * 4. The service builds a VP token and submits it to the verifier's `response_uri`.
 */
class Oidc4VpExchangeProtocol(
    private val oidc4vpService: Oidc4VpService,
    private val sessionStore: SessionStore = InMemorySessionStore(),
) : CredentialExchangeProtocol {

    override val protocolName = ExchangeProtocolName.Oidc4Vp

    override val capabilities = ExchangeProtocolCapabilities(
        supportedOperations = setOf(
            ExchangeOperation.REQUEST_PROOF,
            ExchangeOperation.PRESENT_PROOF,
        ),
        supportsAsync = false,
        supportsMultipleCredentials = true,
        supportsSelectiveDisclosure = true,
        requiresTransportSecurity = true,
    )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
        throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "OFFER_CREDENTIAL not supported by OID4VP — use OID4VCI instead",
            context = mapOf("protocol" to protocolName.value),
        )
    }

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope {
        throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "REQUEST_CREDENTIAL not supported by OID4VP — use OID4VCI instead",
            context = mapOf("protocol" to protocolName.value),
        )
    }

    override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> {
        throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "ISSUE_CREDENTIAL not supported by OID4VP — use OID4VCI instead",
            context = mapOf("protocol" to protocolName.value),
        )
    }

    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        val authorizationUrl = request.options.metadata["authorizationUrl"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("authorizationUrl required in options.metadata")

        val permissionRequest = oidc4vpService.parseAuthorizationUrl(authorizationUrl)
        sessionStore.put(permissionRequest.requestId, permissionRequest)

        val requestJson = buildJsonObject {
            put("requestId", permissionRequest.requestId)
            put("authorizationRequest", buildJsonObject {
                permissionRequest.authorizationRequest.responseUri?.let { put("responseUri", it) }
                permissionRequest.authorizationRequest.redirectUri?.let { put("redirectUri", it) }
                permissionRequest.authorizationRequest.clientId?.let { put("clientId", it) }
                permissionRequest.authorizationRequest.requestUri?.let { put("requestUri", it) }
                permissionRequest.authorizationRequest.nonce?.let { put("nonce", it) }
                permissionRequest.authorizationRequest.state?.let { put("state", it) }
                permissionRequest.authorizationRequest.responseMode?.let { put("responseMode", it) }
            })
            permissionRequest.verifierUrl?.let { put("verifierUrl", it) }
            put("requestedCredentialTypes", JsonArray(permissionRequest.requestedCredentialTypes.map { JsonPrimitive(it) }))
        }

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofRequest,
            messageData = requestJson,
            metadata = mapOf("requestId" to JsonPrimitive(permissionRequest.requestId)),
        )
    }

    override suspend fun presentProof(
        request: ProofExchangeRequest.Presentation,
    ): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        val requestId = request.requestId.value
        val permissionRequest = sessionStore.get(requestId)
            ?: throw IllegalStateException(
                "No pending OID4VP request found for requestId=$requestId. " +
                    "Call requestProof() first.",
            )

        val holderDid = request.proverDid.value
        val keyId = request.options.metadata["keyId"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("keyId required in options.metadata")

        val selectedCredentialIds = request.options.metadata["selectedCredentials"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        val vcList = request.presentation.verifiableCredential

        val selectedCredentials = selectedCredentialIds.mapIndexed { index, id ->
            PresentableCredential(
                credentialId = id,
                credential = vcList.getOrNull(index)
                    ?: throw IllegalArgumentException("No credential provided for credentialId=$id"),
                credentialType = vcList.getOrNull(index)
                    ?.type?.firstOrNull()?.value ?: "VerifiableCredential",
            )
        }

        // selectedFields keyed by credentialId → align to selectedCredentials order
        val selectedFieldsMap = request.options.metadata["selectedFields"]
            ?.jsonObject
            ?.entries
            ?.associate { (k, v) -> k to v.jsonArray.map { it.jsonPrimitive.content } }
            ?: emptyMap()
        val selectedFields: List<List<String>> = selectedCredentialIds.map { id ->
            selectedFieldsMap[id] ?: emptyList()
        }

        val permissionResponse = oidc4vpService.createPermissionResponse(
            permissionRequest = permissionRequest,
            holderDid = holderDid,
            keyId = keyId,
            selectedCredentials = selectedCredentials,
            selectedFields = selectedFields,
        )

        oidc4vpService.submitPermissionResponse(permissionResponse)
        sessionStore.remove(requestId)

        val vp = VerifiablePresentation(
            type = listOf(org.trustweave.credential.model.CredentialType.Custom("VerifiablePresentation")),
            holder = org.trustweave.core.identifiers.Iri(holderDid),
            verifiableCredential = vcList,
        )

        val responseJson = buildJsonObject {
            put("responseId", permissionResponse.responseId)
            put("requestId", permissionResponse.requestId)
            put("vpToken", permissionResponse.vpToken)
            permissionResponse.state?.let { put("state", it) }
        }

        return Pair(
            vp,
            ExchangeMessageEnvelope(
                protocolName = protocolName,
                messageType = ExchangeMessageType.ProofPresentation,
                messageData = responseJson,
                metadata = mapOf("responseId" to JsonPrimitive(permissionResponse.responseId)),
            ),
        )
    }
}
