package io.geoknoesis.vericore.godiddy

import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.godiddy.issuer.GodiddyIssuer
import io.geoknoesis.vericore.godiddy.registrar.GodiddyRegistrar
import io.geoknoesis.vericore.godiddy.resolver.GodiddyResolver
import io.geoknoesis.vericore.godiddy.verifier.GodiddyVerifier

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
     */
    val resolver: GodiddyResolver? = null,
    
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

