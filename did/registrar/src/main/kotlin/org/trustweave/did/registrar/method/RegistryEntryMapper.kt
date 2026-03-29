package org.trustweave.did.registrar.method

import org.trustweave.did.DidMethod
import org.trustweave.did.model.MethodCapabilities
import org.trustweave.did.registration.model.*

/**
 * Maps a DID Method Registry Entry to a [HttpDidMethod] implementation.
 */
object RegistryEntryMapper {

    fun mapToDidMethod(entry: DidMethodRegistryEntry): DidMethod? {
        val implementation = selectBestImplementation(entry.implementations) ?: return null
        val driverUrl = implementation.driverUrl ?: return null
        val protocolAdapter = determineProtocolAdapter(implementation)
        val capabilities = inferCapabilities(entry, implementation)
        val driverConfig = DriverConfig(
            type = "universal-resolver",
            baseUrl = driverUrl,
            registrarUrl = implementation.registrarUrl,
            protocolAdapter = protocolAdapter,
            timeout = 30
        )
        val spec = DidRegistrationSpec(
            name = entry.name,
            status = entry.status,
            specification = entry.specification,
            contact = entry.contact,
            driver = driverConfig,
            capabilities = capabilities
        )
        return HttpDidMethod(spec)
    }

    private fun selectBestImplementation(implementations: List<MethodImplementation>): MethodImplementation? {
        return implementations.firstOrNull { it.driverUrl != null && it.testNet != true }
            ?: implementations.firstOrNull { it.driverUrl != null }
    }

    private fun determineProtocolAdapter(implementation: MethodImplementation): String {
        val name = implementation.name?.lowercase() ?: ""
        val url = implementation.driverUrl?.lowercase() ?: ""
        return when {
            name.contains("godiddy") || url.contains("godiddy") -> "godiddy"
            else -> "standard"
        }
    }

    private fun inferCapabilities(entry: DidMethodRegistryEntry, implementation: MethodImplementation): MethodCapabilities {
        val canResolve = implementation.driverUrl != null
        val canCreate = implementation.registrarUrl != null
        return MethodCapabilities(create = canCreate, resolve = canResolve, update = canCreate, deactivate = canCreate)
    }
}
