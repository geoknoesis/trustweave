package com.trustweave.credential.internal

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.proof.internal.engines.VcLdProofEngineProvider
import com.trustweave.credential.proof.internal.engines.SdJwtProofEngineProvider
import com.trustweave.credential.proof.internal.engines.AnonCredsProofEngineProvider
import com.trustweave.credential.spi.proof.ProofEngine

/**
 * Internal helper to create a map of all built-in proof engines.
 * 
 * Directly instantiates all built-in proof engines. All proof formats are
 * built-in and always available - no ServiceLoader discovery needed.
 */
internal fun createBuiltInEngines(): Map<ProofSuiteId, ProofEngine> {
    val engines = mutableMapOf<ProofSuiteId, ProofEngine>()
    
    // Directly create all built-in proof engines
    VcLdProofEngineProvider().create()?.let { engines[it.format] = it }
    SdJwtProofEngineProvider().create()?.let { engines[it.format] = it }
    AnonCredsProofEngineProvider().create()?.let { engines[it.format] = it }
    
    return engines
}

