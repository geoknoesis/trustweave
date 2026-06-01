package org.trustweave.anchor.payment

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Config-time deprecation helper for raw private-key options on blockchain anchor providers.
 *
 * Background: see `docs/.internal/trusted-domain-blockchain-payment-design.md`.
 *
 * Phase 2 of the on-chain treasury design keeps embedded credentials on plugins for backward
 * compatibility, but operators should migrate key custody to the treasury via
 * `TrustedDomainManager.chainAccount(...)` with a KMS `KmsKeyRef`. This utility surfaces a
 * single WARN line per provider instance when raw-credential options are detected so the
 * deprecation is visible without breaking existing deployments.
 */
object PaymentDeprecation {

    private val logger: Logger = LoggerFactory.getLogger(PaymentDeprecation::class.java)

    private val RAW_CRED_KEYS = setOf(
        "privateKey",
        "mnemonic",
        "secretKey",
        "sk",
        "wif",
        "seed",
    )

    private val warned = ConcurrentHashMap.newKeySet<Int>()

    /**
     * Scans [options] for any raw-credential keys and logs a single WARN per provider instance.
     *
     * The check is case-insensitive on the option key and matches whole-key equality only
     * (a key called "privateKeyRef" or "encryptedSeed" is *not* a raw credential and is ignored).
     * Idempotency is keyed on `(chainId, provider-instance-identity, set-of-matched-keys)`.
     *
     * @param chainId CAIP-2 chain id (used only for context in the log line)
     * @param options provider configuration map
     * @param providerInstance the provider object emitting the warning; used to scope idempotency
     *                         to a single instance so re-creation by tests still warns once each
     * @param log optional override for the logger (defaults to this object's logger)
     */
    fun warnIfRawCreds(
        chainId: String,
        options: Map<String, Any?>,
        providerInstance: Any,
        log: Logger = logger,
    ) {
        if (options.isEmpty()) return
        val matched = options.keys
            .filter { key -> RAW_CRED_KEYS.any { it.equals(key, ignoreCase = true) } }
            .toSortedSet()
        if (matched.isEmpty()) return

        val fingerprint = listOf(
            chainId,
            System.identityHashCode(providerInstance),
            matched,
        ).hashCode()
        if (!warned.add(fingerprint)) return

        log.warn(
            "[trustweave.anchor] Raw private-key options {} on provider {} (chain={}) are " +
                "DEPRECATED. Migrate to TrustedDomainManager.chainAccount(...) with a KMS " +
                "keyRef. See docs/.internal/trusted-domain-blockchain-payment-design.md.",
            matched,
            providerInstance::class.qualifiedName ?: providerInstance::class.java.name,
            chainId,
        )
    }
}
