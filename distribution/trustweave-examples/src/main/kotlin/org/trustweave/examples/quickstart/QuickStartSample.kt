package org.trustweave.examples.quickstart

import org.trustweave.TrustWeave
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.ProofType
import org.trustweave.core.util.DigestUtils
import org.trustweave.services.IssuanceConfig
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
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
    val trustweave = TrustWeave.create()

    val credentialSubject = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    }
    val digest = DigestUtils.sha256DigestMultibase(credentialSubject)
    println("Canonical credential-subject digest: $digest")

    val issuerDocument = trustweave.dids.create()
    val issuerDid = issuerDocument.id
    val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
        ?: error("No verification method generated for $issuerDid")
    println("Issuer DID: $issuerDid (keyId=$issuerKeyId)")

    val credential = trustweave.credentials.issue(
        issuer = issuerDid,
        subject = credentialSubject,
        config = IssuanceConfig(
            proofType = ProofType.Ed25519Signature2020,
            keyId = issuerKeyId,
            issuerDid = issuerDid
        ),
        types = listOf("VerifiableCredential", "QuickStartCredential")
    )
    println("Issued credential id: ${credential.id}")

    val verification = trustweave.credentials.verify(credential)
    if (verification.valid) {
        println(
            "Verification succeeded (proof=${verification.proofValid}, issuer=${verification.issuerValid}, " +
                "revocation=${verification.notRevoked})"
        )
        if (verification.warnings.isNotEmpty()) {
            println("Warnings: ${verification.warnings}")
        }
    } else {
        println("Verification returned errors: ${verification.errors}")
    }

    val anchorRegistry = BlockchainAnchorRegistry().apply {
        register("inmemory:anchor", InMemoryBlockchainAnchorClient("inmemory:anchor"))
    }
    val anchorClient = requireNotNull(anchorRegistry.get("inmemory:anchor")) {
        "inmemory anchor client not registered"
    }

    runCatching {
        val payload = Json.encodeToJsonElement(credential)
        val anchorResult = anchorClient.writePayload(payload)
        println("Anchored credential on ${anchorResult.ref.chainId}: ${anchorResult.ref.txHash}")
    }.onFailure { error ->
        println("Anchoring failed: ${error.message}")
    }
}

