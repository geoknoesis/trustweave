package com.trustweave.credential.internal

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.proof.internal.engines.VcLdProofEngine
import com.trustweave.credential.proof.internal.engines.SdJwtProofEngine
import com.trustweave.credential.spi.proof.ProofEngine

/**
 * Internal helper to create a map of all built-in proof engines.
 * 
 * Directly instantiates all built-in proof engines. All proof formats are
 * built-in and always available - no ServiceLoader discovery needed.
 * 
 * @param didResolver Optional DID resolver for verification operations
 */
internal fun createBuiltInEngines(didResolver: com.trustweave.did.resolver.DidResolver? = null): Map<ProofSuiteId, ProofEngine> {
    val engines = mutableMapOf<ProofSuiteId, ProofEngine>()
    
    // Create config with DID resolver if provided
    val config = if (didResolver != null) {
        com.trustweave.credential.spi.proof.ProofEngineConfig(didResolver = didResolver)
    } else {
        com.trustweave.credential.spi.proof.ProofEngineConfig()
    }
    
    // Directly create all built-in proof engines with config
    val vcLdEngine = VcLdProofEngine(config)
    val sdJwtEngine = SdJwtProofEngine(config)
    
    engines[vcLdEngine.format] = vcLdEngine
    engines[sdJwtEngine.format] = sdJwtEngine
    
    return engines
}

