package org.trustweave.did.threebox

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
 * **STUB — NOT IMPLEMENTED.** Skeleton for the did:3 method (3Box/Identity).
 *
 * No operation works: [createDid] throws and [resolveDid] returns a not-implemented
 * resolution failure. A real implementation would require IPFS integration and
 * 3Box/Identity protocol support, neither of which exists here.
 *
 * This class is intentionally NOT registered for ServiceLoader discovery (no
 * `META-INF/services` entry), so it never silently masquerades as a working DID method.
 * It can only be instantiated explicitly.
 *
 * did:3 (when implemented) uses IPFS for decentralized storage of DID documents:
 * - Format: `did:3:{ipfs-hash}`
 * - Documents stored on IPFS
 * - No blockchain required
 */
class ThreeBoxDidMethod(
    kms: KeyManagementService
) : AbstractDidMethod("3", kms) {

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        throw TrustWeaveException.Unknown(
            code = "THREEBOX_NOT_IMPLEMENTED",
            message = "did:3 is a stub and is not implemented: DID creation would require " +
                "IPFS and 3Box/Identity protocol integration, which does not exist."
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
            "did:3 is a stub and is not implemented: resolution would require IPFS " +
                "integration, which does not exist.",
            method,
            did.value
        )
    }
}

