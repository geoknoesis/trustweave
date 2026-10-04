package org.trustweave.credential.eudiw.exchange

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.eudiw.EudiwOid4VpProfile
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

/**
 * EUDIW (EU Digital Identity Wallet) exchange protocol.
 *
 * Validates proof request/presentation flows against the EUDIW Architecture Reference
 * Framework (ARF) constraints: `direct_post`/`direct_post.jwt` response mode,
 * permitted `client_id_scheme` values, and nonce freshness.
 *
 * Actual credential transport is delegated to OID4VP; this protocol layer
 * enforces the EUDIW compliance constraints on top.
 */
class EudiwExchangeProtocol : CredentialExchangeProtocol {
    override val protocolName = ExchangeProtocolName("eudiw")

    override val capabilities =
        ExchangeProtocolCapabilities(
            supportedOperations =
                setOf(
                    ExchangeOperation.REQUEST_PROOF,
                    ExchangeOperation.PRESENT_PROOF,
                ),
            supportsAsync = false,
            supportsSelectiveDisclosure = true,
        )

    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope = unsupported("OFFER_CREDENTIAL")

    override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope = unsupported("REQUEST_CREDENTIAL")

    override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> =
        unsupported("ISSUE_CREDENTIAL")

    /**
     * Builds a EUDIW-compliant proof request envelope.
     *
     * Validates that the `response_mode` and `client_id_scheme` values in
     * `request.options.metadata` conform to the EUDIW ARF profile, then
     * returns an envelope advertising supported EUDIW response modes.
     */
    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope {
        val responseMode =
            request.options.metadata["response_mode"]?.let {
                (it as? JsonPrimitive)?.content
            }
        val clientIdScheme =
            request.options.metadata["client_id_scheme"]?.let {
                (it as? JsonPrimitive)?.content
            }

        val responseModeResult = EudiwOid4VpProfile.validateResponseMode(responseMode)
        val clientIdSchemeResult = EudiwOid4VpProfile.validateClientIdScheme(clientIdScheme)

        val allViolations = responseModeResult.violations + clientIdSchemeResult.violations
        if (allViolations.isNotEmpty()) {
            throw TrustWeaveException.InvalidOperation(
                code = "EUDIW_PROFILE_VIOLATION",
                message = "Proof request does not conform to EUDIW ARF profile",
                context =
                    mapOf(
                        "violations" to allViolations.joinToString("; "),
                        "response_mode" to (responseMode ?: "null"),
                        "client_id_scheme" to (clientIdScheme ?: "null"),
                    ),
            )
        }

        val requestData =
            buildJsonObject {
                put("profile", "eudiw")
                put("response_mode", responseMode ?: "direct_post")
                put("client_id_scheme", clientIdScheme ?: "did")
                put("verifier_did", request.verifierDid.value)
            }

        return ExchangeMessageEnvelope(
            protocolName = protocolName,
            messageType = ExchangeMessageType.ProofRequest,
            messageData = requestData,
        )
    }

    /**
     * Checks a presentation against the EUDIW profile and wraps it in an envelope.
     *
     * Enforced, failing with `EUDIW_PROFILE_VIOLATION`: the presentation carries at least one
     * credential and a proof (the holder binding); `response_mode` / `client_id_scheme` in
     * `options.metadata`, when supplied, are profile-conformant; and a supplied `nonce_issued_at`
     * (epoch seconds) is not in the future and not older than
     * [EudiwOid4VpProfile.MAX_NONCE_AGE_SECONDS].
     *
     * **This is a profile check, not signature verification.** The envelope reports
     * `status = "profile_checked"`; verify the presentation itself with the credential service.
     */
    override suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope> {
        val vp = request.presentation
        val metadata = request.options.metadata
        val violations = mutableListOf<String>()
        if (vp.verifiableCredential.isEmpty()) violations += "presentation carries no credential"
        if (vp.proof == null) violations += "presentation has no proof (holder binding is mandatory)"
        metadata["response_mode"]?.let {
            violations += EudiwOid4VpProfile.validateResponseMode((it as? JsonPrimitive)?.content).violations
        }
        metadata["client_id_scheme"]?.let {
            violations += EudiwOid4VpProfile.validateClientIdScheme((it as? JsonPrimitive)?.content).violations
        }
        metadata["nonce_issued_at"]?.let { raw ->
            val issuedAt = (raw as? JsonPrimitive)?.content?.toLongOrNull()
            val now =
                kotlin.time.Clock.System
                    .now()
                    .epochSeconds
            when {
                issuedAt == null -> violations += "nonce_issued_at is not epoch seconds: $raw"
                issuedAt > now -> violations += "nonce_issued_at is in the future"
                now - issuedAt > EudiwOid4VpProfile.MAX_NONCE_AGE_SECONDS ->
                    violations += "nonce is older than ${EudiwOid4VpProfile.MAX_NONCE_AGE_SECONDS}s"
            }
        }
        if (violations.isNotEmpty()) {
            throw TrustWeaveException.InvalidOperation(
                code = "EUDIW_PROFILE_VIOLATION",
                message = "Presentation does not conform to EUDIW ARF profile",
                context = mapOf("violations" to violations.joinToString("; ")),
            )
        }

        val responseData =
            buildJsonObject {
                put("profile", "eudiw")
                put("status", "profile_checked")
                put("credentialCount", vp.verifiableCredential.size)
            }

        val envelope =
            ExchangeMessageEnvelope(
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
            context =
                mapOf(
                    "protocolName" to protocolName.value,
                    "operation" to operation,
                    "supportedOperations" to capabilities.supportedOperations.map { it.name },
                ),
        )
}
