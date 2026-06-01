package org.trustweave.anchor.algorand

import com.algorand.algosdk.account.Account
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder

/**
 * Resolves a sponsor DID to the on-chain account that pays the fee in an
 * Algorand fee-payer atomic group.
 *
 * Sponsor key custody stays inside the plugin in Phase 3 — centralising it
 * under the treasury is Phase 3+ scope. The registry indirection exists so
 * tests can inject stub sponsors and so production deployments can swap in a
 * KMS-backed implementation later.
 */
interface SponsorRegistry {
    /**
     * @return sponsor entry for [sponsorDid] or `null` if no sponsor is
     *   registered. The plugin maps a `null` return to
     *   `TreasuryException.SponsorNotAllowed`.
     */
    fun resolve(sponsorDid: String): SponsorEntry?
}

/**
 * Algorand sponsor — an address plus an opaque signer. The signer is invoked
 * on the unsigned sponsor transaction inside the atomic group and must return
 * a [SignedTransaction] msgpack-encodable by the Algorand SDK.
 */
data class SponsorEntry(
    val address: String,
    val sign: (Transaction) -> SignedTransaction,
)

/**
 * Reads sponsor accounts from the client's options map. Recognised keys:
 *
 * - `sponsor.<did>.mnemonic` — 25-word Algorand mnemonic (preferred; address
 *   is derived from the key, no `.address` entry needed)
 * - `sponsor.<did>.privateKey` — base64-encoded 64-byte Ed25519 secret-key
 *   pair (same encoding the existing `privateKey` option uses)
 * - `sponsor.<did>.address` — required only when neither of the above is
 *   present (e.g. in tests that pre-register a sponsor entry programmatically)
 */
class ConfigSponsorRegistry(
    private val options: Map<String, Any?>,
) : SponsorRegistry {

    override fun resolve(sponsorDid: String): SponsorEntry? {
        val mnemonic = options["sponsor.$sponsorDid.mnemonic"] as? String
        val privateKey = options["sponsor.$sponsorDid.privateKey"] as? String
        val account: Account? = when {
            mnemonic != null -> runCatching { Account(mnemonic) }.getOrNull()
            privateKey != null -> runCatching { Account(Encoder.decodeFromBase64(privateKey)) }.getOrNull()
            else -> null
        }
        if (account != null) {
            return SponsorEntry(
                address = account.address.toString(),
                sign = { tx -> account.signTransaction(tx) },
            )
        }
        return null
    }
}
