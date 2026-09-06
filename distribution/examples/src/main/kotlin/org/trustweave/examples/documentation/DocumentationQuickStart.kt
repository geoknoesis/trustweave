package org.trustweave.examples.documentation

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.jsonld.JsonLdContexts
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.quickStart
import org.trustweave.trust.types.getOrThrowDid

/** Local example: the vocabulary is registered in-process; no remote context fetch. */
fun main() =
    runBlocking {
        val contextUrl = "https://example.org/contexts/person/v1"
        JsonLdContexts.register(
            contextUrl,
            """{"@context":{"PersonCredential":"https://example.org/vocab#PersonCredential","name":"https://schema.org/name"}}""",
        )
        val trustWeave = TrustWeave.quickStart()
        try {
            val issuer = trustWeave.createDid().getOrThrowDid()
            val holder = trustWeave.createDid().getOrThrowDid()
            val credential =
                trustWeave
                    .issue {
                        credential {
                            type("PersonCredential")
                            issuer(issuer)
                            subject(holder.value) { "name" to "Alice" }
                        }
                        signedBy(issuer)
                        additionalOption(JsonLdContexts.CONTEXTS_PROOF_OPTION, listOf(contextUrl))
                    }.getOrThrow()
            check(trustWeave.verify(credential) is VerificationResult.Valid) {
                "The issued credential did not verify"
            }
            println("Credential verified")
        } finally {
            trustWeave.close()
        }
    }
