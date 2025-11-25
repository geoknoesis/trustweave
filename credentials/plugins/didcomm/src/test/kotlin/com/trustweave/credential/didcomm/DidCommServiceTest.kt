package com.trustweave.credential.didcomm

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.models.DidCommMessageTypes
import com.trustweave.credential.didcomm.protocol.BasicMessageProtocol
import com.trustweave.credential.didcomm.protocol.CredentialProtocol
import com.trustweave.did.DidDocument
import com.trustweave.did.VerificationMethod
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService
import com.trustweave.testkit.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DidCommServiceTest {
    
    @Test
    fun testBasicMessageCreation() = runBlocking {
        val message = BasicMessageProtocol.createBasicMessage(
            fromDid = "did:key:alice",
            toDid = "did:key:bob",
            content = "Hello, Bob!"
        )
        
        assertEquals("did:key:alice", message.from)
        assertEquals(listOf("did:key:bob"), message.to)
        assertEquals(DidCommMessageTypes.BASIC_MESSAGE, message.type)
        assertNotNull(message.id)
    }
    
    @Test
    fun testCredentialOfferCreation() = runBlocking {
        val preview = com.trustweave.credential.exchange.CredentialPreview(
            attributes = listOf(
                com.trustweave.credential.exchange.CredentialAttribute(
                    name = "name",
                    value = "Alice"
                ),
                com.trustweave.credential.exchange.CredentialAttribute(
                    name = "email",
                    value = "alice@example.com"
                )
            )
        )
        
        val offer = CredentialProtocol.createCredentialOffer(
            fromDid = "did:key:issuer",
            toDid = "did:key:holder",
            credentialPreview = preview
        )
        
        assertEquals("did:key:issuer", offer.from)
        assertEquals(listOf("did:key:holder"), offer.to)
        assertEquals(DidCommMessageTypes.CREDENTIAL_OFFER, offer.type)
        assertNotNull(offer.id)
    }
    
    @Test
    fun testMessageStorage() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val resolveDid: suspend (String) -> DidDocument? = { did ->
            // Mock DID resolution
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
        
        val service = DidCommFactory.createInMemoryService(kms, resolveDid)
        
        val message = BasicMessageProtocol.createBasicMessage(
            fromDid = "did:key:alice",
            toDid = "did:key:bob",
            content = "Test message"
        )
        
        val messageId = service.storeMessage(message)
        assertEquals(message.id, messageId)
        
        val retrieved = service.getMessage(messageId)
        assertNotNull(retrieved)
        assertEquals(message.id, retrieved.id)
        assertEquals(message.type, retrieved.type)
    }
    
    @Test
    fun testMessageThreading() = runBlocking {
        val kms = InMemoryKeyManagementService()
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
        
        val service = DidCommFactory.createInMemoryService(kms, resolveDid)
        
        val thid = "thread-123"
        
        val message1 = BasicMessageProtocol.createBasicMessage(
            fromDid = "did:key:alice",
            toDid = "did:key:bob",
            content = "First message",
            thid = thid
        )
        
        val message2 = BasicMessageProtocol.createBasicMessage(
            fromDid = "did:key:bob",
            toDid = "did:key:alice",
            content = "Reply",
            thid = thid
        )
        
        service.storeMessage(message1)
        service.storeMessage(message2)
        
        val threadMessages = service.getThreadMessages(thid)
        assertEquals(2, threadMessages.size)
    }
}

