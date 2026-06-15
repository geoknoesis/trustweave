package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.util.Base64

/** AVP-Micro content digests. Format matches avp_crypto.py::jcs_digest. */
object Digests {
    /**
     * `"sha-256:" + base64url-nopad(sha256(JCS(unsecured)))` where `unsecured` is `obj`
     * with the `"proof"` key removed — matching avp_crypto.py::jcs_digest which strips
     * the proof before hashing so the digest covers only the document payload.
     */
    fun jcsDigest(obj: JsonObject): String {
        val unsecured = JsonObject(obj.filterKeys { it != "proof" })
        val hash = MessageDigest.getInstance("SHA-256").digest(Jcs.canonicalize(unsecured))
        return "sha-256:" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
