package org.trustweave.signatures.trustlists

/**
 * Parses ETSI TS 119 612 XML bytes into a [TrustList].
 *
 * Two implementations are envisaged:
 * - [EtsiTrustListParser] — DOM-based parser for the production XML format.
 * - Future: a streaming parser for very large trust graphs (the entire EU LoTL is < 5 MB today,
 *   so DOM is fine for the MVP).
 *
 * **Trust assumption.** The MVP treats the supplied XML bytes as *pre-verified*: the caller is
 * expected to validate the LoTL's enveloped XAdES signature (or pin the LoTL to a known good
 * checksum) before calling [parse]. Self-validation of the LoTL signature is a deferred follow-up,
 * documented in section 12 of `docs/architecture/eidas-qes-design.md`.
 */
interface TrustListParser {

    /**
     * Parse the LoTL pointer file and the TSL XML documents it references.
     *
     * @param lotlXml             DER (well, UTF-8 XML) bytes of the root List of Trusted Lists.
     *                            Used to extract top-level metadata (scheme operator, sequence
     *                            number, issue / next-update dates).
     * @param tslXmlByTerritory   Map of ISO 3166-1 alpha-2 territory code → bytes of that
     *                            Member State's `TrustServiceStatusList` XML. Territories listed
     *                            by the LoTL but absent from this map are silently omitted from
     *                            the result; territories present in this map but absent from the
     *                            LoTL are still parsed.
     * @throws TrustListParseException on malformed XML or missing required elements.
     */
    fun parse(lotlXml: ByteArray, tslXmlByTerritory: Map<String, ByteArray>): TrustList
}

/** Thrown by [TrustListParser] implementations on malformed or non-conformant input. */
class TrustListParseException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
