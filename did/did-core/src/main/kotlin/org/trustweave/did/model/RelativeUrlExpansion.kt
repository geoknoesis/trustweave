package org.trustweave.did.model

/**
 * Expands relative DID URLs in this document to absolute DID URLs, per the
 * `expandRelativeUrls` resolution option (DID Resolution 1.0 §4.1, §4.4).
 *
 * Verification methods and verification relationships are modelled as
 * [org.trustweave.did.identifiers.VerificationMethodId], which always renders an absolute DID URL,
 * so only [DidService.id] can be relative. A value that already carries a URI scheme is left
 * untouched; a leading `#` is appended to the document `id`; anything else is treated as a
 * relative path reference.
 *
 * Returns this document unchanged (same instance) when there is nothing to expand.
 */
fun DidDocument.expandRelativeDidUrls(): DidDocument {
    if (service.none { it.id.isRelativeDidUrl() }) return this
    return copy(
        service = service.map { svc ->
            if (svc.id.isRelativeDidUrl()) svc.copy(id = expandAgainst(id.value, svc.id)) else svc
        }
    )
}

/** True when this identifier has no URI scheme and therefore needs expansion. */
private fun String.isRelativeDidUrl(): Boolean {
    if (isEmpty()) return false
    if (startsWith("#")) return true
    // A scheme is ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) ":" per RFC 3986 §3.1.
    val colon = indexOf(':')
    if (colon <= 0) return true
    val scheme = substring(0, colon)
    return !(scheme[0].isLetter() && scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' })
}

private fun expandAgainst(baseDid: String, relative: String): String = when {
    relative.startsWith("#") -> baseDid + relative
    relative.startsWith("/") -> baseDid + relative
    relative.startsWith("?") -> baseDid + relative
    else -> "$baseDid/$relative"
}
