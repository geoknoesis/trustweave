package org.trustweave.trust.context

import org.trustweave.did.DidMethod
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolver
import org.trustweave.trust.dsl.TrustWeaveConfig

/**
 * Narrow context for DID DSL builders and [org.trustweave.trust.services.DidManagementService],
 * so they do not depend on the full [org.trustweave.trust.TrustWeave] facade.
 */
interface DidDslContext {
    val configuration: TrustWeaveConfig

    fun getDidMethod(name: String): DidMethod?

    fun getDidRegistry(): DidMethodRegistry

    fun getDidResolver(): DidResolver
}
