package org.trustweave.godiddy

import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.UniversalResolver
import org.trustweave.godiddy.issuer.GodiddyIssuer
import org.trustweave.godiddy.registrar.GodiddyRegistrar
import org.trustweave.godiddy.resolver.GodiddyResolver
import org.trustweave.godiddy.verifier.GodiddyVerifier

/**
 * Result of godiddy integration setup.
 */
data class GodiddyIntegrationResult(
    /**
     * Registry that received the registered DID methods.
     */
    val registry: DidMethodRegistry,

    /**
     * List of DID methods that were registered.
     */
    val registeredDidMethods: List<String>,

    /**
     * Universal Resolver client instance.
     * Exposed as [UniversalResolver] interface for better abstraction.
     */
    val resolver: UniversalResolver? = null,

    /**
     * Universal Registrar client instance.
     */
    val registrar: GodiddyRegistrar? = null,

    /**
     * Universal Issuer client instance.
     */
    val issuer: GodiddyIssuer? = null,

    /**
     * Universal Verifier client instance.
     */
    val verifier: GodiddyVerifier? = null
)

