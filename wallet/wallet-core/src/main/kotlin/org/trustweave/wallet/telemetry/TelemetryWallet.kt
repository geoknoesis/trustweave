package org.trustweave.wallet.telemetry

import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.CredentialFilter
import org.trustweave.wallet.CredentialQueryBuilder
import org.trustweave.wallet.Wallet

/**
 * Reports wallet storage operations to [Telemetry], and delegates everything else unchanged.
 *
 * The wallet is where a credential goes to be found again, and it is usually backed by something
 * that can be slow or unavailable — a database, a file system, a cloud store. Before this, a host
 * seeing a slow presentation could not tell an expensive proof from a stalled read. Wrapping the
 * configured wallet closes that gap without every storage plugin carrying instrumentation.
 *
 * Kotlin interface delegation supplies every other member, so a method added to [Wallet] keeps
 * working here rather than silently losing its implementation.
 *
 * Nothing is recorded until a host installs a sink. Credential identifiers, subjects, issuers and
 * filter contents are never attached to an event: they identify people, and these become metric
 * labels. Result counts are not attached either, for the same reason a count of one is a fact
 * about a subject.
 */
public class TelemetryWallet(
    private val delegate: Wallet,
) : Wallet by delegate {
    override suspend fun store(credential: VerifiableCredential): String =
        Telemetry.measure(Operation.WALLET_STORE) { delegate.store(credential) }

    override suspend fun get(credentialId: String): VerifiableCredential? =
        Telemetry.measure(Operation.WALLET_GET) {
            delegate.get(credentialId).also { found ->
                // A miss is not a failure, and a host that pages on it will page on ordinary
                // traffic — but it is the outcome worth being able to chart separately.
                if (found == null) Telemetry.rejected(Operation.WALLET_GET, "NotFound")
            }
        }

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> =
        Telemetry.measure(Operation.WALLET_QUERY, LIST) { delegate.list(filter) }

    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> =
        Telemetry.measure(Operation.WALLET_QUERY, QUERY) { delegate.query(query) }

    override suspend fun delete(credentialId: String): Boolean =
        Telemetry.measure(Operation.WALLET_DELETE) {
            delegate.delete(credentialId).also { deleted ->
                if (!deleted) Telemetry.rejected(Operation.WALLET_DELETE, "NotFound")
            }
        }

    private companion object {
        /** Both read paths are one operation; this says which shape the caller used. */
        val LIST = mapOf("wallet.read" to "list")
        val QUERY = mapOf("wallet.read" to "query")
    }
}

/** Wraps this wallet so its storage operations are reported to [Telemetry]. */
public fun Wallet.withTelemetry(): Wallet = if (this is TelemetryWallet) this else TelemetryWallet(this)
