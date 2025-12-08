package com.trustweave.examples.quickstart

import com.trustweave.trust.TrustWeave
import com.trustweave.trust.types.VerificationResult
import com.trustweave.trust.types.*
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.proof.ProofType
import com.trustweave.core.util.DigestUtils
import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Minimal runnable quick-start sample mirroring the documentation flow.
 * Invoke using `./gradlew :TrustWeave-examples:runQuickStartSample`.
 */
fun main(): Unit = runBlocking {
    val trustweave = TrustWeave.build {
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
    }

    val credentialSubject = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    }
    val digest = DigestUtils.sha256DigestMultibase(credentialSubject)
    println("Canonical credential-subject digest: $digest")

    val issuerDid = trustweave.createDid().getOrFail()
    
    // Resolve DID to get verification method
    val issuerDidResolution = trustweave.resolveDid(issuerDid)
    val issuerDidDoc = when (issuerDidResolution) {
        is com.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
        else -> throw IllegalStateException("Failed to resolve issuer DID")
    }
    val issuerKeyId = issuerDidDoc.verificationMethod.firstOrNull()?.id?.substringAfter("#")
        ?: error("No verification method generated for ${issuerDid.value}")
    println("Issuer DID: ${issuerDid.value} (keyId=$issuerKeyId)")

    val credential = trustweave.issue {
        credential {
            type("QuickStartCredential")
            issuer(issuerDid.value)
            subject {
                id("did:key:holder-placeholder")
                "name" to "Alice Example"
                "role" to "Site Reliability Engineer"
            }
            issued(kotlinx.datetime.Clock.System.now())
        }
        signedBy(issuerDid = issuerDid.value, keyId = issuerKeyId)
    }.getOrFail()
    println("Issued credential id: ${credential.id}")

    val verification = trustweave.verifyCredential(credential)
    if (verification.valid) {
        println(
            "Verification succeeded (proof=${verification.proofValid}, issuer=${verification.issuerValid}, " +
                "revocation=${verification.notRevoked})"
        )
        if (verification.allWarnings.isNotEmpty()) {
            println("Warnings: ${verification.allWarnings}")
        }
    } else {
        println("Verification returned errors: ${verification.allErrors}")
    }

    val anchorRegistry = BlockchainAnchorRegistry().apply {
        register("inmemory:anchor", InMemoryBlockchainAnchorClient("inmemory:anchor"))
    }
    val anchorClient = requireNotNull(anchorRegistry.get("inmemory:anchor")) {
        "inmemory anchor client not registered"
    }

    runCatching {
        val payload = Json.encodeToJsonElement(VerifiableCredential.serializer(), credential)
        val anchorResult = anchorClient.writePayload(payload)
        println("Anchored credential on ${anchorResult.ref.chainId}: ${anchorResult.ref.txHash}")
    }.onFailure { error ->
        println("Anchoring failed: ${error.message}")
    }
}

