# Credential verification and tamper rejection

This reusable function issues a credential, verifies it before and after JSON serialization, and rejects a modified signed claim. Local domain examples invoke it through `:distribution:examples:checkDocumentationExamples`; their JUnit wrappers propagate failed assertions. The context is registered locally. This does not qualify remote context resolution or a hosted KMS.

The source block is an exact copy of a compiled SDK file. Documentation CI checks synchronization, and required test gates check execution.

<!-- example-source: distribution/examples/src/main/kotlin/org/trustweave/examples/scenarios/SignedScenario.kt -->
```kotlin
package org.trustweave.examples.scenarios

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.credential.jsonld.JsonLdContexts
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.quickStart
import org.trustweave.trust.types.getOrThrowDid

/** Offline signing and tamper-detection workflow shared by the small domain examples. */
suspend fun runSignedScenario(
    name: String,
    credentialType: String,
    claims: Map<String, String>,
    tamperedClaim: String,
    tamperedValue: String,
) {
    require(claims.isNotEmpty())
    require(tamperedClaim in claims && claims[tamperedClaim] != tamperedValue)
    val contextUrl = "https://contexts.trustweave.example/scenarios/$name/v1"
    val vocabulary = "https://vocab.trustweave.example/scenarios/$name#"
    val context =
        buildJsonObject {
            put(
                "@context",
                buildJsonObject {
                    put(credentialType, vocabulary + credentialType)
                    claims.keys.forEach { put(it, vocabulary + it) }
                },
            )
        }
    JsonLdContexts.register(contextUrl, context.toString())
    val sdk = TrustWeave.quickStart()
    try {
        val issuer = sdk.createDid().getOrThrowDid()
        val holder = sdk.createDid().getOrThrowDid()
        val credential =
            sdk
                .issue {
                    credential {
                        type(credentialType)
                        issuer(issuer)
                        subject(holder.value) { claims.forEach { (key, value) -> key to value } }
                    }
                    signedBy(issuer)
                    additionalOption(JsonLdContexts.CONTEXTS_PROOF_OPTION, listOf(contextUrl))
                }.getOrThrow()
        check(credential.credentialSubject.claims == claims.mapValues { JsonPrimitive(it.value) })
        check(sdk.verify(credential) is VerificationResult.Valid) { "$name: original verification failed" }
        val json = Json { classDiscriminator = "@type" }
        val restored = json.decodeFromString<VerifiableCredential>(json.encodeToString(credential))
        check(restored == credential) { "$name: JSON changed the credential" }
        check(sdk.verify(restored) is VerificationResult.Valid) { "$name: round-trip verification failed" }
        val altered =
            restored.copy(
                credentialSubject =
                    restored.credentialSubject.copy(
                        claims = restored.credentialSubject.claims + (tamperedClaim to JsonPrimitive(tamperedValue)),
                    ),
            )
        check(sdk.verify(altered) !is VerificationResult.Valid) { "$name: altered claim was accepted" }
        println("$name: original verified; JSON round-trip verified; tampering rejected")
    } finally {
        sdk.close()
    }
}
```

[All verified examples](README.md) ? [Testing acceptance](../../contributing/testing/acceptance.md)
