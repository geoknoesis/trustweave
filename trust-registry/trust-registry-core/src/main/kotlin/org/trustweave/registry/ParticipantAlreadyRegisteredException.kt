package org.trustweave.registry

/**
 * Thrown by [TrustRegistry.registerIssuer] / [TrustRegistry.registerVerifier] when the DID is
 * already registered in that role.
 *
 * Registration never overwrites an existing record: re-registering a revoked participant must
 * not silently restore it to [AccreditationStatus.ACTIVE]. Use the update / activate operations
 * to change an existing record deliberately.
 */
class ParticipantAlreadyRegisteredException(
    val did: String,
    val role: String,
) : IllegalStateException("$role already registered: $did")
