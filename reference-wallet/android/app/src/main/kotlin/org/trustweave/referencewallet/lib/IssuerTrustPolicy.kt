package org.trustweave.referencewallet.lib

/**
 * Decides which issuers the wallet is willing to store credentials from.
 *
 * A valid issuer signature only proves that *some* key signed the credential; with did:key
 * issuers anyone can mint such a key, so the wallet must also decide whether it trusts that
 * issuer. [CredentialVerification.verifyImportedCredential] fails closed unless a policy accepts
 * the issuer.
 */
fun interface IssuerTrustPolicy {
    fun isTrusted(issuerDid: String): Boolean

    companion object {
        /** Trusts nothing; the default of [CredentialVerification.verifyImportedCredential]. */
        val NONE: IssuerTrustPolicy = IssuerTrustPolicy { false }

        /** Trusts exactly the listed issuer DIDs (exact match, no prefix matching). */
        fun allowList(trusted: Set<String>): IssuerTrustPolicy {
            val copy = trusted.toSet()
            return IssuerTrustPolicy { it in copy }
        }
    }
}

/**
 * The credential signature is valid but its issuer is not trusted. Carries the signed issuer DID so
 * the UI can ask the user to confirm it explicitly. Throwing this trusts and persists nothing.
 */
class UntrustedIssuerException(val issuerDid: String) :
    CredentialVerification.RejectedCredentialException("Issuer $issuerDid is not trusted by this wallet")

/**
 * Where issuer trust comes from. An issuer named by an offer, QR code or backend response is NEVER
 * trusted by being named; trust comes only from
 *  1. the build-time allow-list (`TRUSTED_ISSUERS`),
 *  2. the wallet's own configured backend identity ([DemoBackend.backendIssuers]),
 *  3. issuers the user explicitly confirmed (persisted in [Storage] after a confirmation dialog).
 */
object IssuerTrust {
    const val MAX_ACCEPTED = 200
    const val MAX_DID_LENGTH = 256
    private val SHAPE = Regex("^did:[a-z0-9]+:[A-Za-z0-9._:%#-]+$")

    fun isPlausibleDid(value: String): Boolean = value.length <= MAX_DID_LENGTH && SHAPE.matches(value)

    /** Drops implausible and duplicate entries and caps the list; used on every read of persisted data. */
    fun sanitize(raw: Collection<String>): List<String> = raw.filter(::isPlausibleDid).distinct().take(MAX_ACCEPTED)

    /** Parses the comma-separated `TRUSTED_ISSUERS` build property; invalid entries are dropped. */
    fun parseConfigured(raw: String?): Set<String> =
        (raw ?: "").split(',').map { it.trim() }.filter(::isPlausibleDid).toSet()

    /** Returns [current] plus [did]; refuses malformed identifiers and a list beyond [MAX_ACCEPTED]. */
    fun withAccepted(current: List<String>, did: String): List<String> {
        require(isPlausibleDid(did)) { "Not a valid issuer identifier" }
        if (did in current) return current
        require(current.size < MAX_ACCEPTED) { "At most $MAX_ACCEPTED accepted issuers are supported. Remove one first." }
        return current + did
    }

    /**
     * The effective policy of one import. [confirmedIssuer] is the DID the user has just confirmed
     * (trusted for this import only; the caller persists it after the credential passed every check).
     */
    fun policy(
        configured: Set<String>,
        backend: Set<String>,
        accepted: Collection<String>,
        confirmedIssuer: String? = null,
    ): IssuerTrustPolicy = IssuerTrustPolicy.allowList(configured + backend + accepted + listOfNotNull(confirmedIssuer))
}
