package org.trustweave.referencewallet.lib

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Checks a credential before the wallet stores it.
 *
 * Kotlin port of the web wallet's `reference-wallet/lib/credential-verification.ts`; keep the two
 * in sync. Supported import profile: issuer proofs by an Ed25519 `did:key` issuer (EdDSA JWS).
 * Anything else fails closed — this wallet has no resolver for other DID methods.
 *
 * Checks, in order: format/serialization shape; the issuer JWS signature against the issuer's
 * did:key; the payload profile; that the credential is bound to [holderDid] (`sub`, and for
 * SD-JWT VC the `cnf.kid`); `exp`/`nbf`/`iat`; and for SD-JWT VC that every disclosure's digest
 * is listed once in the signed `_sd` array (so no disclosure can be added or swapped after
 * issuance).
 */
object CredentialVerification {
    private const val MAX_BYTES = 1_048_576
    private val RESERVED_DISCLOSURE_NAMES =
        setOf("iss", "sub", "iat", "nbf", "exp", "vct", "cnf", "_sd", "_sd_alg", "...", "__proto__", "constructor", "prototype")

    class RejectedCredentialException(message: String) : IllegalArgumentException(message)

    private fun reject(message: String): Nothing = throw RejectedCredentialException(message)

    fun verifyImportedCredential(
        compact: String,
        format: String,
        holderDid: String,
        nowEpochSeconds: Long,
    ) {
        if (format != "vc+jwt" && format != "vc+sd-jwt") reject("Unsupported credential format")
        if (compact.toByteArray(Charsets.UTF_8).size > MAX_BYTES) reject("Credential exceeds the 1 MiB import limit")
        if (format == "vc+jwt" && compact.contains('~')) reject("SD-JWT cannot be imported as a plain VC-JWT")
        if (format == "vc+sd-jwt" && !compact.contains('~')) reject("SD-JWT serialization requires a separator")

        val jwt = compact.substringBefore('~')
        val parts = jwt.split(".")
        if (parts.size != 3) reject("Issuer credential is not a compact JWS")
        val header = parseObject(parts[0]) ?: reject("Unsupported issuer signature profile")
        val payload = parseObject(parts[1]) ?: reject("Credential payload is not a JSON object")

        val iss = payload.string("iss") ?: reject("Credential issuer is missing")
        if (header.string("alg") != "EdDSA" || header.containsKey("crit") || header.containsKey("b64")) {
            reject("Unsupported issuer signature profile")
        }
        header["typ"]?.let { typ ->
            val value = (typ as? JsonPrimitive)?.content
            if (value != "JWT" && value != format) reject("Unsupported credential token type")
        }
        val canonicalKid = "$iss#${iss.removePrefix("did:key:")}"
        header["kid"]?.let { kid ->
            val value = (kid as? JsonPrimitive)?.content
            if (value != iss && value != canonicalKid) reject("Issuer signing key does not match the credential issuer")
        }

        // Signature over the JWS signing input, with the key the issuer DID itself encodes.
        val issuerKey =
            try {
                Crypto.didKeyToPublicKey(iss)
            } catch (e: IllegalArgumentException) {
                reject("Only Ed25519 did:key issuers are supported: ${e.message}")
            }
        val signature =
            try {
                Crypto.b64uDecode(parts[2])
            } catch (e: IllegalArgumentException) {
                reject("Issuer signature is not base64url")
            }
        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)
        if (!Crypto.verifyEd25519(signature, signingInput, issuerKey)) reject("Issuer signature is invalid")

        val sub = payload.string("sub")?.takeIf { it.isNotEmpty() } ?: reject("Credential holder is missing")
        if (sub != holderDid) reject("Credential is bound to $sub, not to this wallet's holder $holderDid")

        if (format == "vc+jwt") {
            val vc = payload["vc"] as? JsonObject
            if (vc == null || payload.containsKey("vct") || payload.containsKey("_sd")) reject("Unsupported VC-JWT payload profile")
            val types = vc["type"] as? JsonArray
            if (types == null || types.isEmpty() || types.any { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content.isNullOrEmpty() }) {
                reject("Invalid credential types")
            }
            vc["issuer"]?.let { issuer ->
                val id = (issuer as? JsonPrimitive)?.content ?: (issuer as? JsonObject)?.string("id")
                if (id != iss) reject("Conflicting credential issuer")
            }
            vc["credentialSubject"]?.let { subject ->
                if (subject !is JsonObject) reject("Conflicting credential holder")
                subject["id"]?.let { if ((it as? JsonPrimitive)?.content != sub) reject("Conflicting credential holder") }
            }
        } else {
            if (payload.string("vct").isNullOrEmpty() || payload.containsKey("vc")) reject("Unsupported SD-JWT payload profile")
            val cnf = payload["cnf"] as? JsonObject
            if (cnf == null || cnf.string("kid") != sub || cnf.keys.any { it != "kid" }) {
                reject("Unsupported or conflicting holder key binding")
            }
        }

        val times = listOf("exp", "nbf", "iat").associateWith { claim -> payload[claim]?.let { numberOrReject(it) } }
        val exp = times["exp"]
        val nbf = times["nbf"]
        val iat = times["iat"]
        if (exp != null && exp <= nowEpochSeconds) reject("Credential has expired")
        if (nbf != null && nbf > nowEpochSeconds) reject("Credential is not yet valid")
        if (iat != null && iat > nowEpochSeconds) reject("Credential issue time is in the future")
        if (exp != null && listOfNotNull(nbf, iat).any { it >= exp }) reject("Inconsistent credential validity interval")

        if (format == "vc+sd-jwt") verifyDisclosures(compact, payload)
    }

    /** Every disclosure must hash to a distinct digest in the signed top-level `_sd` array. */
    private fun verifyDisclosures(
        compact: String,
        payload: JsonObject,
    ) {
        payload["_sd_alg"]?.let { if ((it as? JsonPrimitive)?.content != "sha-256") reject("Unsupported disclosure digest algorithm") }
        val decoded = SdJwt.decode(compact)
        if (decoded.kbJwt != null) reject("Import the issuer credential, not a holder presentation")

        val digests = mutableSetOf<String>()

        fun collect(
            node: JsonElement,
            depth: Int,
        ) {
            if (depth > 32) reject("Credential nesting exceeds 32 levels")
            when (node) {
                is JsonArray -> node.forEach { collect(it, depth + 1) }
                is JsonObject ->
                    node.forEach { (key, value) ->
                        when (key) {
                            "..." -> reject("Array selective disclosures are not supported by this wallet")
                            "_sd" -> {
                                val array = value as? JsonArray ?: reject("Invalid disclosure digests")
                                if (depth != 0) reject("Nested selective disclosures are not supported by this wallet")
                                array.forEach { item ->
                                    val digest = (item as? JsonPrimitive)?.takeIf { it.isString }?.content ?: reject("Invalid disclosure digests")
                                    if (!digests.add(digest)) reject("Duplicate disclosure digest")
                                }
                            }
                            else -> collect(value, depth + 1)
                        }
                    }
                else -> Unit
            }
        }
        collect(payload, 0)

        val names = mutableSetOf<String>()
        for (disclosure in decoded.disclosures) {
            if (disclosure.hash !in digests ||
                disclosure.name in names ||
                disclosure.name in RESERVED_DISCLOSURE_NAMES ||
                payload.containsKey(disclosure.name)
            ) {
                reject("Disclosure is not uniquely bound to the issuer signature")
            }
            collect(disclosure.value, 1)
            names.add(disclosure.name)
        }
    }

    private fun parseObject(segment: String): JsonObject? =
        try {
            Json.parseToJsonElement(Crypto.b64uDecodeString(segment)).jsonObject
        } catch (e: Exception) {
            null
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun numberOrReject(element: JsonElement): Long {
        val primitive = element as? JsonPrimitive
        val value = primitive?.takeIf { !it.isString }?.doubleOrNull
        if (value == null || !value.isFinite()) reject("Invalid credential time claim")
        return value.toLong()
    }
}
