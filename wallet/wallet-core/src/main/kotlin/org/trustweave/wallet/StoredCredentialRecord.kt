package org.trustweave.wallet

import org.trustweave.credential.model.vc.VerifiableCredential

/** Storage handles are distinct from signed credential identifiers; never rewrite a signed VC. */
data class StoredCredentialRecord(
    val storageId: String,
    val credential: VerifiableCredential,
)

/** Optional storage capability for credentials that do not carry their own identifier. */
interface CredentialRecordStorage {
    suspend fun listRecords(filter: CredentialFilter? = null): List<StoredCredentialRecord>
}

enum class StoredCredentialStatus { ACTIVE, REVOKED, SUSPENDED, UNKNOWN }

/** Supply an adapter to a trusted revocation manager when live status is required. */
fun interface WalletStatusResolver {
    suspend fun resolve(credential: VerifiableCredential): StoredCredentialStatus
}

suspend fun resolveStoredStatus(
    credential: VerifiableCredential,
    resolver: WalletStatusResolver?,
): StoredCredentialStatus =
    if (credential.credentialStatus == null) {
        StoredCredentialStatus.ACTIVE
    } else {
        resolver?.resolve(credential) ?: StoredCredentialStatus.UNKNOWN
    }

/** Offline stores must report unknown status instead of treating a reference as revocation. */
fun matchesOfflineStatusFilter(
    credential: VerifiableCredential,
    filter: CredentialFilter,
): Boolean {
    if (filter.hasStatusEntry != null && (credential.credentialStatus != null) != filter.hasStatusEntry) return false
    if (filter.revoked == null) return true
    check(credential.credentialStatus == null) { "Revocation status is unknown; use a status-aware wallet with a WalletStatusResolver" }
    return filter.revoked == false
}

/** Explicit recovery result. A partial read must never masquerade as a complete list. */
data class CredentialReadFailure(
    val storageHandle: String,
    val reason: String,
)

data class CredentialRecoveryResult(
    val records: List<StoredCredentialRecord>,
    val failures: List<CredentialReadFailure>,
) {
    val complete: Boolean get() = failures.isEmpty()
}

interface CredentialRecovery {
    suspend fun recoverRecords(): CredentialRecoveryResult
}

/** A cursor is the last scanned storage ID, scoped to the same wallet and filter. */
data class CredentialPage(
    val records: List<StoredCredentialRecord>,
    val nextCursor: String?,
)

/** Bounded reads: filters may produce an empty page with a non-null continuation cursor. */
interface PagedCredentialStorage {
    suspend fun pageRecords(
        limit: Int = 100,
        after: String? = null,
        filter: CredentialFilter? = null,
    ): CredentialPage
}
