package org.trustweave.did.model

import kotlinx.serialization.Serializable

/**
 * Capabilities supported by a DID method implementation.
 *
 * Canonical, single definition merging the previously duplicated
 * [org.trustweave.did.discovery.MethodCapabilities] and
 * [org.trustweave.did.registration.model.MethodCapabilities].
 */
@Serializable
data class MethodCapabilities(
    /**
     * The DID method name (e.g. "key", "web", "ion").
     */
    val method: String = "",

    /**
     * Whether this implementation supports creating new DIDs.
     */
    val create: Boolean = false,

    /**
     * Whether this implementation supports resolving DIDs.
     */
    val resolve: Boolean = true,

    /**
     * Whether this implementation supports updating DID documents.
     */
    val update: Boolean = false,

    /**
     * Whether this implementation supports deactivating DIDs.
     */
    val deactivate: Boolean = false,

    /**
     * Supported key types (e.g. "Ed25519", "P-256").
     */
    val keyTypes: List<String> = emptyList(),

    /**
     * Additional feature identifiers supported by this method.
     */
    val features: List<String> = emptyList()
)
