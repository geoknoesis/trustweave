package org.trustweave.did.registrar.method

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.exception.DidException
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.client.DefaultUniversalRegistrar
import org.trustweave.did.registrar.model.*
import org.trustweave.did.registration.model.DidRegistrationSpec
import org.trustweave.did.resolver.DefaultUniversalResolver
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.StandardUniversalResolverAdapter
import org.trustweave.did.resolver.UniversalResolver
import org.trustweave.did.resolver.UniversalResolverProtocolAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generic DID method that uses an HTTP endpoint for resolution/registration.
 *
 * Delegates resolution to a [UniversalResolver] (via [DefaultUniversalResolver]) and
 * create/update/deactivate to an injected [DidRegistrar] (or auto-constructed
 * [DefaultUniversalRegistrar] from `driver.registrarUrl`).
 *
 * Protocol adapters for additional resolver types (e.g., "godiddy") should be passed via
 * [additionalAdapters]. This eliminates the `Class.forName` reflection anti-pattern.
 *
 * **Example:**
 * ```kotlin
 * val method = HttpDidMethod(
 *     registrationSpec = spec,
 *     additionalAdapters = mapOf("godiddy" to GodiddyProtocolAdapter())
 * )
 * ```
 */
class HttpDidMethod(
    val registrationSpec: DidRegistrationSpec,
    private val registrar: DidRegistrar? = null,
    /** Named protocol adapters. "standard" is always available. */
    private val additionalAdapters: Map<String, UniversalResolverProtocolAdapter> = emptyMap()
) : DidMethod {

    override val method: String = registrationSpec.name

    override val capabilities get() = registrationSpec.capabilities
        ?.let { if (it.method.isEmpty()) it.copy(method = method) else it }

    private val resolver: UniversalResolver = createResolver()

    private val actualRegistrar: DidRegistrar? = registrar ?: createRegistrar()

    private val stateMachine: RegistrationStateMachine by lazy {
        RegistrationStateMachine(actualRegistrar)
    }

    // ─── Resolver / registrar wiring ────────────────────────────────────────────

    private fun createResolver(): UniversalResolver {
        val driver = registrationSpec.driver
            ?: throw TrustWeaveException.InvalidState(message = "Driver configuration is required for HTTP-based DID method")
        require(driver.type == "universal-resolver") { "Driver type must be 'universal-resolver' for HttpDidMethod" }
        val baseUrl = driver.baseUrl
            ?: throw TrustWeaveException.InvalidState(message = "baseUrl is required for Universal Resolver driver")
        return DefaultUniversalResolver(
            baseUrl = baseUrl,
            timeout = driver.timeout ?: 30,
            apiKey = driver.apiKey,
            protocolAdapter = createProtocolAdapter(driver.protocolAdapter ?: "standard")
        )
    }

    /**
     * Creates a [DefaultUniversalRegistrar] from `driver.registrarUrl` if present.
     * No reflection required — direct instantiation.
     */
    private fun createRegistrar(): DidRegistrar? {
        val driver = registrationSpec.driver ?: return null
        val registrarUrl = driver.registrarUrl ?: return null
        return DefaultUniversalRegistrar(
            baseUrl = registrarUrl,
            timeout = driver.timeout ?: 60,
            apiKey = driver.apiKey
        )
    }

    private fun createProtocolAdapter(adapterName: String): UniversalResolverProtocolAdapter {
        return when (adapterName.lowercase()) {
            "standard" -> StandardUniversalResolverAdapter()
            else -> additionalAdapters[adapterName.lowercase()]
                ?: throw TrustWeaveException.InvalidOperation(
                    message = "Unknown protocol adapter '$adapterName'. " +
                            "Pass it via the additionalAdapters constructor parameter. " +
                            "Registered adapters: ${additionalAdapters.keys + "standard"}",
                    context = mapOf("adapterName" to adapterName)
                )
        }
    }

    // ─── DidMethod interface ─────────────────────────────────────────────────────

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        val capabilities = registrationSpec.capabilities
        if (capabilities?.create != true) throw DidException.DidCreationFailed(
            did = null, reason = "DID method '${registrationSpec.name}' does not support creation."
        )
        val reg = actualRegistrar ?: throw DidException.DidCreationFailed(
            did = null, reason = "DID creation requires a DidRegistrar. Specify registrarUrl in driver config."
        )
        val specOptions = CreateDidOptions(
            keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
            methodSpecificOptions = buildMap {
                put("algorithm", JsonPrimitive(options.algorithm.algorithmName))
                put("purposes", JsonArray(options.purposes.map { JsonPrimitive(it.purposeName) }))
                options.additionalProperties.forEach { (k, v) -> put(k, JsonPrimitive(v.toString())) }
            }
        )
        val response = reg.createDid(registrationSpec.name, specOptions)
        val final = stateMachine.advance(response, "create", response.didState.did?.let { Did(it) })
        final.didState.didDocument ?: throw DidException.DidCreationFailed(
            did = final.didState.did?.let { Did(it) },
            reason = "DID creation completed but no DID Document returned. State: ${final.didState.state}"
        )
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        val capabilities = registrationSpec.capabilities
        if (capabilities?.resolve != true) throw DidException.DidResolutionFailed(
            did = did, reason = "DID method '${registrationSpec.name}' does not support resolution."
        )
        require(did.method == registrationSpec.name) {
            "Invalid DID format for method '${registrationSpec.name}': ${did.value}"
        }
        try {
            resolver.resolveDid(did.value)
        } catch (e: DidException) {
            throw e
        } catch (e: Exception) {
            throw DidException.DidResolutionFailed(
                did = did, reason = "Failed to resolve DID: ${e.message}", cause = e
            )
        }
    }

    override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument =
        withContext(Dispatchers.IO) {
            val capabilities = registrationSpec.capabilities
            if (capabilities?.update != true) throw DidException.DidUpdateFailed(
                did = did, reason = "DID method '${registrationSpec.name}' does not support updates."
            )
            val reg = actualRegistrar ?: throw DidException.DidUpdateFailed(
                did = did, reason = "DID updates require a DidRegistrar. Specify registrarUrl in driver config."
            )
            val current = when (val r = resolver.resolveDid(did.value)) {
                is DidResolutionResult.Success -> r.document
                else -> throw DidException.DidNotFound(did = did)
            }
            val updated = updater(current)
            val response = reg.updateDid(did.value, updated)
            val final = stateMachine.advance(response, "update", did)
            final.didState.didDocument ?: throw DidException.DidUpdateFailed(
                did = did, reason = "DID update completed but no DID Document returned. State: ${final.didState.state}"
            )
        }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        val capabilities = registrationSpec.capabilities
        if (capabilities?.deactivate != true) throw DidException.DidDeactivationFailed(
            did = did, reason = "DID method '${registrationSpec.name}' does not support deactivation."
        )
        val reg = actualRegistrar ?: throw DidException.DidDeactivationFailed(
            did = did, reason = "DID deactivation requires a DidRegistrar. Specify registrarUrl in driver config."
        )
        val response = reg.deactivateDid(did.value)
        val final = stateMachine.advance(response, "deactivate", did)
        final.didState.state == OperationState.FINISHED
    }

}
