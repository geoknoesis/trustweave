package org.trustweave.credential.model.vc

import org.trustweave.core.identifiers.Iri
import kotlinx.serialization.Serializable

/**
 * VC Refresh Service - service for refreshing expiring credentials.
 * 
 * Per W3C VC Data Model, used to specify how a credential holder can refresh
 * an expiring credential.
 * 
 * **Examples:**
 * ```kotlin
 * val refreshService = RefreshService(
 *     id = Iri("https://example.edu/refresh/3732"),
 *     type = "ManualRefreshService2020"
 * )
 * ```
 */
@Serializable
data class RefreshService(
    val id: Iri,
    val type: String  // e.g., "ManualRefreshService2020", "AutomaticRefreshService2020"
)

