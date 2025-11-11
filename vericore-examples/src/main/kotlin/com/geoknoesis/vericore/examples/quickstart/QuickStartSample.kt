package com.geoknoesis.vericore.examples.quickstart

import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.anchor.BlockchainAnchorRegistry
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.json.DigestUtils
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Minimal runnable quick-start sample mirroring the documentation flow.
 * Invoke using `./gradlew :vericore-examples:runQuickStartSample`.
 */
fun main(): Unit = runBlocking {
    val vericore = VeriCore.create()

    val credentialSubject = buildJsonObject {
        put("id", "did:key:holder-placeholder")
        put("name", "Alice Example")
        put("role", "Site Reliability Engineer")
    }
    val digest = DigestUtils.sha256DigestMultibase(credentialSubject)
    println("Canonical credential-subject digest: $digest")

    val issuerDocument = vericore.createDid().getOrThrow()
    val issuerDid = issuerDocument.id
    val issuerKeyId = issuerDocument.verificationMethod.firstOrNull()?.id
        ?: error("No verification method generated for $issuerDid")
    println("Issuer DID: $issuerDid (keyId=$issuerKeyId)")

    val credential = vericore.issueCredential(
        issuerDid = issuerDid,
        issuerKeyId = issuerKeyId,
        credentialSubject = credentialSubject,
        types = listOf("VerifiableCredential", "QuickStartCredential")
    ).getOrThrow()
    println("Issued credential id: ${credential.id}")

    vericore.verifyCredential(credential).fold(
        onSuccess = { verification ->
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
        },
        onFailure = { error ->
            println("Verification failed: ${error.message}")
        }
    )

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

