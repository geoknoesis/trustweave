package org.trustweave.credential.jsonld

import org.trustweave.credential.internal.JsonLdContextLoader

/**
 * Public registry for additional JSON-LD `@context` documents used during credential
 * canonicalization (signing and verification).
 *
 * TrustWeave resolves JSON-LD contexts **offline**: the core W3C credential and
 * security-suite contexts are bundled, and remote context fetching is disabled by default
 * for determinism and SSRF safety. Credentials whose `credentialSubject` claims use terms
 * that are not defined by any declared `@context` are rejected fail-closed at issuance and
 * verification (undefined terms would be silently dropped from the canonical form and not
 * covered by the proof signature).
 *
 * Issuers using their own claim vocabularies must therefore:
 * 1. Register the vocabulary context document here (process-wide, once), and
 * 2. Declare its URL in the credential's `@context` — for the issuance DSL, pass the
 *    URL list via the [CONTEXTS_PROOF_OPTION] proof option (e.g.
 *    `additionalOption("contexts", listOf(url))` on the issuance builder).
 */
object JsonLdContexts {

    /**
     * Key under `ProofOptions.additionalOptions` carrying the list of additional
     * `@context` URLs (`List<String>`) a credential declares at issuance.
     */
    const val CONTEXTS_PROOF_OPTION = "contexts"

    /**
     * Register a JSON-LD context document so it can be resolved offline during
     * canonicalization. Registration is process-wide.
     *
     * @param url The context URL exactly as it appears in `@context`
     * @param contextJson The full JSON context document as a string
     */
    fun register(url: String, contextJson: String) {
        JsonLdContextLoader.registerContext(url, contextJson)
    }
}
