package org.trustweave.trust

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.trustweave.revocation.services.StatusListRegistryFactory
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.*
import org.trustweave.trust.services.TrustRegistryFactory
import org.trustweave.wallet.services.WalletFactory

/**
 * Quick start: in-memory TrustWeave with did:key (alias for [inMemory]).
 *
 * Same as [inMemory] — use whichever name reads better in your context (`quickStart` for tutorials,
 * `inMemory` when emphasizing the KMS/storage model).
 *
 * Ideal for prototypes, tests, and getting started.
 *
 * **Example:**
 * ```kotlin
 * val trustWeave = runBlocking { TrustWeave.quickStart() }
 * val issuerDid = trustWeave.createDid().getOrThrowDid()
 * val credential = trustWeave.issue {
 *     credential { type("Person"); issuer(issuerDid); subject { id("did:key:holder"); "name" to "Alice" } }
 *     signedBy(issuerDid)
 * }.getOrThrow()
 * ```
 */
suspend fun TrustWeave.Companion.quickStart(): TrustWeave = inMemory()

/**
 * In-memory TrustWeave (KMS, did:key, optional in-memory trust registry and anchor).
 *
 * Prefer [quickStart] for documentation-facing examples; both resolve to the same configuration.
 *
 * When [trustRegistryFactory] is null (the default), the built-in in-memory trust registry
 * backs the `trust { provider("inMemory") }` configuration, so a bare `quickStart()` works
 * without any factories.
 */
suspend fun TrustWeave.Companion.inMemory(
    chainId: String? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    statusListRegistryFactory: StatusListRegistryFactory? = null,
    trustRegistryFactory: TrustRegistryFactory? = null,
    walletFactory: WalletFactory? = null
): TrustWeave {
    return build {
        dispatcher(dispatcher)
        factories(
            statusListRegistryFactory = statusListRegistryFactory,
            trustRegistryFactory = trustRegistryFactory,
            walletFactory = walletFactory
        )
        keys {
            provider("inMemory")
            algorithm("Ed25519")
        }
        did {
            method("key") {
                algorithm("Ed25519")
            }
        }
        chainId?.let {
            anchor {
                chain(it) { inMemory() }
            }
        }
        trust {
            provider("inMemory")
        }
    }
}

/**
 * Wrap an existing [TrustWeaveConfig] in the facade.
 */
fun TrustWeave.Companion.from(config: TrustWeaveConfig): TrustWeave {
    return TrustWeave(config)
}
