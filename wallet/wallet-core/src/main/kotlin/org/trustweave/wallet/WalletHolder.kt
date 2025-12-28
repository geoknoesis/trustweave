package org.trustweave.wallet

import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.oidc4vp.Oidc4VpService
import org.trustweave.credential.oidc4vp.models.PermissionRequest
import org.trustweave.credential.oidc4vp.models.PresentableCredential
import org.trustweave.credential.qr.QrCodeContent
import org.trustweave.credential.qr.QrCodeParser
import org.trustweave.did.identifiers.Did
import java.util.*

/**
 * Wallet holder convenience API for wallet applications.
 *
 * Provides high-level operations for accepting credential offers and handling presentation requests,
 * simplifying wallet app integration.
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
        // Parse QR code
        val qrContent = QrCodeParser.parse(offerUrl)
        
        when (qrContent) {
            is QrCodeContent.CredentialOffer -> {
                return acceptOidc4vciOffer(qrContent)
            }
            else -> {
                throw IllegalArgumentException("Unsupported credential offer format: $offerUrl")
            }
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
        
        // Parse QR code
        val qrContent = QrCodeParser.parse(requestUrl)
        
        when (qrContent) {
            is QrCodeContent.PresentationRequest -> {
                return oidc4vpService.parseAuthorizationUrl(qrContent.authorizationUrl)
            }
            else -> {
                throw IllegalArgumentException("Unsupported presentation request format: $requestUrl")
            }
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
        
        // Convert credentials to PresentableCredential format
        val presentableCredentials = selectedCredentials.map { cred ->
            PresentableCredential(
                credentialId = cred.id?.value ?: UUID.randomUUID().toString(),
                credential = cred,
                credentialType = cred.type.firstOrNull()?.value ?: "VerifiableCredential"
            )
        }
        
        // Create permission response
        val permissionResponse = oidc4vpService.createPermissionResponse(
            permissionRequest = permissionRequest,
            selectedCredentials = presentableCredentials,
            selectedFields = selectedFields,
            holderDid = holderDid.value,
            keyId = keyId
        )
        
        // Submit response
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
            // No specific types requested, return all credentials
            return wallet.list()
        }
        
        // Query wallet for matching credential types
        // Match credentials that have any of the requested types
        val allCredentials = wallet.list()
        return allCredentials.filter { credential ->
            requestedTypes.any { requestedType ->
                credential.type.any { credentialType -> credentialType.value == requestedType }
            }
        }
    }

    /**
     * Accepts an OIDC4VCI credential offer.
     */
    private suspend fun acceptOidc4vciOffer(offer: QrCodeContent.CredentialOffer): VerifiableCredential {
        requireNotNull(exchangeRegistry) {
            "ExchangeProtocolRegistry is required for credential offers. Provide it in WalletHolder constructor."
        }
        
        // This is a simplified implementation
        // Full implementation would:
        // 1. Create credential request from offer
        // 2. Exchange for credential
        // 3. Store in wallet
        
        throw UnsupportedOperationException(
            "OIDC4VCI credential offer acceptance requires full implementation. " +
            "Use ExchangeProtocolRegistry directly for now."
        )
    }
}

