package org.trustweave.trust.dsl

import org.trustweave.credential.jsonld.JsonLdContexts
import org.trustweave.trust.dsl.credential.IssuanceBuilder

/**
 * Test JSON-LD claim vocabulary for trust-module tests.
 *
 * VC-LD issuance fails closed when `credentialSubject` claim terms are not defined by the
 * credential's `@context` (undefined terms are silently dropped by JSON-LD
 * canonicalization and would not be covered by the proof signature). Tests that issue
 * credentials with ad-hoc claims (e.g. `"test" to "value"`) must declare a context that
 * defines those terms.
 *
 * This helper registers a catch-all `@vocab` context (process-wide, idempotent) and lets
 * tests declare it on issued credentials via [withTestClaimContexts].
 */
const val TEST_CLAIMS_CONTEXT_URL = "https://trustweave.example/contexts/test-claims/v1"

private val testClaimsContextRegistered: Boolean = run {
    JsonLdContexts.register(
        TEST_CLAIMS_CONTEXT_URL,
        """
        {
          "@context": {
            "@vocab": "https://trustweave.example/vocab/test-claims#"
          }
        }
        """.trimIndent()
    )
    true
}

/**
 * Declare the test claim vocabulary on the issued credential's `@context`, so ad-hoc test
 * claims are defined JSON-LD terms and are covered by the proof signature.
 */
fun IssuanceBuilder.withTestClaimContexts() {
    check(testClaimsContextRegistered) { "Test claim context registration failed" }
    additionalOption(JsonLdContexts.CONTEXTS_PROOF_OPTION, listOf(TEST_CLAIMS_CONTEXT_URL))
}
