package org.trustweave.signatures.trustlists

import java.security.cert.X509Certificate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Policy for [VerifiedTrustListLoader.load].
 *
 * @property allowStale             Accept lists whose `NextUpdate` has passed (or is absent). Off by
 *                                  default: a stale list may omit later withdrawals. Enable only for
 *                                  offline use or tests, and surface the staleness to the user.
 * @property previousLotlSequence   Highest LoTL `TSLSequenceNumber` seen before; a lower number is a
 *                                  rollback and is rejected. Persist [TrustList.sequenceNumber]
 *                                  after each successful load and pass it back here.
 * @property previousTslSequences   Same, per territory (upper-case ISO code).
 */
data class TrustListLoadOptions
    @JvmOverloads
    constructor(
        val allowStale: Boolean = false,
        val previousLotlSequence: Int? = null,
        val previousTslSequences: Map<String, Int> = emptyMap(),
    )

/** Why [VerifiedTrustListLoader.load] refused a list. */
enum class TrustListRejection {
    LOTL_SIGNATURE,
    TSL_SIGNATURE,
    MALFORMED,
    TERRITORY_NOT_IN_LOTL,
    TERRITORY_MISMATCH,
    EXPIRED,
    MISSING_NEXT_UPDATE,
    ROLLBACK,
    ISSUED_IN_FUTURE,
}

/** Outcome of [VerifiedTrustListLoader.load]. */
sealed class TrustListLoadResult {
    /**
     * @property trustList   Parsed, signature-verified and fresh trust list.
     * @property staleLists  Labels (`"LoTL"` or a territory code) accepted only because
     *                       [TrustListLoadOptions.allowStale] was set; empty in the strict mode.
     */
    data class Loaded(
        val trustList: TrustList,
        val staleLists: List<String> = emptyList(),
    ) : TrustListLoadResult()

    /** @property territory the offending territory, or null for the LoTL. */
    data class Rejected(
        val reason: TrustListRejection,
        val message: String,
        val territory: String? = null,
    ) : TrustListLoadResult()
}

/**
 * Loads an EU trust graph without assuming the XML is pre-verified.
 *
 * 1. The LoTL's enveloped signature is verified against [lotlSigningCerts] (the pinned OJ signer).
 * 2. Each Member-State TSL is verified against the certificates **its LoTL pointer** publishes, so a
 *    TSL cannot be signed by an arbitrary key and cannot be supplied for a territory the LoTL does
 *    not list.
 * 3. The territory declared in each TSL must match the key it was supplied under.
 * 4. `NextUpdate` is enforced (see [TrustListLoadOptions.allowStale]), future issue dates are
 *    refused, and sequence numbers may not go backwards relative to the caller-supplied
 *    previous values.
 *
 * Rollback protection that does not depend on the caller remembering: pass a [TrustListStateStore] and the loader
 * records, per list, the sequence number and document hash it accepted, and refuses a lower sequence and also a
 * *different* document at an equal sequence. The store is updated only after the whole load succeeded. The explicit
 * `previous*` options still work and are combined with the store (the higher sequence wins).
 *
 * Territories listed by the LoTL but absent from the supplied map are omitted from the result, as
 * with [TrustListParser.parse].
 */
class VerifiedTrustListLoader
    @JvmOverloads
    constructor(
        private val parser: TrustListParser = EtsiTrustListParser(),
        private val clock: Clock = Clock.System,
        private val lotlVerifier: LotlSignatureVerifier = DefaultLotlSignatureVerifier(clock),
        private val tslVerifier: TslSignatureVerifier = DefaultTslSignatureVerifier(clock),
        private val stateStore: TrustListStateStore? = null,
    ) {
        @JvmOverloads
        fun load(
            lotlXml: ByteArray,
            tslXmlByTerritory: Map<String, ByteArray>,
            lotlSigningCerts: List<X509Certificate>,
            options: TrustListLoadOptions = TrustListLoadOptions(),
        ): TrustListLoadResult {
            val now = clock.now()
            val stale = mutableListOf<String>()
            val accepted = LinkedHashMap<String, TrustListState>()

            when (val sig = lotlVerifier.verify(lotlXml, lotlSigningCerts)) {
                is LotlSignatureValidationResult.Valid -> Unit
                is LotlSignatureValidationResult.Invalid ->
                    return TrustListLoadResult.Rejected(
                        TrustListRejection.LOTL_SIGNATURE,
                        "LoTL signature rejected: $sig",
                    )
            }

            val lotl =
                try {
                    parser.parse(lotlXml, emptyMap())
                } catch (e: TrustListParseException) {
                    return TrustListLoadResult.Rejected(TrustListRejection.MALFORMED, "LoTL: ${e.message}")
                }
            freshness(
                "LoTL",
                null,
                lotl.issuedAt,
                lotl.nextUpdateAt,
                lotl.sequenceNumber,
                options.previousLotlSequence,
                options,
                now,
                stale,
                LOTL_KEY,
                lotlXml,
                accepted,
            )?.let { return it }

            val certsByTerritory =
                lotl.tslPointers
                    .groupBy { it.territory }
                    .mapValues { (_, pointers) -> pointers.flatMap { it.signingCertificates } }

            val normalised = LinkedHashMap<String, ByteArray>()
            for ((rawTerritory, bytes) in tslXmlByTerritory) {
                val territory = rawTerritory.trim().uppercase()
                val certs =
                    certsByTerritory[territory]
                        ?: return TrustListLoadResult.Rejected(
                            TrustListRejection.TERRITORY_NOT_IN_LOTL,
                            "territory '$territory' has no pointer in the LoTL",
                            territory,
                        )
                when (val sig = tslVerifier.verify(bytes, certs)) {
                    is LotlSignatureValidationResult.Valid -> Unit
                    is LotlSignatureValidationResult.Invalid ->
                        return TrustListLoadResult.Rejected(
                            TrustListRejection.TSL_SIGNATURE,
                            "TSL signature rejected for '$territory': $sig",
                            territory,
                        )
                }
                normalised[territory] = bytes
            }

            val trustList =
                try {
                    parser.parse(lotlXml, normalised)
                } catch (e: TrustListParseException) {
                    return TrustListLoadResult.Rejected(TrustListRejection.MALFORMED, e.message ?: "parse failure")
                }

            for (tsl in trustList.memberStateLists) {
                val declared = tsl.schemeTerritory
                if (declared == null || declared != tsl.territory) {
                    return TrustListLoadResult.Rejected(
                        TrustListRejection.TERRITORY_MISMATCH,
                        "TSL supplied for '${tsl.territory}' declares SchemeTerritory '${declared ?: "<none>"}'",
                        tsl.territory,
                    )
                }
                freshness(
                    tsl.territory,
                    tsl.territory,
                    tsl.issuedAt,
                    tsl.nextUpdateAt,
                    tsl.sequenceNumber,
                    options.previousTslSequences[tsl.territory],
                    options,
                    now,
                    stale,
                    "TSL:${tsl.territory}",
                    normalised.getValue(tsl.territory),
                    accepted,
                )?.let { return it }
            }
            stateStore?.let { store -> accepted.forEach { (key, state) -> store.put(key, state) } }
            return TrustListLoadResult.Loaded(trustList, stale)
        }

        @Suppress("LongParameterList")
        private fun freshness(
            label: String,
            territory: String?,
            issuedAt: Instant,
            nextUpdateAt: Instant?,
            sequence: Int,
            previousSequence: Int?,
            options: TrustListLoadOptions,
            now: Instant,
            stale: MutableList<String>,
            stateKey: String,
            document: ByteArray,
            accepted: MutableMap<String, TrustListState>,
        ): TrustListLoadResult.Rejected? {
            val stored = stateStore?.get(stateKey)
            val highestSeen = listOfNotNull(previousSequence, stored?.sequence).maxOrNull()
            if (highestSeen != null && sequence < highestSeen) {
                return TrustListLoadResult.Rejected(
                    TrustListRejection.ROLLBACK,
                    "$label sequence number $sequence is lower than the previously seen $highestSeen",
                    territory,
                )
            }
            val hash = TrustListState.hashOf(document)
            if (stored != null && sequence == stored.sequence && hash != stored.documentSha256) {
                return TrustListLoadResult.Rejected(
                    TrustListRejection.ROLLBACK,
                    "$label sequence number $sequence was already accepted with a different document",
                    territory,
                )
            }
            accepted[stateKey] = TrustListState(sequence, hash)
            if (issuedAt > now + MAX_CLOCK_SKEW) {
                return TrustListLoadResult.Rejected(
                    TrustListRejection.ISSUED_IN_FUTURE,
                    "$label was issued at $issuedAt, after the validation time $now",
                    territory,
                )
            }
            when {
                nextUpdateAt == null ->
                    if (options.allowStale) {
                        stale += label
                    } else {
                        return TrustListLoadResult.Rejected(
                            TrustListRejection.MISSING_NEXT_UPDATE,
                            "$label carries no NextUpdate, so its freshness cannot be established",
                            territory,
                        )
                    }
                nextUpdateAt < now ->
                    if (options.allowStale) {
                        stale += label
                    } else {
                        return TrustListLoadResult.Rejected(
                            TrustListRejection.EXPIRED,
                            "$label expired: NextUpdate $nextUpdateAt is before $now",
                            territory,
                        )
                    }
            }
            return null
        }

        private companion object {
            val MAX_CLOCK_SKEW = 5.minutes
            const val LOTL_KEY = "LOTL"
        }
    }
