package org.trustweave.credential.federation.exchange

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.model.ExchangeMessageType
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.federation.TrustChainResolver
import org.trustweave.credential.federation.TrustChainResolutionResult
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation

/**
 * OpenID Federation 1.0 exchange protocol.
 *
 * Adds federation trust-chain verification to credential presentation flows.
 * When presenting credentials, the issuer's entity identifier is resolved against
 * the configured [trustedAnchorIds] using [TrustChainResolver].
 *
 * Issuance operations (offer/request/issue) are out of scope for this protocol.
 */
class FederationExchangeProtocol(
    private val resolver: TrustChainResolver,
    private val trustedAnchorIds: Set<String>,
) : CredentialExchangeProtocol {

    override val protocolName = ExchangeProtocolName("openid-federation")

    override val capabilities = ExchangeProtocolCapabilities(
        supportedOperations = setOf(
            ExchangeOperation.REQUEST_PROOF,
            ExchangeOperation.PRESENT_PROOF,
        ),
        supportsAsync = false,
        supportsSelectiveDisclosure = false,
    )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope =
        unsupported("OFFER_CREDENTIAL")

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope =
        unsupported("REQUEST_CREDENTIAL")

    override suspend fun issue(
        request: ExchangeRequest.Issue,
    ): Pair<VerifiableCredential, ExchangeMessageEnvelope> = unsupported("ISSUE_CREDENTIAL")

    /**
     * Builds a federation-aware proof request.
     *
     * The returned envelope carries the set of trusted anchor entity identifiers
     * so the holder knows which federations are accepted.
     */
    override suspend fun requestProof(
        request: ProofExchangeRequest.Request,
    ): ExchangeMessageEnvelope {
        val anchorsArray = buildJsonArray {
            trustedAnchorIds.forEach { add(JsonPrimitive(it)) }
        }
        val requestData = buildJsonObject {
            put("trustedAnchors", anchorsArray)
            put("verifierDid", request.verifierDid.value)
        }
        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofRequest,
            messageData = requestData,
        )
    }

    /**
     * Verifies that every credential issuer in the VP has a valid trust chain
     * leading to one of the [trustedAnchorIds].
     *
     * Returns the VP unchanged if all trust chains are valid; throws if any
     * issuer cannot be verified.
     */
    override suspend fun presentProof(
        request: ProofExchangeRequest.Presentation,
    ): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        val vp = request.presentation
        val issuers = vp.verifiableCredential.map { it.issuer.id.value }.distinct()

        val chainResults = mutableMapOf<String, TrustChainResolutionResult>()
        for (issuerEntityId in issuers) {
            val result = resolver.resolve(issuerEntityId, trustedAnchorIds)
            chainResults[issuerEntityId] = result
            if (result is TrustChainResolutionResult.Failure) {
                throw TrustWeaveException.InvalidOperation(
                    code = "FEDERATION_TRUST_CHAIN_FAILED",
                    message = "Trust chain resolution failed for issuer $issuerEntityId: ${result.reason}",
                    context = mapOf(
                        "issuerEntityId" to issuerEntityId,
                        "reason" to result.reason,
                        "trustedAnchors" to trustedAnchorIds.joinToString(),
                    ),
                )
            }
            if (result is TrustChainResolutionResult.Success && !resolver.verifyChain(result.chain)) {
                throw TrustWeaveException.InvalidOperation(
                    code = "FEDERATION_TRUST_CHAIN_INVALID",
                    message = "Trust chain verification failed for issuer $issuerEntityId",
                    context = mapOf("issuerEntityId" to issuerEntityId),
                )
            }
        }

        val responseData = buildJsonObject {
            put("status", "verified")
            put("verifiedIssuers", buildJsonArray {
                issuers.forEach { add(JsonPrimitive(it)) }
            })
        }

        val envelope = ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofPresentation,
            messageData = responseData,
        )
        return Pair(vp, envelope)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun unsupported(operation: String): Nothing =
        throw TrustWeaveException.InvalidOperation(
            code = "OPERATION_NOT_SUPPORTED",
            message = "Operation $operation not supported by protocol ${protocolName.value}",
            context = mapOf(
                "protocolName" to protocolName.value,
                "operation" to operation,
                "supportedOperations" to capabilities.supportedOperations.map { it.name },
            ),
        )
}
