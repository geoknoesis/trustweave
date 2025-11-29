package com.trustweave.credential.exchange

// Note: This file contains examples that require plugin dependencies
// Commented out plugin-specific imports as they're not available in core module
// import com.trustweave.credential.didcomm.DidCommFactory
// import com.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.did.DidDocument
// import com.trustweave.kms.KeyManagementService
// import com.trustweave.testkit.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

/**
 * Examples demonstrating the protocol abstraction layer.
 */
object ExchangeProtocolExamples {

    /**
     * Example: Using protocol abstraction with multiple protocols.
     */
    fun multiProtocolExample() = runBlocking {
        // This example requires plugin dependencies that are not available in core module
        // Uncomment and add plugin dependencies to use
        /*
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            // Mock DID resolution
            DidDocument(id = did, verificationMethod = emptyList())
        }

        // Create protocol registry
        val registry = CredentialExchangeProtocolRegistry()

        // Register DIDComm protocol
        val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
        val didCommProtocol = DidCommExchangeProtocol(didCommService)
        registry.register(didCommProtocol)

        // Register OIDC4VCI protocol (when implemented)
        // val oidc4vciProtocol = Oidc4VciExchangeProtocol(oidc4vciService)
        // registry.register(oidc4vciProtocol)

        // Use protocol-agnostic API
        val offer = registry.offerCredential(
            protocolName = "didcomm",
            request = CredentialOfferRequest(
                issuerDid = "did:key:issuer",
                holderDid = "did:key:holder",
                credentialPreview = CredentialPreview(
                    attributes = listOf(
                        CredentialAttribute("name", "Alice"),
                        CredentialAttribute("email", "alice@example.com")
                    )
                ),
                options = mapOf(
                    "fromKeyId" to "did:key:issuer#key-1",
                    "toKeyId" to "did:key:holder#key-1"
                )
            )
        )

        println("Created offer with protocol: ${offer.protocolName}")
        println("Offer ID: ${offer.offerId}")
        */
    }

    /**
     * Example: Switching between protocols.
     */
    fun protocolSwitchingExample() = runBlocking {
        // This example requires plugin dependencies that are not available in core module
        /*
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            DidDocument(id = did, verificationMethod = emptyList())
        }

        val registry = CredentialExchangeProtocolRegistry()

        // Register multiple protocols
        val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
        registry.register(DidCommExchangeProtocol(didCommService))

        // Same API, different protocols
        val didCommOffer = registry.offerCredential(
            protocolName = "didcomm",
            request = CredentialOfferRequest(
                issuerDid = "did:key:issuer",
                holderDid = "did:key:holder",
                credentialPreview = CredentialPreview(
                    attributes = listOf(CredentialAttribute("name", "Alice"))
                ),
                options = mapOf(
                    "fromKeyId" to "did:key:issuer#key-1",
                    "toKeyId" to "did:key:holder#key-1"
                )
            )
        )

        // Could switch to OIDC4VCI with same API
        // val oidc4vciOffer = registry.offerCredential(
        //     protocolName = "oidc4vci",
        //     request = sameRequest
        // )

        println("Protocol: ${didCommOffer.protocolName}")
        */
    }

    /**
     * Example: Complete credential exchange flow.
     */
    fun completeExchangeFlowExample() = runBlocking {
        // This example requires plugin dependencies that are not available in core module
        // Uncomment and add plugin dependencies to use
        /*
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            DidDocument(id = did, verificationMethod = emptyList())
        }

        val registry = CredentialExchangeProtocolRegistry()
        // Note: DidCommExchangeProtocol is plugin-specific and not available in core module
        // val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid)
        // registry.register(DidCommExchangeProtocol(didCommService))

        val issuerDid = "did:key:issuer"
        val holderDid = "did:key:holder"

        // Step 1: Offer credential
        val offer = registry.offerCredential(
            protocolName = "didcomm",
            request = CredentialOfferRequest(
                issuerDid = issuerDid,
                holderDid = holderDid,
                credentialPreview = CredentialPreview(
                    attributes = listOf(
                        CredentialAttribute("name", "Alice"),
                        CredentialAttribute("email", "alice@example.com")
                    )
                ),
                options = mapOf(
                    "fromKeyId" to "$issuerDid#key-1",
                    "toKeyId" to "$holderDid#key-1"
                )
            )
        )

        // Step 2: Request credential
        val credentialRequest = registry.requestCredential(
            protocolName = "didcomm",
            request = CredentialRequestRequest(
                holderDid = holderDid,
                issuerDid = issuerDid,
                offerId = offer.offerId,
                options = mapOf(
                    "fromKeyId" to "$holderDid#key-1",
                    "toKeyId" to "$issuerDid#key-1"
                )
            )
        )

        // Step 3: Issue credential
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = issuerDid,
            credentialSubject = kotlinx.serialization.json.buildJsonObject {
                put("id", kotlinx.serialization.json.JsonPrimitive(holderDid))
                put("name", kotlinx.serialization.json.JsonPrimitive("Alice"))
                put("email", kotlinx.serialization.json.JsonPrimitive("alice@example.com"))
            },
            issuanceDate = java.time.Instant.now().toString()
        )

        val issue = registry.issueCredential(
            protocolName = "didcomm",
            request = CredentialIssueRequest(
                issuerDid = issuerDid,
                holderDid = holderDid,
                credential = credential,
                requestId = credentialRequest.requestId,
                options = mapOf(
                    "fromKeyId" to "$issuerDid#key-1",
                    "toKeyId" to "$holderDid#key-1"
                )
            )
        )

        println("Issued credential: ${issue.credential.id}")
        println("Protocol: ${issue.protocolName}")
        */
    }
}

