package org.trustweave.credential.spi.proof

/**
 * Proof engine capabilities.
 *
 * Describes what capabilities a proof engine supports.
 *
 * Lives in commonMain (separate file from [ProofEngine]) so that
 * [org.trustweave.credential.CredentialService] can reference it in its multiplatform
 * interface. The [ProofEngine] interface itself, plus [ProofEngineConfig], stay in
 * `:credentials:credential-api` (JVM) because [ProofEngineConfig.didResolver] is
 * `org.trustweave.did.resolver.DidResolver` — not yet portable.
 */
data class ProofEngineCapabilities(
    val selectiveDisclosure: Boolean = false,
    val zeroKnowledge: Boolean = false,
    val revocation: Boolean = true,
    val presentation: Boolean = true,
    val predicates: Boolean = false
)
