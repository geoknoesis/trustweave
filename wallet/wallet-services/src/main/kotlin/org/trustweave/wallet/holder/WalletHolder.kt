package org.trustweave.wallet.holder

import org.trustweave.core.identifiers.Iri
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
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.Wallet
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.*

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
 *     oidc4vpService = oidc4vpService
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
    private val oidc4vpService: Oidc4VpService? = null
) {
    /**
     * Accepts a credential offer from a URL (e.g., from QR code).
     *
     * Parses the offer URL, requests the credential, and stores it in the wallet.
     *
     * @param offerUrl The credential offer URL (openid-credential-offer:// or HTTPS)
     * @return The accepted credential stored in the wallet
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
        keyId: String
    ) {
        requireNotNull(oidc4vpService) {
            "Oidc4VpService is required for presentation submission. Provide it in WalletHolder constructor."
        }

        val presentableCredentials = selectedCredentials.map { cred ->
            PresentableCredential(
                credentialId = cred.id?.value ?: UUID.randomUUID().toString(),
                credential = cred,
                credentialType = cred.type.firstOrNull()?.value ?: "VerifiableCredential"
            )
        }

        val permissionResponse = oidc4vpService.createPermissionResponse(
            permissionRequest = permissionRequest,
            selectedCredentials = presentableCredentials,
            selectedFields = selectedFields,
            holderDid = holderDid.value,
            keyId = keyId
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

        val protocol = exchangeRegistry.get(ExchangeProtocolName.Oidc4Vci)
            ?: throw UnsupportedOperationException(
                "OIDC4VCI exchange protocol is not registered. " +
                "Register an Oidc4VciExchangeProtocol in the ExchangeProtocolRegistry."
            )

        val credentialIssuer = offer.credentialIssuer.ifEmpty {
            throw IllegalArgumentException(
                "Credential offer has no issuer — cannot derive issuer DID. " +
                "Ensure the offer URL contains a 'credential_issuer' parameter."
            )
        }

        // Map the credential issuer URL to a DID. If it already is a DID, use it directly;
        // otherwise synthesize a did:web DID from the HTTPS host.
        val issuerDidValue = if (credentialIssuer.startsWith("did:")) {
            credentialIssuer
        } else {
            val host = credentialIssuer
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
                .replace("/", ":")
            "did:web:$host"
        }
        val issuerDid = Did(issuerDidValue)

        // Step 1: Register the offer in the exchange protocol so that the subsequent
        //         request() call can look it up by offer ID.
        val offerEnvelope = protocol.offer(
            ExchangeRequest.Offer(
                protocolName = ExchangeProtocolName.Oidc4Vci,
                issuerDid = issuerDid,
                holderDid = holderDid,
                credentialPreview = CredentialPreview(
                    attributes = offer.credentialConfigurationIds.map { typeId ->
                        CredentialAttribute(name = typeId, value = "")
                    }
                ),
                options = ExchangeOptions(
                    metadata = mapOf(
                        "credentialIssuer" to JsonPrimitive(credentialIssuer),
                        "credentialTypes" to JsonArray(
                            offer.credentialConfigurationIds.map { JsonPrimitive(it) }
                        )
                    )
                )
            )
        )

        val offerId = offerEnvelope.metadata["offerId"]?.jsonPrimitive?.content
            ?: throw IllegalStateException(
                "OIDC4VCI offer step did not return an offerId in the envelope metadata."
            )

        // Step 2: Create the holder's credential request.
        val requestEnvelope = protocol.request(
            ExchangeRequest.Request(
                protocolName = ExchangeProtocolName.Oidc4Vci,
                holderDid = holderDid,
                issuerDid = issuerDid,
                offerId = OfferId(offerId)
            )
        )

        val requestId = requestEnvelope.metadata["requestId"]?.jsonPrimitive?.content
            ?: throw IllegalStateException(
                "OIDC4VCI request step did not return a requestId in the envelope metadata."
            )

        // Step 3: Trigger the issuance — the protocol sends the credential request to the
        //         issuer's HTTP endpoint and returns the issued VerifiableCredential.
        //         A placeholder credential is required by the ExchangeRequest.Issue API; the
        //         actual credential returned is sourced from the issuer's HTTP response.
        val placeholderCredential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri(credentialIssuer)),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri(holderDid.value))
        )
        val (issuedCredential, _) = protocol.issue(
            ExchangeRequest.Issue(
                protocolName = ExchangeProtocolName.Oidc4Vci,
                issuerDid = issuerDid,
                holderDid = holderDid,
                credential = placeholderCredential,
                requestId = RequestId(requestId)
            )
        )

        // Step 4: Persist the credential in the wallet and return it.
        wallet.store(issuedCredential)
        return issuedCredential
    }
}
