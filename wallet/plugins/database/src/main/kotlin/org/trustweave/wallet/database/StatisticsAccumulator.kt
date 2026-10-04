package org.trustweave.wallet.database

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.StoredCredentialStatus
import org.trustweave.wallet.WalletStatusResolver
import org.trustweave.wallet.resolveStoredStatus
import kotlin.time.Clock

/** Folds credentials into the counters behind [org.trustweave.wallet.WalletStatistics]. */
internal class StatisticsAccumulator(
    private val statusResolver: WalletStatusResolver?,
) {
    private val now = Clock.System.now()

    var valid = 0
        private set
    var expired = 0
        private set
    var revoked = 0
        private set
    var unknown = 0
        private set

    suspend fun add(credential: VerifiableCredential) {
        val status = resolveStoredStatus(credential, statusResolver)
        val isExpired = effectiveExpiry(credential)?.let { it <= now } == true
        if (isExpired) expired++
        if (status == StoredCredentialStatus.REVOKED) revoked++
        if (status == StoredCredentialStatus.UNKNOWN) unknown++
        if (credential.proof != null && status == StoredCredentialStatus.ACTIVE && !isExpired) valid++
    }
}
