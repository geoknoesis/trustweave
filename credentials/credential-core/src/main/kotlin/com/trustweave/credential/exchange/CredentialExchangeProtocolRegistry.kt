package com.trustweave.credential.exchange

import com.trustweave.credential.exchange.exception.ExchangeException
import com.trustweave.credential.exchange.exception.toExchangeException
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for credential exchange protocols.
 *
 * Allows registration and discovery of different exchange protocols
 * (DIDComm, OIDC4VCI, CHAPI, etc.) for use in credential workflows.
 *
 * **Example Usage:**
 * ```kotlin
 * val registry = CredentialExchangeProtocolRegistry()
 *
 * // Register protocols
 * registry.register(DidCommExchangeProtocol(didCommService))
 * registry.register(Oidc4VciExchangeProtocol(oidc4vciService))
 *
 * // Use protocol
 * val offer = registry.offerCredential(
 *     protocolName = "didcomm",
 *     request = CredentialOfferRequest(...)
 * )
 * ```
 */
class CredentialExchangeProtocolRegistry(
    initialProtocols: Map<String, CredentialExchangeProtocol> = emptyMap()
) {
    private val protocols = ConcurrentHashMap<String, CredentialExchangeProtocol>(initialProtocols)

    /**
     * Registers a credential exchange protocol.
     */
    fun register(protocol: CredentialExchangeProtocol) {
        protocols[protocol.protocolName] = protocol
    }

    /**
     * Unregisters a protocol.
     */
    fun unregister(protocolName: String) {
        protocols.remove(protocolName)
    }

    /**
     * Gets a protocol by name.
     */
    fun get(protocolName: String): CredentialExchangeProtocol? {
        return protocols[protocolName]
    }

    /**
     * Gets all registered protocols.
     */
    fun getAll(): Map<String, CredentialExchangeProtocol> = protocols.toMap()

    /**
     * Gets all registered protocol names.
     */
    fun getAllProtocolNames(): List<String> = protocols.keys.toList()

    /**
     * Checks if a protocol is registered.
     */
    fun isRegistered(protocolName: String): Boolean = protocols.containsKey(protocolName)

    /**
     * Creates a credential offer using the specified protocol.
     */
    suspend fun offerCredential(
        protocolName: String,
        request: CredentialOfferRequest
    ): CredentialOfferResponse {
        val protocol = protocols[protocolName]
            ?: throw ExchangeException.ProtocolNotRegistered(
                protocolName = protocolName,
                availableProtocols = protocols.keys.toList()
            )

        if (!protocol.supportedOperations.contains(ExchangeOperation.OFFER_CREDENTIAL)) {
            throw ExchangeException.OperationNotSupported(
                protocolName = protocolName,
                operation = "OFFER_CREDENTIAL",
                supportedOperations = protocol.supportedOperations.map { it.name }
            )
        }

        return try {
            protocol.offerCredential(request)
        } catch (e: ExchangeException) {
            throw e  // Re-throw exchange exceptions as-is
        } catch (e: Throwable) {
            // Convert unexpected errors to ExchangeException
            throw e.toExchangeException()
        }
    }

    /**
     * Requests a credential using the specified protocol.
     */
    suspend fun requestCredential(
        protocolName: String,
        request: CredentialRequestRequest
    ): CredentialRequestResponse {
        val protocol = protocols[protocolName]
            ?: throw ExchangeException.ProtocolNotRegistered(
                protocolName = protocolName,
                availableProtocols = protocols.keys.toList()
            )

        if (!protocol.supportedOperations.contains(ExchangeOperation.REQUEST_CREDENTIAL)) {
            throw ExchangeException.OperationNotSupported(
                protocolName = protocolName,
                operation = "REQUEST_CREDENTIAL",
                supportedOperations = protocol.supportedOperations.map { it.name }
            )
        }

        return try {
            protocol.requestCredential(request)
        } catch (e: ExchangeException) {
            throw e  // Re-throw exchange exceptions as-is
        } catch (e: Throwable) {
            // Convert unexpected errors to ExchangeException
            throw e.toExchangeException()
        }
    }

    /**
     * Issues a credential using the specified protocol.
     */
    suspend fun issueCredential(
        protocolName: String,
        request: CredentialIssueRequest
    ): CredentialIssueResponse {
        val protocol = protocols[protocolName]
            ?: throw ExchangeException.ProtocolNotRegistered(
                protocolName = protocolName,
                availableProtocols = protocols.keys.toList()
            )

        if (!protocol.supportedOperations.contains(ExchangeOperation.ISSUE_CREDENTIAL)) {
            throw ExchangeException.OperationNotSupported(
                protocolName = protocolName,
                operation = "ISSUE_CREDENTIAL",
                supportedOperations = protocol.supportedOperations.map { it.name }
            )
        }

        return try {
            protocol.issueCredential(request)
        } catch (e: ExchangeException) {
            throw e  // Re-throw exchange exceptions as-is
        } catch (e: Throwable) {
            // Convert unexpected errors to ExchangeException
            throw e.toExchangeException()
        }
    }

    /**
     * Requests a proof using the specified protocol.
     */
    suspend fun requestProof(
        protocolName: String,
        request: ProofRequestRequest
    ): ProofRequestResponse {
        val protocol = protocols[protocolName]
            ?: throw ExchangeException.ProtocolNotRegistered(
                protocolName = protocolName,
                availableProtocols = protocols.keys.toList()
            )

        if (!protocol.supportedOperations.contains(ExchangeOperation.REQUEST_PROOF)) {
            throw ExchangeException.OperationNotSupported(
                protocolName = protocolName,
                operation = "REQUEST_PROOF",
                supportedOperations = protocol.supportedOperations.map { it.name }
            )
        }

        return try {
            protocol.requestProof(request)
        } catch (e: ExchangeException) {
            throw e  // Re-throw exchange exceptions as-is
        } catch (e: Throwable) {
            // Convert unexpected errors to ExchangeException
            throw e.toExchangeException()
        }
    }

    /**
     * Presents a proof using the specified protocol.
     */
    suspend fun presentProof(
        protocolName: String,
        request: ProofPresentationRequest
    ): ProofPresentationResponse {
        val protocol = protocols[protocolName]
            ?: throw ExchangeException.ProtocolNotRegistered(
                protocolName = protocolName,
                availableProtocols = protocols.keys.toList()
            )

        if (!protocol.supportedOperations.contains(ExchangeOperation.PRESENT_PROOF)) {
            throw ExchangeException.OperationNotSupported(
                protocolName = protocolName,
                operation = "PRESENT_PROOF",
                supportedOperations = protocol.supportedOperations.map { it.name }
            )
        }

        return try {
            protocol.presentProof(request)
        } catch (e: ExchangeException) {
            throw e  // Re-throw exchange exceptions as-is
        } catch (e: Throwable) {
            // Convert unexpected errors to ExchangeException
            throw e.toExchangeException()
        }
    }

    /**
     * Clears all registered protocols.
     */
    fun clear() {
        protocols.clear()
    }

    /**
     * Creates a snapshot of the registry.
     */
    fun snapshot(): CredentialExchangeProtocolRegistry {
        return CredentialExchangeProtocolRegistry(protocols.toMap())
    }

    companion object {
        /**
         * Creates an empty registry.
         */
        fun create(): CredentialExchangeProtocolRegistry = CredentialExchangeProtocolRegistry()
    }
}

