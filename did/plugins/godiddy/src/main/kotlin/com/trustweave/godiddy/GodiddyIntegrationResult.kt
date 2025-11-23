package com.trustweave.godiddy

import com.trustweave.did.DidMethodRegistry
import com.trustweave.godiddy.issuer.GodiddyIssuer
import com.trustweave.godiddy.registrar.GodiddyRegistrar
import com.trustweave.godiddy.resolver.GodiddyResolver
import com.trustweave.godiddy.verifier.GodiddyVerifier

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

