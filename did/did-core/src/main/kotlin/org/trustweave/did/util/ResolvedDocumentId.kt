package org.trustweave.did.util

/**
 * Binds a resolved DID document to the DID that was asked for.
 *
 * A resolver that fetches a document from an external source (a node, a gateway, a universal
 * resolver) must check that `document.id` is the DID that was requested. Otherwise a malicious or
 * misbehaving source can return another subject's document (with that subject's keys) for the
 * requested DID, and a caller that trusts the result attributes the wrong keys to the DID. See
 * DID Resolution: the `id` of the returned document is the DID being resolved.
 *
 * On mismatch callers must reject the result with the `invalidDidDocument` error. They must never
 * rewrite the id to the requested DID, and never cache the document under either DID.
 */
public object ResolvedDocumentId {
    /**
     * Returns `null` when [documentId] is acceptable for [requestedDid], otherwise a
     * human-readable reason.
     *
     * @param requestedDid the DID passed to the resolver
     * @param documentId the `id` of the document the source returned
     * @param allowCanonicalOfLongForm for Sidetree methods (ion, orb): a long-form request
     *   `did:<m>:<suffix>:<initial-state>` may be answered with the canonical short form
     *   `did:<m>:<suffix>`. Any other difference is still a mismatch.
     */
    public fun mismatchReason(
        requestedDid: String,
        documentId: String,
        allowCanonicalOfLongForm: Boolean = false,
    ): String? {
        if (documentId == requestedDid) return null
        if (allowCanonicalOfLongForm && documentId == canonicalOfLongForm(requestedDid)) return null
        return "Resolved document id '$documentId' does not match the requested DID '$requestedDid'"
    }

    private fun canonicalOfLongForm(did: String): String? {
        val parts = did.split(":")
        // did : method : suffix : initial-state (the initial state itself contains no colon).
        // The third segment must be shaped like a Sidetree suffix (46 base64url characters, `Ei`
        // multihash prefix). Without that, `did:ion:test:<suffix>` (network + short form) and
        // `did:orb:<anchor>:<suffix>` would be misread as long forms and their "canonical form"
        // (`did:ion:test`, `did:orb:<anchor>`) would be accepted as a resolved document id.
        return if (parts.size == 4 && parts[0] == "did" && isSidetreeSuffix(parts[2])) parts.take(3).joinToString(":") else null
    }

    private fun isSidetreeSuffix(segment: String): Boolean = segment.length == 46 && segment.startsWith("Ei")
}
