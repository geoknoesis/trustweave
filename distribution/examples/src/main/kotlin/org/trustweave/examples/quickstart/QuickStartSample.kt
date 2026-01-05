package org.trustweave.examples.quickstart

import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.VerificationResult
import org.trustweave.trust.types.*
import org.trustweave.trust.dsl.credential.DidMethods.KEY
import org.trustweave.trust.dsl.credential.KeyAlgorithms.ED25519
import org.trustweave.trust.dsl.credential.KmsProviders.IN_MEMORY
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.ProofType
import org.trustweave.core.util.DigestUtils
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.getOrFail
import org.trustweave.did.identifiers.extractKeyId
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
            provider(IN_MEMORY)
            algorithm(ED25519)
        }
        did {
            method(KEY) {
                algorithm(ED25519)
            }
        }
    }

    // Note: Digest computation requires JsonObject, so we create it separately for that purpose
    val credentialSubjectForDigest = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    }
    val digest = DigestUtils.sha256DigestMultibase(credentialSubjectForDigest)
    println("Canonical credential-subject digest: $digest")

    val issuerDid = trustweave.createDid().getOrFail()
    println("Issuer DID: ${issuerDid.value}")

    val holderDid = org.trustweave.did.identifiers.Did("did:key:holder-placeholder")

    val credential = trustweave.issue {
        credential {
            type("QuickStartCredential")
            issuer(issuerDid)
            subject {
                id(holderDid)
                "name" to "Alice Example"
                "role" to "Site Reliability Engineer"
            }
            issued(kotlinx.datetime.Clock.System.now())
        }
        signedBy(issuerDid)
    }.getOrFail()
    println("Issued credential id: ${credential.id}")

    val verification = trustweave.verify {
        credential(credential)
    }
    when (verification) {
        is VerificationResult.Valid -> {
            println("Verification succeeded")
            if (verification.warnings.isNotEmpty()) {
                println("Warnings: ${verification.warnings}")
            }
        }
        is VerificationResult.Invalid -> {
            println("Verification failed: ${verification.errors.joinToString()}")
        }
    }

    val anchorRegistry = BlockchainAnchorRegistry().apply {
        register("inmemory:anchor", InMemoryBlockchainAnchorClient("inmemory:anchor"))
    }
    val anchorClient = requireNotNull(anchorRegistry.get("inmemory:anchor")) {
        "inmemory anchor client not registered"
    }

    runCatching {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            classDiscriminator = "@type" // Use @type instead of type to avoid conflict with LinkedDataProof.type
        }
        val payload = json.encodeToJsonElement(credential)
        val anchorResult = anchorClient.writePayload(payload)
        println("Anchored credential on ${anchorResult.ref.chainId}: ${anchorResult.ref.txHash}")
    }.onFailure { error ->
        println("Anchoring failed: ${error.message}")
    }
}

