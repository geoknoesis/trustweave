package com.trustweave.did.registration.impl

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidMethod
import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument
import com.trustweave.did.registrar.DidRegistrar
import com.trustweave.did.registrar.model.*
import com.trustweave.did.registration.model.DidRegistrationSpec
import com.trustweave.did.resolver.DefaultUniversalResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.resolver.UniversalResolver
import com.trustweave.did.resolver.UniversalResolverProtocolAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generic DID method implementation that uses an HTTP endpoint for resolution
 * and optionally a DID Registrar for create/update/deactivate operations.
 *
 * This implementation allows DID methods to be registered via JSON configuration
 * without writing custom code. It delegates resolution to an HTTP endpoint, which
 * can be a Universal Resolver instance, a custom resolver service, or any HTTP
 * endpoint that follows the Universal Resolver protocol.
 *
 * For create/update/deactivate operations, if a [DidRegistrar] is provided, it will
 * be used to perform these operations. Otherwise, these operations will throw exceptions
 * unless the method's capabilities indicate they are not supported.
 *
 * **Example Usage:**
 * ```kotlin
 * val spec = DidRegistrationSpec(
 *     name = "example",
 *     driver = DriverConfig(
 *         type = "universal-resolver",
 *         baseUrl = "https://dev.uniresolver.io",
 *         protocolAdapter = "standard"
 *     ),
 *     capabilities = MethodCapabilities(resolve = true, create = true)
 * )
 * val registrar: DidRegistrar = // ... obtain registrar
 * val method = HttpDidMethod(spec, registrar)
 * ```
 */
class HttpDidMethod(
    val registrationSpec: DidRegistrationSpec,
    private val registrar: DidRegistrar? = null
) : DidMethod {

    override val method: String = registrationSpec.name

    private val resolver: UniversalResolver = createResolver()

    private fun createResolver(): UniversalResolver {
        val driver = registrationSpec.driver
            ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "Driver configuration is required for HTTP-based DID method"
            )

        require(driver.type == "universal-resolver") {
            "Driver type must be 'universal-resolver' for HttpDidMethod"
        }

        val baseUrl = driver.baseUrl
            ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "baseUrl is required for Universal Resolver driver"
            )

        val protocolAdapter = createProtocolAdapter(driver.protocolAdapter ?: "standard")
        val timeout = driver.timeout ?: 30
        val apiKey = driver.apiKey

        return DefaultUniversalResolver(
            baseUrl = baseUrl,
            timeout = timeout,
            apiKey = apiKey,
            protocolAdapter = protocolAdapter
        )
    }

    private fun createProtocolAdapter(adapterName: String): UniversalResolverProtocolAdapter {
        return when (adapterName.lowercase()) {
            "standard" -> {
                // Use reflection or direct import
                com.trustweave.did.resolver.StandardUniversalResolverAdapter()
            }
            "godiddy" -> {
                // Try to load GodiddyProtocolAdapter if available
                try {
                    val clazz = Class.forName("com.trustweave.godiddy.resolver.GodiddyProtocolAdapter")
                    clazz.getDeclaredConstructor().newInstance() as UniversalResolverProtocolAdapter
                } catch (e: ClassNotFoundException) {
                    throw com.trustweave.core.exception.TrustWeaveException.PluginNotFound(
                        pluginId = "godiddy-protocol-adapter",
                        pluginType = "protocol-adapter"
                    )
                }
            }
            else -> {
                throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                    message = "Unknown protocol adapter: $adapterName. Supported: 'standard', 'godiddy'",
                    context = mapOf("adapterName" to adapterName, "supportedAdapters" to listOf("standard", "godiddy"))
                )
            }
        }
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        val capabilities = registrationSpec.capabilities
        if (capabilities?.create != true) {
            throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                message = "DID method '${registrationSpec.name}' does not support creation. This method only supports resolution via HTTP endpoint.",
                context = mapOf("method" to registrationSpec.name, "operation" to "create")
            )
        }

        // Use registrar if available
        val registrarToUse = registrar
            ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "DID creation requires a DidRegistrar. Provide a registrar when creating HttpDidMethod or use a native implementation.",
                context = mapOf("method" to registrationSpec.name, "operation" to "create")
            )

        // Convert legacy options to spec-compliant options
        val specOptions = CreateDidOptions(
            keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
            methodSpecificOptions = buildMap {
                put("algorithm", JsonPrimitive(options.algorithm.algorithmName))
                put("purposes", JsonArray(options.purposes.map {
                    JsonPrimitive(it.purposeName)
                }))
                options.additionalProperties.forEach { (key, value) ->
                    put(key, when (value) {
                        is String -> JsonPrimitive(value)
                        is Number -> JsonPrimitive(value.toDouble())
                        is Boolean -> JsonPrimitive(value)
                        else -> JsonPrimitive(value.toString())
                    })
                }
            }
        )

        // Call registrar and extract DID Document from response
        val response = registrarToUse.createDid(registrationSpec.name, specOptions)

        // Handle long-running operations
        val finalResponse = if (response.isComplete()) {
            response
        } else if (response.jobId != null) {
            // For now, throw an error for long-running operations
            // In the future, we could add polling support
            throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                message = "DID creation is a long-running operation (jobId: ${response.jobId}). Use the registrar directly to poll for completion.",
                context = mapOf("jobId" to (response.jobId ?: ""), "operation" to "create")
            )
        } else {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "DID creation failed: ${response.didState.state}",
                context = mapOf("operation" to "create", "state" to response.didState.state.toString())
            )
        }

        // Extract DID Document from response
        finalResponse.didState.didDocument
            ?: throw com.trustweave.did.exception.DidException.InvalidDidFormat(
                did = "unknown",
                reason = "DID creation completed but no DID Document returned. State: ${finalResponse.didState.state}"
            )
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        val capabilities = registrationSpec.capabilities
        if (capabilities?.resolve != true) {
            throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                message = "DID method '${registrationSpec.name}' does not support resolution.",
                context = mapOf("method" to registrationSpec.name, "operation" to "resolve")
            )
        }

        // Validate DID format
        require(did.method == registrationSpec.name) {
            "Invalid DID format for method '${registrationSpec.name}': ${did.value}"
        }

        // Delegate to HTTP resolver endpoint
        try {
            resolver.resolveDid(did.value)
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve DID $did using HTTP endpoint: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to did, "method" to registrationSpec.name),
                cause = e
            )
        }
    }

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        val capabilities = registrationSpec.capabilities
        if (capabilities?.update != true) {
            throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                message = "DID method '${registrationSpec.name}' does not support updates.",
                context = mapOf("method" to registrationSpec.name, "operation" to "update")
            )
        }

        // Use registrar if available
        val registrarToUse = registrar
            ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "DID updates require a DidRegistrar. Provide a registrar when creating HttpDidMethod or use a native implementation.",
                context = mapOf("method" to registrationSpec.name, "operation" to "update")
            )

        // First resolve the current document
        val resolutionResult = resolver.resolveDid(did.value)
        val currentDocument = when (resolutionResult) {
            is DidResolutionResult.Success -> resolutionResult.document
            else -> throw com.trustweave.did.exception.DidException.DidNotFound(
                did = did,
                availableMethods = emptyList()
            )
        }

        // Apply the updater function
        val updatedDocument = updater(currentDocument)

        // Update via registrar (registrar still uses String for backward compatibility)
        val response = registrarToUse.updateDid(did.value, updatedDocument)

        // Handle long-running operations
        val finalResponse = if (response.isComplete()) {
            response
        } else if (response.jobId != null) {
            throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                message = "DID update is a long-running operation (jobId: ${response.jobId}). Use the registrar directly to poll for completion.",
                context = mapOf("jobId" to (response.jobId ?: ""), "operation" to "update")
            )
        } else {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "DID update failed: ${response.didState.state}",
                context = mapOf("operation" to "update", "state" to response.didState.state.toString())
            )
        }

        // Extract updated DID Document from response
        finalResponse.didState.didDocument
            ?: throw com.trustweave.did.exception.DidException.InvalidDidFormat(
                did = did.value,
                reason = "DID update completed but no DID Document returned. State: ${finalResponse.didState.state}"
            )
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        val capabilities = registrationSpec.capabilities
        if (capabilities?.deactivate != true) {
            throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                message = "DID method '${registrationSpec.name}' does not support deactivation.",
                context = mapOf("method" to registrationSpec.name, "operation" to "deactivate")
            )
        }

        // Use registrar if available
        val registrarToUse = registrar
            ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "DID deactivation requires a DidRegistrar. Provide a registrar when creating HttpDidMethod or use a native implementation.",
                context = mapOf("method" to registrationSpec.name, "operation" to "deactivate")
            )

        // Deactivate via registrar (registrar still uses String for backward compatibility)
        val response = registrarToUse.deactivateDid(did.value)

        // Handle long-running operations
        val finalResponse = if (response.isComplete()) {
            response
        } else if (response.jobId != null) {
            throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                message = "DID deactivation is a long-running operation (jobId: ${response.jobId}). Use the registrar directly to poll for completion.",
                context = mapOf("jobId" to (response.jobId ?: ""), "operation" to "deactivate")
            )
        } else {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "DID deactivation failed: ${response.didState.state}",
                context = mapOf("operation" to "deactivate", "state" to response.didState.state.toString())
            )
        }

        // Return true if deactivation succeeded
        finalResponse.didState.state == OperationState.FINISHED
    }
}

