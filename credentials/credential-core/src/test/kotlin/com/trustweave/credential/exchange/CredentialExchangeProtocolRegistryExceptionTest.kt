package com.trustweave.credential.exchange

import com.trustweave.credential.exchange.exception.ExchangeException
import com.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.core.identifiers.Iri
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

/**
 * Unit tests for exception handling in CredentialExchangeProtocolRegistry.
 * 
 * NOTE: This test file tests exception handling for a convenience API that may not exist.
 * The current ExchangeProtocolRegistry interface doesn't have convenience methods like offerCredential().
 * These tests may need to be refactored to use the actual API structure.
 */
class CredentialExchangeProtocolRegistryExceptionTest {

    // TODO: Refactor these tests to use the new ExchangeRequest API
    // The registry.get(protocolName)?.offer(request) pattern instead of registry.offerCredential()
    
    /*
    @Test
    fun `test ProtocolNotRegistered exception thrown`() = runBlocking {
        val registry = ExchangeProtocolRegistries.default()

        val exception = assertFailsWith<ExchangeException.ProtocolNotRegistered> {
            registry.offerCredential("unknown", createTestOfferRequest())
        }

        assertEquals("unknown", exception.protocolName)
        assertTrue(exception.availableProtocols.isEmpty())
        assertEquals("PROTOCOL_NOT_REGISTERED", exception.code)
    }
    */

    // Note: These tests use an old API that doesn't exist (registry.offerCredential(), createMockProtocol(), etc.)
    // They need to be refactored to use the new ExchangeRequest API
    // Commented out until refactored
    /*
    @Test
    fun `test ProtocolNotRegistered includes available protocols`() = runBlocking {
        // TODO: Refactor to use new API
    }

    @Test
    fun `test OperationNotSupported exception thrown`() = runBlocking {
        // TODO: Refactor to use new API
    }

    @Test
    fun `test all registry methods throw ProtocolNotRegistered`() = runBlocking {
        // TODO: Refactor to use new API
    }

    @Test
    fun `test all registry methods throw OperationNotSupported`() = runBlocking {
        // TODO: Refactor to use new API
    }
    */

    // Helper functions

    // TODO: Update these to use ExchangeRequest.Offer, ExchangeRequest.Request, etc.
    /*
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
    */

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromIri(
                com.trustweave.core.identifiers.Iri("urn:subject"),
                claims = mapOf("name" to JsonPrimitive("Alice"))
            ),
            issuanceDate = Clock.System.now()
        )
    }

    private fun createTestPresentation(): VerifiablePresentation {
        return VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = listOf(createTestCredential()),
            holder = Did("did:key:holder")
        )
    }

    // TODO: Update mock protocol to use new API (ExchangeRequest.Offer, etc.)
    /*
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
    */
}

