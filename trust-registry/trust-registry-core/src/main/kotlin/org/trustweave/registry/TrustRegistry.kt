package org.trustweave.registry

/**
 * Registry of accredited issuers and verifiers.
 *
 * Contract shared by all implementations:
 * - `register*` throws [ParticipantAlreadyRegisteredException] for a DID already registered in
 *   that role and never overwrites (or re-activates) the existing record;
 * - `update*` throws [NoSuchElementException] for an unknown DID;
 * - `revoke*` records [AccreditationStatus.REVOKED] and the optional reason (exposed as
 *   `revocationReason`); `activate*` restores [AccreditationStatus.ACTIVE] and clears it;
 * - listings are ordered by registration time, then DID, so pages are stable; the paged
 *   overloads take `limit >= 0` and `offset >= 0` and apply the [RegistryFilter] before paging;
 * - [statusHistory] returns every registration / revocation / activation of a DID, oldest first.
 */
interface TrustRegistry {
    suspend fun registerIssuer(registration: IssuerRegistration): IssuerRecord

    suspend fun getIssuer(did: String): IssuerRecord?

    suspend fun listIssuers(filter: RegistryFilter = RegistryFilter()): List<IssuerRecord>

    /**
     * One page of [listIssuers]. The default slices the full listing; implementations backed by a
     * database should override it to page in the query.
     */
    suspend fun listIssuers(
        filter: RegistryFilter,
        limit: Int,
        offset: Int,
    ): List<IssuerRecord> {
        requirePage(limit, offset)
        return listIssuers(filter).drop(offset).take(limit)
    }

    suspend fun updateIssuer(
        did: String,
        update: IssuerUpdate,
    ): IssuerRecord

    suspend fun revokeIssuer(
        did: String,
        reason: String? = null,
    ): Boolean

    suspend fun activateIssuer(did: String): Boolean

    suspend fun registerVerifier(registration: VerifierRegistration): VerifierRecord

    suspend fun getVerifier(did: String): VerifierRecord?

    suspend fun listVerifiers(filter: RegistryFilter = RegistryFilter()): List<VerifierRecord>

    /** One page of [listVerifiers]; see the paged [listIssuers]. */
    suspend fun listVerifiers(
        filter: RegistryFilter,
        limit: Int,
        offset: Int,
    ): List<VerifierRecord> {
        requirePage(limit, offset)
        return listVerifiers(filter).drop(offset).take(limit)
    }

    suspend fun updateVerifier(
        did: String,
        update: VerifierUpdate,
    ): VerifierRecord

    suspend fun revokeVerifier(
        did: String,
        reason: String? = null,
    ): Boolean

    suspend fun activateVerifier(did: String): Boolean

    suspend fun getAccreditationStatus(did: String): AccreditationStatus

    suspend fun listCredentialTypes(): List<String>

    /**
     * Audit trail of [did]: its registration and every later revocation / activation (issuer and
     * verifier roles interleaved by time), oldest first. Empty for an unknown DID.
     *
     * The default throws [UnsupportedOperationException]: an implementation that keeps no history
     * must say so instead of returning an empty trail that looks like "never changed".
     */
    suspend fun statusHistory(did: String): List<StatusChange> =
        throw UnsupportedOperationException("${this::class.simpleName} does not keep a status history")
}

/** Validates paging arguments; shared by every implementation of the paged listings. */
fun requirePage(
    limit: Int,
    offset: Int,
) {
    require(limit >= 0) { "limit must be >= 0, got $limit" }
    require(offset >= 0) { "offset must be >= 0, got $offset" }
}
