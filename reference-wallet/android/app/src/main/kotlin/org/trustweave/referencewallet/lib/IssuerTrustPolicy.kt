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
