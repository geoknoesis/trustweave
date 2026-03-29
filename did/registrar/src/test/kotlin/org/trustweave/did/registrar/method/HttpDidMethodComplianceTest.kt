package org.trustweave.did.registrar.method

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.KeyPurpose
import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.PollableRegistrar
import org.trustweave.did.registrar.model.*
import org.trustweave.did.registration.model.DidRegistrationSpec
import org.trustweave.did.registration.model.DriverConfig
import org.trustweave.did.model.MethodCapabilities
import org.trustweave.did.resolver.DidResolutionResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compliance tests for HttpDidMethod DID Registration specification.
 */
class HttpDidMethodComplianceTest {

    @Test
    fun `test automatic polling for long-running operations`() = runTest {
        val spec = createSpecWithRegistrarUrl()
        val mockRegistrar = MockPollableRegistrar().apply {
            nextResponse = DidRegistrationResponse(
                jobId = "job-123",
                didState = DidState(state = OperationState.WAIT, did = "did:test:123")
            )
            responsesAfterWait = listOf(
                DidRegistrationResponse(jobId = null, didState = DidState(
                    state = OperationState.FINISHED, did = "did:test:123", didDocument = createTestDocument()
                ))
            )
        }
        val method = HttpDidMethod(spec, mockRegistrar)
        val document = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519, purposes = listOf(KeyPurpose.AUTHENTICATION)))
        assertNotNull(document)
        assertEquals("did:test:123", document.id.value)
        assertTrue(mockRegistrar.pollCount > 0, "Should have polled for completion")
    }

    @Test
    fun `test ACTION state handling with redirect`() = runTest {
        val spec = createSpecWithRegistrarUrl()
        val mockRegistrar = MockPollableRegistrar().apply {
            nextResponse = DidRegistrationResponse(jobId = null, didState = DidState(
                state = OperationState.ACTION, action = Action(type = "redirect", url = "https://example.com/oauth", description = "Redirect to OAuth provider")
            ))
        }
        val method = HttpDidMethod(spec, mockRegistrar)
        val exception = assertThrows<DidException.RequiresAction> {
            method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519, purposes = listOf(KeyPurpose.AUTHENTICATION)))
        }
        assertEquals("redirect", exception.action.type)
        assertEquals("https://example.com/oauth", exception.action.url)
    }

    @Test
    fun `test FAILED state handling`() = runTest {
        val spec = createSpecWithRegistrarUrl()
        val mockRegistrar = MockPollableRegistrar().apply {
            nextResponse = DidRegistrationResponse(jobId = null, didState = DidState(state = OperationState.FAILED, reason = "Insufficient funds"))
        }
        val method = HttpDidMethod(spec, mockRegistrar)
        val exception = assertThrows<DidException.DidCreationFailed> {
            method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519, purposes = listOf(KeyPurpose.AUTHENTICATION)))
        }
        assertTrue(exception.reason.contains("Insufficient funds"))
    }

    @Test
    fun `test FINISHED state success`() = runTest {
        val spec = createSpecWithRegistrarUrl()
        val mockRegistrar = MockPollableRegistrar().apply {
            nextResponse = DidRegistrationResponse(jobId = null, didState = DidState(
                state = OperationState.FINISHED, did = "did:test:123", didDocument = createTestDocument()
            ))
        }
        val method = HttpDidMethod(spec, mockRegistrar)
        val document = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519, purposes = listOf(KeyPurpose.AUTHENTICATION)))
        assertNotNull(document)
        assertEquals("did:test:123", document.id.value)
    }

    @Test
    fun `test deactivateDid with automatic polling`() = runTest {
        val spec = createSpecWithRegistrarUrl()
        val mockRegistrar = MockPollableRegistrar().apply {
            nextResponse = DidRegistrationResponse(jobId = "job-deactivate-123", didState = DidState(state = OperationState.WAIT, did = "did:test:123"))
            responsesAfterWait = listOf(DidRegistrationResponse(jobId = null, didState = DidState(state = OperationState.FINISHED, did = "did:test:123")))
        }
        val method = HttpDidMethod(spec, mockRegistrar)
        val result = method.deactivateDid(Did("did:test:123"))
        assertTrue(result, "Deactivation should succeed")
        assertTrue(mockRegistrar.pollCount > 0, "Should have polled for deactivation completion")
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun createSpecWithRegistrarUrl() = DidRegistrationSpec(
        name = "test",
        driver = DriverConfig(type = "universal-resolver", baseUrl = "https://dev.uniresolver.io"),
        capabilities = MethodCapabilities(resolve = true, create = true, update = true, deactivate = true)
    )

    private fun createTestDocument() = DidDocument(
        id = Did("did:test:123"), verificationMethod = emptyList(), authentication = emptyList(),
        assertionMethod = emptyList(), keyAgreement = emptyList(), capabilityInvocation = emptyList(),
        capabilityDelegation = emptyList(), service = emptyList()
    )

    private class MockPollableRegistrar : DidRegistrar, PollableRegistrar {
        var nextResponse: DidRegistrationResponse? = null
        var responsesAfterWait: List<DidRegistrationResponse> = emptyList()
        var pollCount = 0
        private var waitStateIndex = 0

        override suspend fun createDid(method: String, options: CreateDidOptions) = nextResponse ?: finished()
        override suspend fun updateDid(did: String, document: DidDocument, options: UpdateDidOptions) = nextResponse ?: finished()
        override suspend fun deactivateDid(did: String, options: DeactivateDidOptions) = nextResponse ?: finished()

        override suspend fun getOperationStatus(jobId: String): DidRegistrationResponse {
            pollCount++
            return if (waitStateIndex < responsesAfterWait.size) responsesAfterWait[waitStateIndex++] else finished()
        }

        override suspend fun waitForCompletion(response: DidRegistrationResponse): DidRegistrationResponse {
            if (response.isComplete()) return response
            val jobId = response.jobId ?: return response
            var cur = response
            var attempts = 0
            while (!cur.isComplete() && attempts < 10) {
                kotlinx.coroutines.delay(10)
                cur = getOperationStatus(jobId)
                attempts++
            }
            return cur
        }

        private fun finished() = DidRegistrationResponse(jobId = null, didState = DidState(
            state = OperationState.FINISHED, did = "did:test:123",
            didDocument = DidDocument(id = Did("did:test:123"), verificationMethod = emptyList(),
                authentication = emptyList(), assertionMethod = emptyList(), keyAgreement = emptyList(),
                capabilityInvocation = emptyList(), capabilityDelegation = emptyList(), service = emptyList())
        ))
    }
}
