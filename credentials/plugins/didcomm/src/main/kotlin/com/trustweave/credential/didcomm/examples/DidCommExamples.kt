package com.trustweave.credential.didcomm.examples

import com.trustweave.credential.didcomm.*
import com.trustweave.credential.didcomm.protocol.*
import com.trustweave.credential.didcomm.utils.DidCommUtils
import com.trustweave.did.DidDocument
import com.trustweave.did.VerificationMethod
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.runBlocking

/**
 * Example usage of DIDComm V2 for credential exchange.
 */
object DidCommExamples {

    /**
     * Example: Complete credential issuance flow.
     */
    fun credentialIssuanceExample(kms: KeyManagementService) = runBlocking {
        // Setup - KMS should be provided by caller
        val issuerDid = "did:key:issuer"
        val holderDid = "did:key:holder"

        // Mock DID resolution
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            DidDocument(
                id = did,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = "$did#key-1",
                        type = "Ed25519VerificationKey2020",
                        controller = did,
                        publicKeyJwk = mapOf(
                            "kty" to "OKP",
                            "crv" to "Ed25519",
                            "x" to "test-key"
                        )
                    )
                ),
                keyAgreement = listOf("$did#key-1")
            )
        }

        // Create DIDComm service
        val didcomm = DidCommFactory.createInMemoryService(kms, resolveDid)

        // Step 1: Issuer creates credential offer
        val preview = com.trustweave.credential.exchange.CredentialPreview(
            attributes = listOf(
                com.trustweave.credential.exchange.CredentialAttribute("name", "Alice"),
                com.trustweave.credential.exchange.CredentialAttribute("email", "alice@example.com")
            )
        )

        val offer = CredentialProtocol.createCredentialOffer(
            fromDid = issuerDid,
            toDid = holderDid,
            credentialPreview = preview
        )

        // Step 2: Send offer (in real implementation, this would be delivered via HTTP/WebSocket)
        val offerId = didcomm.sendMessage(
            message = offer,
            fromDid = issuerDid,
            fromKeyId = "$issuerDid#key-1",
            toDid = holderDid,
            toKeyId = "$holderDid#key-1"
        )

        println("Sent credential offer: $offerId")

        // Step 3: Holder receives offer and creates request
        val receivedOffer = didcomm.getMessage(offerId)
        if (receivedOffer != null) {
            val request = CredentialProtocol.createCredentialRequest(
                fromDid = holderDid,
                toDid = issuerDid,
                thid = receivedOffer.id
            )

            val requestId = didcomm.sendMessage(
                message = request,
                fromDid = holderDid,
                fromKeyId = "$holderDid#key-1",
                toDid = issuerDid,
                toKeyId = "$issuerDid#key-1"
            )

            println("Sent credential request: $requestId")
        }
    }

    /**
     * Example: Basic message exchange.
     */
    fun basicMessageExample(kms: KeyManagementService) = runBlocking {
        // KMS should be provided by caller
        val aliceDid = "did:key:alice"
        val bobDid = "did:key:bob"

        val resolveDid: suspend (String) -> DidDocument? = { did ->
            DidDocument(
                id = did,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = "$did#key-1",
                        type = "Ed25519VerificationKey2020",
                        controller = did,
                        publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519", "x" to "test")
                    )
                ),
                keyAgreement = listOf("$did#key-1")
            )
        }

        val didcomm = DidCommFactory.createInMemoryService(kms, resolveDid)

        // Alice sends message to Bob
        val message = BasicMessageProtocol.createBasicMessage(
            fromDid = aliceDid,
            toDid = bobDid,
            content = "Hello, Bob!",
            thid = "conversation-123"
        )

        val messageId = didcomm.sendMessage(
            message = message,
            fromDid = aliceDid,
            fromKeyId = "$aliceDid#key-1",
            toDid = bobDid,
            toKeyId = "$bobDid#key-1"
        )

        println("Sent message: $messageId")

        // Bob retrieves messages
        val bobMessages = didcomm.getMessagesForDid(bobDid)
        println("Bob has ${bobMessages.size} messages")

        // Get conversation thread
        val threadMessages = didcomm.getThreadMessages("conversation-123")
        println("Thread has ${threadMessages.size} messages")
    }

    /**
     * Example: Finding key agreement keys from DID documents.
     */
    fun keyAgreementExample() = runBlocking {
        val document = DidDocument(
            id = "did:key:example",
            verificationMethod = listOf(
                VerificationMethod(
                    id = "did:key:example#key-1",
                    type = "X25519KeyAgreementKey2020",
                    controller = "did:key:example",
                    publicKeyJwk = mapOf("kty" to "OKP", "crv" to "X25519", "x" to "key")
                )
            ),
            keyAgreement = listOf("did:key:example#key-1")
        )

        // Find key agreement key
        val keyAgreementKey = DidCommUtils.findKeyAgreementKey(document)
        println("Found key agreement key: ${keyAgreementKey?.id}")

        // Check for DIDComm service
        val hasService = DidCommUtils.hasDidCommService(document)
        println("Has DIDComm service: $hasService")

        // Get service endpoint
        val endpoint = DidCommUtils.getDidCommServiceEndpoint(document)
        println("Service endpoint: $endpoint")
    }
}

