package org.trustweave.did.registration.mapper

import org.trustweave.did.DidMethod
import org.trustweave.did.registration.impl.HttpDidMethod
import org.trustweave.did.registration.model.*

/**
 * Maps a DID Method Registry Entry (official format) to a Trustweave DidMethod implementation.
 *
 * This mapper extracts resolver configuration from the registry entry's implementations
 * and creates an appropriate DidMethod instance.
 *
 * **Mapping Strategy:**
 * 1. Extract resolver URL from `implementations[].driverUrl`
 * 2. Determine protocol adapter based on implementation name or URL
 * 3. Create HttpDidMethod with appropriate configuration
 * 4. Infer capabilities from available implementations
 */
object RegistryEntryMapper {

    /**
     * Maps a registry entry to a DidMethod implementation.
     *
     * @param entry The official registry entry
     * @return A DidMethod instance, or null if no suitable implementation can be created
     */
    fun mapToDidMethod(
        entry: DidMethodRegistryEntry
    ): DidMethod? {
        // Find the best implementation to use
        val implementation = selectBestImplementation(entry.implementations)
            ?: return null

        // Extract resolver configuration
        val driverUrl = implementation.driverUrl
            ?: return null

        // Determine protocol adapter
        val protocolAdapter = determineProtocolAdapter(implementation)

        // Infer capabilities from registry entry
        val capabilities = inferCapabilities(entry, implementation)

        // Create driver configuration
        val driverConfig = DriverConfig(
            type = "universal-resolver",
            baseUrl = driverUrl,
            protocolAdapter = protocolAdapter,
            timeout = 30
        )

        // Create registration spec (internal format)
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

    /**
     * Selects the best implementation from the list.
     *
     * Priority:
     * 1. Non-testnet implementations
     * 2. First implementation with a driverUrl
     */
    private fun selectBestImplementation(
        implementations: List<MethodImplementation>
    ): MethodImplementation? {
        // Prefer non-testnet implementations
        val nonTestNet = implementations.firstOrNull {
            it.driverUrl != null && it.testNet != true
        }
        if (nonTestNet != null) return nonTestNet

        // Fall back to any implementation with driverUrl
        return implementations.firstOrNull { it.driverUrl != null }
    }

    /**
     * Determines the protocol adapter based on implementation details.
     */
    private fun determineProtocolAdapter(
        implementation: MethodImplementation
    ): String {
        val name = implementation.name?.lowercase() ?: ""
        val url = implementation.driverUrl?.lowercase() ?: ""

        return when {
            name.contains("godiddy") || url.contains("godiddy") -> "godiddy"
            else -> "standard"
        }
    }

    /**
     * Infers method capabilities from the registry entry.
     *
     * Capabilities are inferred from:
     * - Resolution: Available if driverUrl is present
     * - Create/Update/Deactivate: Available if registrarUrl is present
     */
    private fun inferCapabilities(
        entry: DidMethodRegistryEntry,
        implementation: MethodImplementation
    ): MethodCapabilities {
        // If there's a driverUrl, we can resolve
        val canResolve = implementation.driverUrl != null

        // If there's a registrarUrl, we can create/update/deactivate
        val canCreate = implementation.registrarUrl != null
        val canUpdate = implementation.registrarUrl != null
        val canDeactivate = implementation.registrarUrl != null

        return MethodCapabilities(
            create = canCreate,
            resolve = canResolve,
            update = canUpdate,
            deactivate = canDeactivate
        )
    }
}

