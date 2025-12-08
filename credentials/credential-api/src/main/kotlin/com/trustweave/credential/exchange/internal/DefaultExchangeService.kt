package com.trustweave.credential.exchange.internal

import com.trustweave.credential.CredentialService
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.exchange.*
import com.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import com.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import com.trustweave.credential.exchange.model.ExchangeMessageType
import com.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import com.trustweave.credential.exchange.request.ExchangeRequest
import com.trustweave.credential.exchange.request.ProofExchangeRequest
import com.trustweave.credential.exchange.response.ExchangeResponse
import com.trustweave.credential.exchange.response.ProofExchangeResponse
import com.trustweave.credential.exchange.result.ExchangeResult
import com.trustweave.credential.identifiers.*
import com.trustweave.credential.identifiers.ExchangeProtocolName
import com.trustweave.did.resolver.DidResolver
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Default implementation of ExchangeService.
 * 
 * Internal implementation detail. Use factory functions to create instances.
 */
internal class DefaultExchangeService(
    private val protocolRegistry: ExchangeProtocolRegistry,
    private val credentialService: CredentialService,
    private val didResolver: DidResolver
) : ExchangeService {
    
    /**
     * Convert exception to ExchangeResult.Failure.
     */
    private fun Exception.toExchangeFailure(operation: String): ExchangeResult.Failure {
        return when (this) {
            is IllegalArgumentException -> ExchangeResult.Failure.InvalidRequest(
                field = "request",
                reason = message ?: "Invalid request",
                errors = listOf("$operation: ${message ?: "Invalid request"}")
            )
            is IllegalStateException -> ExchangeResult.Failure.InvalidRequest(
                field = "state",
                reason = message ?: "Invalid state",
                errors = listOf("$operation: ${message ?: "Invalid state"}")
            )
            is NoSuchElementException -> ExchangeResult.Failure.MessageNotFound(
                messageId = "",
                errors = listOf("$operation: ${message ?: "Resource not found"}")
            )
            is TimeoutException,
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is IOException -> ExchangeResult.Failure.NetworkError(
                reason = when (this) {
                    is TimeoutException -> "Request timeout: ${message ?: "Operation timed out"}"
                    is UnknownHostException -> "Unknown host: ${message ?: "Host not found"}"
                    is ConnectException -> "Connection failed: ${message ?: "Unable to connect"}"
                    is SocketTimeoutException -> "Socket timeout: ${message ?: "Connection timed out"}"
                    is IOException -> "I/O error: ${message ?: "Input/output operation failed"}"
                    else -> message ?: "Network error"
                },
                errors = listOf("$operation: ${message ?: "Network error"}"),
                cause = this
            )
            else -> ExchangeResult.Failure.Unknown(
                reason = "$operation: ${message ?: "Unknown error"}",
                errors = listOf(message ?: "Unknown error"),
                cause = this
            )
        }
    }
    
    override suspend fun offer(request: ExchangeRequest.Offer): ExchangeResult<ExchangeResponse.Offer> {
        // Protocol is now type-safe in request
        val protocolName = request.protocolName
        val protocol = protocolRegistry.get(protocolName)
            ?: return ExchangeResult.Failure.ProtocolNotSupported(
                protocolName = protocolName,
                availableProtocols = protocolRegistry.getSupportedProtocols()
            )
        
        if (!protocol.supports(ExchangeOperation.OFFER_CREDENTIAL)) {
            return ExchangeResult.Failure.OperationNotSupported(
                protocolName = protocolName,
                operation = ExchangeOperation.OFFER_CREDENTIAL,
                supportedOperations = protocol.capabilities.supportedOperations.toList()
            )
        }
        
        return try {
            val messageEnvelope = protocol.offer(request)
            val offerId = OfferId(java.util.UUID.randomUUID().toString()) // Generate or extract from envelope

            ExchangeResult.Success(
                ExchangeResponse.Offer(
                    protocolName = protocolName,
                    messageEnvelope = messageEnvelope,
                    offerId = offerId
                )
            )
        } catch (e: Exception) {
            e.toExchangeFailure("Failed to create credential offer")
        }
    }
    
    override suspend fun request(request: ExchangeRequest.Request): ExchangeResult<ExchangeResponse.Request> {
        val protocolName = request.protocolName
        val protocol = protocolRegistry.get(protocolName)
            ?: return ExchangeResult.Failure.ProtocolNotSupported(
                protocolName = protocolName,
                availableProtocols = protocolRegistry.getSupportedProtocols()
            )
        
        if (!protocol.supports(ExchangeOperation.REQUEST_CREDENTIAL)) {
            return ExchangeResult.Failure.OperationNotSupported(
                protocolName = protocolName,
                operation = ExchangeOperation.REQUEST_CREDENTIAL,
                supportedOperations = protocol.capabilities.supportedOperations.toList()
            )
        }
        
        return try {
            val messageEnvelope = protocol.request(request)
            val requestId = RequestId(java.util.UUID.randomUUID().toString())
            
            ExchangeResult.Success(
                ExchangeResponse.Request(
                    protocolName = protocolName,
                    messageEnvelope = messageEnvelope,
                    requestId = requestId
                )
            )
        } catch (e: Exception) {
            e.toExchangeFailure("Failed to create credential request")
        }
    }
    
    override suspend fun issue(request: ExchangeRequest.Issue): ExchangeResult<ExchangeResponse.Issue> {
        val protocolName = request.protocolName
        val protocol = protocolRegistry.get(protocolName)
            ?: return ExchangeResult.Failure.ProtocolNotSupported(
                protocolName = protocolName,
                availableProtocols = protocolRegistry.getSupportedProtocols()
            )
        
        if (!protocol.supports(ExchangeOperation.ISSUE_CREDENTIAL)) {
            return ExchangeResult.Failure.OperationNotSupported(
                protocolName = protocolName,
                operation = ExchangeOperation.ISSUE_CREDENTIAL,
                supportedOperations = protocol.capabilities.supportedOperations.toList()
            )
        }
        
        return try {
            val (credential, messageEnvelope) = protocol.issue(request)
            val issueId = IssueId(java.util.UUID.randomUUID().toString())
            
            ExchangeResult.Success(
                ExchangeResponse.Issue(
                    protocolName = protocolName,
                    messageEnvelope = messageEnvelope,
                    issueId = issueId,
                    credential = credential
                )
            )
        } catch (e: Exception) {
            e.toExchangeFailure("Failed to issue credential")
        }
    }
    
    override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeResult<ProofExchangeResponse.Request> {
        val protocolName = request.protocolName
        val protocol = protocolRegistry.get(protocolName)
            ?: return ExchangeResult.Failure.ProtocolNotSupported(
                protocolName = protocolName,
                availableProtocols = protocolRegistry.getSupportedProtocols()
            )
        
        if (!protocol.supports(ExchangeOperation.REQUEST_PROOF)) {
            return ExchangeResult.Failure.OperationNotSupported(
                protocolName = protocolName,
                operation = ExchangeOperation.REQUEST_PROOF,
                supportedOperations = protocol.capabilities.supportedOperations.toList()
            )
        }
        
        return try {
            val messageEnvelope = protocol.requestProof(request)
            val requestId = RequestId(java.util.UUID.randomUUID().toString())
            
            ExchangeResult.Success(
                ProofExchangeResponse.Request(
                    protocolName = protocolName,
                    messageEnvelope = messageEnvelope,
                    requestId = requestId
                )
            )
        } catch (e: Exception) {
            e.toExchangeFailure("Failed to create proof request")
        }
    }
    
    override suspend fun presentProof(request: ProofExchangeRequest.Presentation): ExchangeResult<ProofExchangeResponse.Presentation> {
        val protocolName = request.protocolName
        val protocol = protocolRegistry.get(protocolName)
            ?: return ExchangeResult.Failure.ProtocolNotSupported(
                protocolName = protocolName,
                availableProtocols = protocolRegistry.getSupportedProtocols()
            )
        
        if (!protocol.supports(ExchangeOperation.PRESENT_PROOF)) {
            return ExchangeResult.Failure.OperationNotSupported(
                protocolName = protocolName,
                operation = ExchangeOperation.PRESENT_PROOF,
                supportedOperations = protocol.capabilities.supportedOperations.toList()
            )
        }
        
        return try {
            val (presentation, messageEnvelope) = protocol.presentProof(request)
            val presentationId = PresentationId(java.util.UUID.randomUUID().toString())
            
            ExchangeResult.Success(
                ProofExchangeResponse.Presentation(
                    protocolName = protocolName,
                    messageEnvelope = messageEnvelope,
                    presentationId = presentationId,
                    presentation = presentation
                )
            )
        } catch (e: Exception) {
            e.toExchangeFailure("Failed to present proof")
        }
    }
    
    override fun supports(protocolName: ExchangeProtocolName): Boolean {
        return protocolRegistry.isRegistered(protocolName)
    }
    
    override fun supports(protocolName: ExchangeProtocolName, operation: ExchangeOperation): Boolean {
        return protocolRegistry.supports(protocolName, operation)
    }
    
    override fun supports(operation: ExchangeOperation): Boolean {
        return protocolRegistry.getAll().values.any { it.supports(operation) }
    }
    
    override fun supportsCapability(
        protocolName: ExchangeProtocolName,
        predicate: ExchangeProtocolCapabilities.() -> Boolean
    ): Boolean {
        val capabilities = protocolRegistry.getCapabilities(protocolName) ?: return false
        return predicate(capabilities)
    }
    
    override fun supportedProtocols(): List<ExchangeProtocolName> {
        return protocolRegistry.getSupportedProtocols()
    }
    
    override fun getCapabilities(protocolName: ExchangeProtocolName): ExchangeProtocolCapabilities? {
        return protocolRegistry.getCapabilities(protocolName)
    }
}

