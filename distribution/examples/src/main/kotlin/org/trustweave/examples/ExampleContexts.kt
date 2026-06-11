package org.trustweave.examples

import org.trustweave.credential.jsonld.JsonLdContexts

/**
 * Shared JSON-LD `@context` for the runnable example scenarios.
 *
 * The demos issue Linked Data (`Ed25519Signature2020`) credentials whose `credentialSubject` uses
 * illustrative, domain-specific terms (`registrationNumber`, `make`, `activityType`, …). TrustWeave
 * fails issuance closed when such terms are not defined by any declared `@context` — otherwise they
 * would be silently dropped from the canonical form and left uncovered by the proof signature.
 *
 * Rather than enumerate every example term, this registers a single permissive context that maps any
 * otherwise-undefined term to a namespace via `@vocab`, so all illustrative claims survive
 * canonicalization and are signed. Declare it at issuance:
 *
 * ```kotlin
 * trustWeave.issue {
 *     additionalOption(ExampleContexts.OPTION_KEY, ExampleContexts.contexts)
 *     credential { /* ... */ }
 *     signedBy(issuerDid)
 * }
 * ```
 *
 * Production issuers should declare a real vocabulary context with explicit term definitions instead
 * of a catch-all `@vocab`.
 */
object ExampleContexts {

    /** Proof-option key under which the `@context` URL list is declared at issuance. */
    const val OPTION_KEY: String = JsonLdContexts.CONTEXTS_PROOF_OPTION

    /** URL of the registered example context (resolved offline; never fetched). */
    const val URL: String = "https://contexts.trustweave.example/examples/v1"

    private const val DOCUMENT: String = """{"@context":{"@vocab":"https://vocab.trustweave.example/examples#"}}"""

    init {
        JsonLdContexts.register(URL, DOCUMENT)
    }

    /** The `@context` list to pass at issuance; accessing it guarantees the context is registered. */
    val contexts: List<String> = listOf(URL)
}
