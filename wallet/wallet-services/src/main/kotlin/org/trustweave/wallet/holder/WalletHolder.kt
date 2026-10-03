package org.trustweave.wallet.holder

import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.CredentialService
import org.trustweave.credential.exchange.model.CredentialAttribute
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.identifiers.OfferId
import org.trustweave.credential.identifiers.RequestId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.oidc4vp.Oidc4VpService
import org.trustweave.credential.oidc4vp.models.PermissionRequest
import org.trustweave.credential.oidc4vp.models.PresentableCredential
import org.trustweave.credential.qr.QrCodeContent
import org.trustweave.credential.qr.QrCodeParser
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.Wallet
import java.util.UUID

/**
 * Wallet holder convenience API for wallet applications.
 *
 * Provides high-level operations for accepting credential offers and handling presentation requests,
 * simplifying wallet app integration.
 *
 * This class is intentionally placed in [wallet:wallet-services] rather than [wallet:wallet-core]
 * because it depends on OIDC4VP and credential exchange protocols which are infrastructure concerns
 * that should not pollute the pure domain interfaces in [wallet-core].
 *
 * **Example Usage:**
 * ```kotlin
 * val walletHolder = WalletHolder(
 *     wallet = wallet,
 *     holderDid = holderDid,
 *     exchangeRegistry = registry,
 *     oidc4vpService = oidc4vpService,
 *     credentialVerifier = credentialService // required to accept credential offers
 * )
 *
 * // Accept credential offer from QR code
 * val credential = walletHolder.acceptCredentialOffer(offerUrl)
 *
 * // Handle presentation request from QR code
 * val permissionRequest = walletHolder.handlePresentationRequest(requestUrl)
 * val selectedCredentials = wallet.selectCredentials(...)  // User selects
 * walletHolder.submitPresentation(permissionRequest, selectedCredentials)
 * ```
 */
class WalletHolder(
    private val wallet: Wallet,
    private val holderDid: Did,
    private val exchangeRegistry: ExchangeProtocolRegistry? = null,
    private val oidc4vpService: Oidc4VpService? = null,
    /**
     * Verifies every credential received through [acceptCredentialOffer] before it is stored.
     * Required for accepting offers: without it the wallet would store whatever the issuer
     * endpoint returned, so [acceptCredentialOffer] fails instead.
     */
    private val credentialVerifier: CredentialService? = null,
) {
    /** Binary-compatible form of the constructor that predates [credentialVerifier]. */
    constructor(
        wallet: Wallet,
        holderDid: Did,
        exchangeRegistry: ExchangeProtocolRegistry?,
        oidc4vpService: Oidc4VpService?,
    ) : this(wallet, holderDid, exchangeRegistry, oidc4vpService, null)

    /**
     * Accepts a credential offer from a URL (e.g., from QR code).
     *
     * Parses the offer URL, requests the credential, verifies it and only then stores it in the
     * wallet. The credential is rejected (and nothing is stored) unless:
     * - the configured [CredentialService] verifies it (proof, validity window, status),
     * - its issuer is the offer's credential issuer (its DID, or the issuer URL itself), and
     * - its subject is bound to this holder (`credentialSubject.id` is [holderDid] or one of its
     *   DID URLs).
     *
     * @param offerUrl The credential offer URL (openid-credential-offer:// or HTTPS)
     * @return The accepted credential stored in the wallet
     * @throws IllegalStateException if no credential verifier is configured
     * @throws CredentialRejectedException if the issued credential fails any check above
     */
    suspend fun acceptCredentialOffer(offerUrl: String): VerifiableCredential {
        val qrContent = QrCodeParser.parse(offerUrl)

        return when (qrContent) {
            is QrCodeContent.CredentialOffer -> acceptOidc4vciOffer(qrContent)
            else -> throw IllegalArgumentException("Unsupported credential offer format: $offerUrl")
        }
    }

    /**
     * Handles a presentation request from a URL (e.g., from QR code).
     *
     * Parses the authorization URL and returns a PermissionRequest for user interaction.
     *
     * @param requestUrl The presentation request URL (openid4vp:// or HTTPS)
     * @return PermissionRequest for user credential selection
     */
    suspend fun handlePresentationRequest(requestUrl: String): PermissionRequest {
        requireNotNull(oidc4vpService) {
            "Oidc4VpService is required for presentation requests. Provide it in WalletHolder constructor."
        }

        val qrContent = QrCodeParser.parse(requestUrl)

        return when (qrContent) {
            is QrCodeContent.PresentationRequest ->
                oidc4vpService.parseAuthorizationUrl(qrContent.authorizationUrl)
            else -> throw IllegalArgumentException("Unsupported presentation request format: $requestUrl")
        }
    }

    /**
     * Submits a presentation response for a permission request.
     *
     * Creates a permission response with selected credentials and submits it to the verifier.
     *
     * @param permissionRequest The permission request to respond to
     * @param selectedCredentials List of credentials to include
     * @param selectedFields List of field selections per credential (optional)
     * @param keyId Key ID for signing the VP token
     */
    suspend fun submitPresentation(
        permissionRequest: PermissionRequest,
        selectedCredentials: List<VerifiableCredential>,
        selectedFields: List<List<String>> = emptyList(),
        keyId: String,
    ) {
        requireNotNull(oidc4vpService) {
            "Oidc4VpService is required for presentation submission. Provide it in WalletHolder constructor."
        }

        val presentableCredentials =
            selectedCredentials.map { cred ->
                PresentableCredential(
                    credentialId = cred.id?.value ?: UUID.randomUUID().toString(),
                    credential = cred,
                    credentialType = cred.type.firstOrNull()?.value ?: "VerifiableCredential",
                )
            }

        val permissionResponse =
            oidc4vpService.createPermissionResponse(
                permissionRequest = permissionRequest,
                selectedCredentials = presentableCredentials,
                selectedFields = selectedFields,
                holderDid = holderDid.value,
                keyId = keyId,
            )

        oidc4vpService.submitPermissionResponse(permissionResponse)
    }

    /**
     * Selects credentials from wallet that match the requested types.
     *
     * Helper method to query wallet for credentials matching permission request requirements.
     *
     * @param permissionRequest The permission request
     * @return List of matching credentials from wallet
     */
    suspend fun selectMatchingCredentials(permissionRequest: PermissionRequest): List<VerifiableCredential> {
        val requestedTypes = permissionRequest.requestedCredentialTypes

        if (requestedTypes.isEmpty()) {
            return wallet.list()
        }

        val allCredentials = wallet.list()
        return allCredentials.filter { credential ->
            requestedTypes.any { requestedType ->
                credential.type.any { credentialType -> credentialType.value == requestedType }
            }
        }
    }

    /**
     * Accepts an OIDC4VCI credential offer via the registered exchange protocol.
     *
     * Implements the OIDC4VCI holder-side flow:
     * 1. Resolve the issuer DID from the offer's credential issuer URL.
     * 2. Register the offer in the exchange protocol (via `offer()`).
     * 3. Create a credential request (via `request()`).
     * 4. Retrieve the issued credential (via `issue()`).
     * 5. Store it in the wallet.
     *
     * **Prerequisite:** An `Oidc4VciExchangeProtocol` must be registered in [exchangeRegistry]
     * under the `oidc4vci` protocol name. The underlying service must be configured with the
     * issuer's credential endpoint so that the HTTP credential request in step 4 succeeds.
     */
    private suspend fun acceptOidc4vciOffer(offer: QrCodeContent.CredentialOffer): VerifiableCredential {
        requireNotNull(exchangeRegistry) {
            "ExchangeProtocolRegistry is required for credential offers. Provide it in WalletHolder constructor."
        }

        val protocol =
            exchangeRegistry.get(ExchangeProtocolName.Oidc4Vci)
                ?: throw UnsupportedOperationException(
                    "OIDC4VCI exchange protocol is not registered. " +
                        "Register an Oidc4VciExchangeProtocol in the ExchangeProtocolRegistry.",
                )

        val credentialIssuer =
            offer.credentialIssuer.ifEmpty {
                throw IllegalArgumentException(
                    "Credential offer has no issuer — cannot derive issuer DID. " +
                        "Ensure the offer URL contains a 'credential_issuer' parameter.",
                )
            }

        val verifier =
            checkNotNull(credentialVerifier) {
                "A CredentialService is required to accept credential offers: issued credentials are " +
                    "verified before they are stored. Provide credentialVerifier in the WalletHolder constructor."
            }

        val issuerDid = Did(issuerDidFor(credentialIssuer))

        // Step 1: Register the offer in the exchange protocol so that the subsequent
        //         request() call can look it up by offer ID.
        val offerEnvelope =
            protocol.offer(
                ExchangeRequest.Offer(
                    protocolName = ExchangeProtocolName.Oidc4Vci,
                    issuerDid = issuerDid,
                    holderDid = holderDid,
                    credentialPreview =
                        CredentialPreview(
                            attributes =
                                offer.credentialConfigurationIds.map { typeId ->
                                    CredentialAttribute(name = typeId, value = "")
                                },
                        ),
                    options =
                        ExchangeOptions(
                            metadata =
                                mapOf(
                                    "credentialIssuer" to JsonPrimitive(credentialIssuer),
                                    "credentialTypes" to
                                        JsonArray(
                                            offer.credentialConfigurationIds.map { JsonPrimitive(it) },
                                        ),
                                ),
                        ),
                ),
            )

        val offerId =
            offerEnvelope.metadata["offerId"]?.jsonPrimitive?.content
                ?: throw IllegalStateException(
                    "OIDC4VCI offer step did not return an offerId in the envelope metadata.",
                )

        // Step 2: Create the holder's credential request.
        val requestEnvelope =
            protocol.request(
                ExchangeRequest.Request(
                    protocolName = ExchangeProtocolName.Oidc4Vci,
                    holderDid = holderDid,
                    issuerDid = issuerDid,
                    offerId = OfferId(offerId),
                ),
            )

        val requestId =
            requestEnvelope.metadata["requestId"]?.jsonPrimitive?.content
                ?: throw IllegalStateException(
                    "OIDC4VCI request step did not return a requestId in the envelope metadata.",
                )

        // Step 3: Trigger the issuance — the protocol sends the credential request to the
        //         issuer's HTTP endpoint and returns the issued VerifiableCredential.
        val placeholderCredential = holderSideIssueEnvelope(credentialIssuer)
        val (issuedCredential, _) =
            protocol.issue(
                ExchangeRequest.Issue(
                    protocolName = ExchangeProtocolName.Oidc4Vci,
                    issuerDid = issuerDid,
                    holderDid = holderDid,
                    credential = placeholderCredential,
                    requestId = RequestId(requestId),
                ),
            )
        if (issuedCredential == placeholderCredential) {
            throw CredentialRejectedException("The exchange protocol returned the request envelope, not an issued credential")
        }

        // Step 4: Verify before persisting — never store an unverified credential.
        requireAcceptable(issuedCredential, verifier, credentialIssuer, issuerDid)
        wallet.store(issuedCredential)
        return issuedCredential
    }

    /**
     * The `credential` that [ExchangeRequest.Issue] requires. That request shape is shared with
     * issuer-side protocols, where it carries the credential to issue; for a holder accepting an
     * OIDC4VCI offer there is no such credential — the OIDC4VCI protocol ignores it and returns the
     * issuer's own credential from the HTTP response. This envelope is therefore never stored or
     * returned: [acceptOidc4vciOffer] rejects a protocol that echoes it back, and everything it does
     * return is verified independently.
     */
    private fun holderSideIssueEnvelope(credentialIssuer: String): VerifiableCredential =
        VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri(credentialIssuer)),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri(holderDid.value)),
        )

    private suspend fun requireAcceptable(
        credential: VerifiableCredential,
        verifier: CredentialService,
        credentialIssuer: String,
        issuerDid: Did,
    ) {
        when (val result = verifier.verify(credential)) {
            is VerificationResult.Valid -> Unit
            is VerificationResult.Invalid -> throw CredentialRejectedException(
                "Issued credential failed verification: " +
                    (result.errors.takeIf { it.isNotEmpty() }?.joinToString("; ") ?: result::class.simpleName),
            )
        }

        val actualIssuer = credential.issuer.id.value
        if (actualIssuer != issuerDid.value && actualIssuer != credentialIssuer) {
            throw CredentialRejectedException(
                "Issued credential names issuer '$actualIssuer', but the offer came from '$credentialIssuer' " +
                    "(${issuerDid.value})",
            )
        }

        val subject =
            credential.credentialSubject.id?.value
                ?: throw CredentialRejectedException("Issued credential has no subject ID, so it is not bound to holder ${holderDid.value}")
        if (subject != holderDid.value && !subject.startsWith(holderDid.value + "#")) {
            throw CredentialRejectedException("Issued credential is bound to '$subject', not to holder ${holderDid.value}")
        }
    }
}

/** An issued credential was refused before it reached the wallet; nothing was stored. */
class CredentialRejectedException(
    message: String,
) : IllegalStateException(message)

/**
 * The DID of an OIDC4VCI credential issuer: the identifier itself when it is a DID, otherwise the
 * did:web DID of its HTTPS URL (did:web spec §3.1: the port's `:` is percent-encoded, path
 * segments become `:`-separated).
 */
internal fun issuerDidFor(credentialIssuer: String): String {
    if (credentialIssuer.startsWith("did:")) return credentialIssuer
    val uri = runCatching { java.net.URI(credentialIssuer) }.getOrNull()
    val host = uri?.host
    require(uri != null && host != null && uri.scheme.equals("https", ignoreCase = true)) {
        "Credential issuer '$credentialIssuer' is neither a DID nor an https URL; cannot derive its DID"
    }
    val authority = if (uri.port >= 0) "$host%3A${uri.port}" else host
    val path =
        uri.path
            .orEmpty()
            .trim('/')
            .split('/')
            .filter { it.isNotEmpty() }
    return (listOf("did:web:$authority") + path).joinToString(":")
}
