package org.trustweave.did.registry

/**
 * Describes a non-fatal failure while scanning or instantiating [DidMethodProvider] SPI entries.
 *
 * SPI discovery itself continues where possible; see [DidMethodRegistryAutoRegisterResult].
 */
data class DidMethodAutoRegisterFailure(
    val phase: String,
    val message: String,
    val cause: Throwable? = null,
)

/**
 * Outcome of [DidMethodRegistry.autoRegister]: a (possibly partial) registry plus structured failures.
 *
 * Always inspect [failures] in production builds when methods are missing unexpectedly.
 */
data class DidMethodRegistryAutoRegisterResult(
    val registry: DidMethodRegistry,
    val failures: List<DidMethodAutoRegisterFailure>,
) {
    val hasFailures: Boolean get() = failures.isNotEmpty()
}
