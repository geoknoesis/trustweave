package org.trustweave.credential.internal.infrastructure

import kotlinx.serialization.json.JsonObject
import org.trustweave.credential.internal.JsonLdUtils
import org.trustweave.credential.spi.proof.JsonLdCanonicalizationPort

/**
 * Default implementation of [JsonLdCanonicalizationPort] backed by the jsonld-java library.
 *
 * Fail-closed: canonicalization errors propagate as exceptions — there is no fallback to
 * plain JSON serialization (a fallback would make the signing input non-deterministic and
 * mask `@context` resolution failures). See [JsonLdUtils.canonicalizeDocument].
 */
internal class DefaultJsonLdCanonicalizationAdapter : JsonLdCanonicalizationPort {

    override fun canonicalize(document: JsonObject): String =
        JsonLdUtils.canonicalizeDocument(document)
}
