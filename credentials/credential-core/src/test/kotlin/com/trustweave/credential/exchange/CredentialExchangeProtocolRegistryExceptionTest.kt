package com.trustweave.credential.exchange

import com.trustweave.credential.exchange.exception.ExchangeException
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

/**
 * Unit tests for exception handling in CredentialExchangeProtocolRegistry.
 */
class CredentialExchangeProtocolRegistryExceptionTest {

    @Test
    fun `test ProtocolNotRegistered exception thrown`() = runBlocking {
        val registry = CredentialExchangeProtocolRegistry()

        val exception = assertFailsWith<ExchangeException.ProtocolNotRegistered> {
            registry.offerCredential("unknown", createTestOfferRequest())
        }

        assertEquals("unknown", exception.protocolName)
        assertTrue(exception.availableProtocols.isEmpty())
        assertEquals("PROTOCOL_NOT_REGISTERED", exception.code)
    }

    @Test
    fun `test ProtocolNotRegistered includes available protocols`() = runBlocking {
        val registry = CredentialExchangeProtocolRegistry()

        // Register some protocols
        registry.register(createMockProtocol("didcomm"))
        registry.register(createMockProtocol("oidc4vci"))

        val exception = assertFailsWith<ExchangeException.ProtocolNotRegistered> {
            registry.offerCredential("unknown", createTestOfferRequest())
        }

        assertEquals("unknown", exception.protocolName)
        assertTrue(exception.availableProtocols.contains("didcomm"))
        assertTrue(exception.availableProtocols.contains("oidc4vci"))
    }

    @Test
    fun `test OperationNotSupported exception thrown`() = runBlocking {
        val registry = CredentialExchangeProtocolRegistry()
        val protocol = createMockProtocol("test", supportedOperations = setOf(ExchangeOperation.OFFER_CREDENTIAL))
        registry.register(protocol)

        val exception = assertFailsWith<ExchangeException.OperationNotSupported> {
            registry.requestProof("test", createTestProofRequest())
        }

        assertEquals("test", exception.protocolName)
        assertEquals("REQUEST_PROOF", exception.operation)
        assertTrue(exception.supportedOperations.contains("OFFER_CREDENTIAL"))
        assertEquals("OPERATION_NOT_SUPPORTED", exception.code)
    }

    @Test
    fun `test all registry methods throw ProtocolNotRegistered`() = runBlocking {
        val registry = CredentialExchangeProtocolRegistry()

        // Test offerCredential
        assertFailsWith<ExchangeException.ProtocolNotRegistered> {
            registry.offerCredential("unknown", createTestOfferRequest())
        }

        // Test requestCredential
        assertFailsWith<ExchangeException.ProtocolNotRegistered> {
            registry.requestCredential("unknown", createTestRequestRequest())
        }

        // Test issueCredential
        assertFailsWith<ExchangeException.ProtocolNotRegistered> {
            registry.issueCredential("unknown", createTestIssueRequest())
        }

        // Test requestProof
        assertFailsWith<ExchangeException.ProtocolNotRegistered> {
            registry.requestProof("unknown", createTestProofRequest())
        }

        // Test presentProof
        assertFailsWith<ExchangeException.ProtocolNotRegistered> {
            registry.presentProof("unknown", createTestPresentationRequest())
        }
    }

    @Test
    fun `test all registry methods throw OperationNotSupported`() = runBlocking {
        val registry = CredentialExchangeProtocolRegistry()
        val protocol = createMockProtocol("test", supportedOperations = setOf(ExchangeOperation.OFFER_CREDENTIAL))
        registry.register(protocol)

        // Test requestCredential
        assertFailsWith<ExchangeException.OperationNotSupported> {
            registry.requestCredential("test", createTestRequestRequest())
        }

        // Test issueCredential
        assertFailsWith<ExchangeException.OperationNotSupported> {
            registry.issueCredential("test", createTestIssueRequest())
        }

        // Test requestProof
        assertFailsWith<ExchangeException.OperationNotSupported> {
            registry.requestProof("test", createTestProofRequest())
        }

        // Test presentProof
        assertFailsWith<ExchangeException.OperationNotSupported> {
            registry.presentProof("test", createTestPresentationRequest())
        }
    }

    // Helper functions

    private fun createTestOfferRequest(): CredentialOfferRequest {
        return CredentialOfferRequest(
            issuerDid = "did:key:issuer",
            holderDid = "did:key:holder",
            credentialPreview = CredentialPreview(
                attributes = listOf(
                    CredentialAttribute("name", "Alice")
                )
            ),
            options = emptyMap()
        )
    }

    private fun createTestRequestRequest(): CredentialRequestRequest {
        return CredentialRequestRequest(
            holderDid = "did:key:holder",
            issuerDid = "did:key:issuer",
            offerId = "offer-123",
            options = emptyMap()
        )
    }

    private fun createTestIssueRequest(): CredentialIssueRequest {
        return CredentialIssueRequest(
            issuerDid = "did:key:issuer",
            holderDid = "did:key:holder",
            credential = createTestCredential(),
            requestId = "request-123",
            options = emptyMap()
        )
    }

    private fun createTestProofRequest(): ProofRequestRequest {
        return ProofRequestRequest(
            verifierDid = "did:key:verifier",
            proverDid = "did:key:prover",
            name = "Proof Request",
            version = "1.0",
            requestedAttributes = emptyMap(),
            requestedPredicates = emptyMap(),
            options = emptyMap()
        )
    }

    private fun createTestPresentationRequest(): ProofPresentationRequest {
        return ProofPresentationRequest(
            proverDid = "did:key:prover",
            verifierDid = "did:key:verifier",
            presentation = createTestPresentation(),
            requestId = "proof-request-123",
            options = emptyMap()
        )
    }

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("name", "Alice")
            },
            issuanceDate = Clock.System.now().toString()
        )
    }

    private fun createTestPresentation(): VerifiablePresentation {
        return VerifiablePresentation(
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(createTestCredential()),
            holder = "did:key:holder"
        )
    }

    private fun createMockProtocol(
        name: String,
        supportedOperations: Set<ExchangeOperation> = setOf(
            ExchangeOperation.OFFER_CREDENTIAL,
            ExchangeOperation.REQUEST_CREDENTIAL,
            ExchangeOperation.ISSUE_CREDENTIAL,
            ExchangeOperation.REQUEST_PROOF,
            ExchangeOperation.PRESENT_PROOF
        )
    ): CredentialExchangeProtocol {
        return object : CredentialExchangeProtocol {
            override val protocolName = name
            override val supportedOperations = supportedOperations

            override suspend fun offerCredential(request: CredentialOfferRequest): CredentialOfferResponse {
                return CredentialOfferResponse(
                    offerId = "offer-123",
                    offerData = buildJsonObject {},
                    protocolName = name
                )
            }

            override suspend fun requestCredential(request: CredentialRequestRequest): CredentialRequestResponse {
                return CredentialRequestResponse(
                    requestId = "request-123",
                    requestData = buildJsonObject {},
                    protocolName = name
                )
            }

            override suspend fun issueCredential(request: CredentialIssueRequest): CredentialIssueResponse {
                return CredentialIssueResponse(
                    issueId = "issue-123",
                    credential = createTestCredential(),
                    issueData = buildJsonObject {},
                    protocolName = name
                )
            }

            override suspend fun requestProof(request: ProofRequestRequest): ProofRequestResponse {
                return ProofRequestResponse(
                    requestId = "proof-request-123",
                    requestData = buildJsonObject {},
                    protocolName = name
                )
            }

            override suspend fun presentProof(request: ProofPresentationRequest): ProofPresentationResponse {
                return ProofPresentationResponse(
                    presentationId = "presentation-123",
                    presentation = createTestPresentation(),
                    presentationData = buildJsonObject {},
                    protocolName = name
                )
            }
        }
    }
}

