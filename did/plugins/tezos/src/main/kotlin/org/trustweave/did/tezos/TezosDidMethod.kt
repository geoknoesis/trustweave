package org.trustweave.did.tezos

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * **STUB — NOT IMPLEMENTED.** Skeleton for the did:tz method (Tezos).
 *
 * No operation works: [createDid] throws and [resolveDid] returns a not-implemented
 * resolution failure. A real implementation would require Tezos SDK and smart contract
 * integration, neither of which exists here.
 *
 * This class is intentionally NOT registered for ServiceLoader discovery (no
 * `META-INF/services` entry), so it never silently masquerades as a working DID method.
 * It can only be instantiated explicitly.
 *
 * did:tz (when implemented) uses the Tezos blockchain to anchor DID documents:
 * - Format: `did:tz:{network}:{address}`
 * - Documents stored on Tezos blockchain via smart contracts
 */
class TezosDidMethod(
    kms: KeyManagementService
) : AbstractDidMethod("tz", kms) {

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        throw TrustWeaveException.Unknown(
            code = "TEZOS_NOT_IMPLEMENTED",
            message = "did:tz is a stub and is not implemented: DID creation would require " +
                "Tezos SDK and smart contract integration, which does not exist."
        )
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
        } catch (e: Exception) {
            return@withContext DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method,
                did.value
            )
        }

        // Honest not-implemented failure (internal-error class), never "invalidDid":
        // the DID may be perfectly valid — this stub simply cannot resolve anything.
        DidMethodUtils.createErrorResolutionResult(
            "notImplemented",
            "did:tz is a stub and is not implemented: resolution would require Tezos SDK " +
                "integration, which does not exist.",
            method,
            did.value
        )
    }
}

