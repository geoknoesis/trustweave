package org.trustweave.registry

interface TrustRegistryProvider {
    val name: String
    fun create(config: Map<String, Any?> = emptyMap()): TrustRegistry
}
