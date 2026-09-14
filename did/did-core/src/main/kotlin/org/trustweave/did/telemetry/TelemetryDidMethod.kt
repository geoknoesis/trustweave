package org.trustweave.did.telemetry

import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument

/**
 * Reports the DID write lifecycle to [Telemetry], and delegates everything else unchanged.
 *
 * `CachingDidResolver` already reports resolution, which is the read half. Creation, update and
 * deactivation are the half that changes something — the operations a host most needs to see when
 * a registrar is slow, a chain is congested or a key rotation did not take. Instrumenting them
 * here rather than in each method plugin means a new plugin is covered the day it is registered.
 *
 * Kotlin interface delegation supplies every other member, so a method added to [DidMethod] keeps
 * working here rather than silently losing its implementation.
 *
 * Nothing is recorded until a host installs a sink. The DID method name is attached because it is
 * low-cardinality and the first thing worth grouping by; the DID itself never is, because it
 * identifies a subject and these become metric labels.
 */
public class TelemetryDidMethod(
    /**
     * The method this wraps.
     *
     * Public because `DidMethodRegistry.register` instruments on the way in, so a caller holding a
     * registry entry needs a way back to the instance it registered — to compare identity, or to
     * reach a member the [DidMethod] interface does not expose.
     */
    public val delegate: DidMethod,
) : DidMethod by delegate {
    private val attributes = mapOf("did.method" to delegate.method)

    override suspend fun createDid(options: DidCreationOptions): DidDocument =
        Telemetry.measure(Operation.DID_CREATE, attributes) { delegate.createDid(options) }

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument = Telemetry.measure(Operation.DID_UPDATE, attributes) { delegate.updateDid(did, updater) }

    override suspend fun deactivateDid(did: Did): Boolean =
        Telemetry.measure(Operation.DID_DEACTIVATE, attributes) {
            delegate.deactivateDid(did).also { deactivated ->
                // A method that answers "no" has not failed; it has declined. Recording it as a
                // success would hide the one outcome an operator needs to notice.
                if (!deactivated) Telemetry.rejected(Operation.DID_DEACTIVATE, "NotDeactivated", attributes)
            }
        }
}

/** Wraps this method so its creations, updates and deactivations are reported to [Telemetry]. */
public fun DidMethod.withTelemetry(): DidMethod = if (this is TelemetryDidMethod) this else TelemetryDidMethod(this)
