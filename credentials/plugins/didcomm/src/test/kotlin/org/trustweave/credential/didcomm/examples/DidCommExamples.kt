package org.trustweave.credential.didcomm.examples

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.secret.Secret
import org.trustweave.credential.didcomm.*
import org.trustweave.credential.didcomm.crypto.interop.MapSecretResolver
import org.trustweave.credential.didcomm.protocol.*
import org.trustweave.credential.didcomm.utils.findKeyAgreementKey
import org.trustweave.credential.didcomm.utils.hasDidCommService
import org.trustweave.credential.didcomm.utils.getDidCommServiceEndpoint
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.runBlocking

/**
 * Example usage of DIDComm V2 for credential exchange.
 *
 * The examples generate real X25519 key-agreement key pairs and register the private JWKs in a
 * [MapSecretResolver] so that didcomm-java performs real ECDH-based encryption. Placeholder
 * crypto is not available; DIDComm encryption always requires real key material.
 */
object DidCommExamples {

    /**
     * Example: Complete credential issuance flow.
     */
    fun credentialIssuanceExample(kms: KeyManagementService) = runBlocking {
        // Setup - KMS should be provided by caller
        val issuerDid = "did:key:issuer"
        val holderDid = "did:key:holder"

        // Real X25519 key agreement keys for both parties
        val issuerKeyPair = generateX25519KeyPair()
        val holderKeyPair = generateX25519KeyPair()

        val documents = mapOf(
            issuerDid to didDocumentFor(issuerDid, issuerKeyPair),
            holderDid to didDocumentFor(holderDid, holderKeyPair),
        )
        val resolveDid: suspend (String) -> DidDocument? = { didStr -> documents[didStr] }

        // Private key material for pack/unpack (in production, load from secure storage)
        val secretResolver = MapSecretResolver()
        secretResolver.put("$issuerDid#key-1", secretFor("$issuerDid#key-1", issuerKeyPair))
        secretResolver.put("$holderDid#key-1", secretFor("$holderDid#key-1", holderKeyPair))

        // Create DIDComm service with real (didcomm-java) crypto
        val didcomm = DidCommFactory.createInMemoryService(kms, resolveDid, secretResolver)

        // Step 1: Issuer creates credential offer
        val preview = org.trustweave.credential.exchange.model.CredentialPreview(
            attributes = listOf(
                org.trustweave.credential.exchange.model.CredentialAttribute("name", "Alice"),
                org.trustweave.credential.exchange.model.CredentialAttribute("email", "alice@example.com")
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

        val aliceKeyPair = generateX25519KeyPair()
        val bobKeyPair = generateX25519KeyPair()

        val documents = mapOf(
            aliceDid to didDocumentFor(aliceDid, aliceKeyPair),
            bobDid to didDocumentFor(bobDid, bobKeyPair),
        )
        val resolveDid: suspend (String) -> DidDocument? = { didStr -> documents[didStr] }

        val secretResolver = MapSecretResolver()
        secretResolver.put("$aliceDid#key-1", secretFor("$aliceDid#key-1", aliceKeyPair))
        secretResolver.put("$bobDid#key-1", secretFor("$bobDid#key-1", bobKeyPair))

        val didcomm = DidCommFactory.createInMemoryService(kms, resolveDid, secretResolver)

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
        val did = Did("did:key:example")
        val vmId = VerificationMethodId.parse("did:key:example#key-1", did)
        val document = DidDocument(
            id = did,
            verificationMethod = listOf(
                VerificationMethod(
                    id = vmId,
                    type = "X25519KeyAgreementKey2020",
                    controller = did,
                    publicKeyJwk = mapOf("kty" to "OKP", "crv" to "X25519", "x" to "key")
                )
            ),
            keyAgreement = listOf(vmId)
        )

        // Find key agreement key
        val keyAgreementKey = document.findKeyAgreementKey()
        println("Found key agreement key: ${keyAgreementKey?.id}")

        // Check for DIDComm service
        val hasService = document.hasDidCommService
        println("Has DIDComm service: $hasService")

        // Get service endpoint
        val endpoint = document.getDidCommServiceEndpoint()
        println("Service endpoint: $endpoint")
    }

    private fun generateX25519KeyPair(): OctetKeyPair =
        OctetKeyPairGenerator(Curve.X25519).generate()

    private fun didDocumentFor(didStr: String, keyPair: OctetKeyPair): DidDocument {
        val did = Did(didStr)
        val vmId = VerificationMethodId.parse("$didStr#key-1", did)
        return DidDocument(
            id = did,
            verificationMethod = listOf(
                VerificationMethod(
                    id = vmId,
                    type = "JsonWebKey2020",
                    controller = did,
                    publicKeyJwk = mapOf(
                        "kty" to keyPair.keyType.value,
                        "crv" to keyPair.curve.name,
                        "x" to keyPair.x.toString()
                    )
                )
            ),
            keyAgreement = listOf(vmId)
        )
    }

    private fun secretFor(kid: String, keyPair: OctetKeyPair): Secret =
        Secret(
            kid,
            VerificationMethodType.JSON_WEB_KEY_2020,
            VerificationMaterial(VerificationMaterialFormat.JWK, keyPair.toJSONString())
        )
}
