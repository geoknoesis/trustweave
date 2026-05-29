package org.trustweave.anchor.indy

/**
 * Hyperledger Indy transaction type codes.
 *
 * These match the wire-protocol type codes documented in indy-node:
 * https://github.com/hyperledger/indy-node/blob/main/docs/source/transactions.md
 *
 * Only the subset used by this anchor plugin is enumerated; ATTRIB / GET_ATTRIB are
 * the transactions used to store and retrieve anchored digests against a submitter DID.
 */
internal object IndyTxnTypes {
    const val NYM = "1"
    const val ATTRIB = "100"
    const val GET_ATTRIB = "104"

    /** Default Indy protocol version for ledger requests. */
    const val PROTOCOL_VERSION = 2
}

/**
 * Constants used when encoding anchored payloads inside an ATTRIB transaction's `raw` field.
 *
 * Indy ATTRIB transactions accept three storage forms — `raw` (arbitrary JSON object),
 * `hash` (SHA-256 hex digest), and `enc` (encrypted blob). We use `raw` to store a small
 * JSON object containing the digest and original media type so that GET_ATTRIB can return
 * the same payload without an external store.
 */
internal object IndyAttribFields {
    /** Top-level key under `raw` holding the anchored digest (lowercase hex SHA-256). */
    const val DIGEST = "digest"

    /** Top-level key under `raw` holding the original media type. */
    const val MEDIA_TYPE = "mediaType"

    /** Top-level key under `raw` holding the payload, kept inline so reads round-trip. */
    const val PAYLOAD = "payload"

    /** Attribute name used by both ATTRIB (`raw` object key) and GET_ATTRIB (`raw` string). */
    const val ATTRIB_NAME = "trustweaveAnchor"
}
