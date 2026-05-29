package org.trustweave.referencewallet.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * SD-JWT and SD-JWT VC helpers — multiplatform.
 *
 * Drafts:
 *  - draft-ietf-oauth-selective-disclosure-jwt (SD-JWT core)
 *  - draft-ietf-oauth-sd-jwt-vc (SD-JWT VC profile)
 *
 * Format: `<issuer-jwt>~<disclosure1>~<disclosure2>~...~[<kb-jwt>]`
 *
 * Phase 2.5c moves this code from android-only to shared/commonMain so the
 * future iOS wallet, web wallet (via Kotlin/JS export, or its own TS mirror),
 * and any future server-side issuer/verifier can use the same canonical impl.
 */
object SdJwtVc {

    /** `_sd_alg`="sha-256" — base64url(SHA-256(disclosure)). */
    fun disclosureHash(disclosure: String): String =
        Base64Url.encode(sha256(disclosure.encodeToByteArray()))

    fun randomSalt(): String = Base64Url.encode(secureRandomBytes(16))

    /** A single disclosure for an object-property claim. */
    data class Disclosure(
        val raw: String,    // base64url([salt, name, value])
        val hash: String,
        val salt: String,
        val name: String,
        val value: JsonElement,
    )

    /** Build one disclosure: `base64url([salt, name, value])`. */
    fun createObjectDisclosure(name: String, value: JsonElement): Disclosure {
        val salt = randomSalt()
        val arr = buildJsonArray {
            add(JsonPrimitive(salt))
            add(JsonPrimitive(name))
            add(value)
        }
        val raw = Base64Url.encodeString(Json.encodeToString(JsonElement.serializer(), arr))
        return Disclosure(raw, disclosureHash(raw), salt, name, value)
    }

    /** Parse a disclosure back to its components. Does not verify any hash. */
    fun parseDisclosure(raw: String): Disclosure {
        val json = Base64Url.decodeString(raw)
        val arr = Json.parseToJsonElement(json).jsonArray
        require(arr.size == 3) { "Object disclosure must be [salt, name, value], got length ${arr.size}" }
        return Disclosure(
            raw = raw,
            hash = disclosureHash(raw),
            salt = arr[0].jsonPrimitive.content,
            name = arr[1].jsonPrimitive.content,
            value = arr[2],
        )
    }

    data class DecodedSdJwtVc(
        val issuerJwt: String,
        val issuerPayload: JsonObject,
        val disclosures: List<Disclosure>,
        val kbJwt: String?,
    )

    /** Structural decode. Does NOT verify signatures or hashes. */
    fun decode(sdJwtVc: String): DecodedSdJwtVc {
        val parts = sdJwtVc.split("~")
        require(parts.isNotEmpty()) { "Empty SD-JWT VC" }
        val issuerJwt = parts[0]
        var lastIdx = parts.size - 1
        val kbJwt: String? = if (parts[lastIdx].isNotEmpty()) {
            parts[lastIdx].also { lastIdx -= 1 }
        } else null
        val disclosureSegments = parts.subList(1, lastIdx + 1).filter { it.isNotEmpty() }
        val disclosures = disclosureSegments.map { parseDisclosure(it) }

        val jwtParts = issuerJwt.split(".")
        require(jwtParts.size == 3) { "Issuer JWT must have three parts" }
        val payloadJson = Base64Url.decodeString(jwtParts[1])
        val issuerPayload = Json.parseToJsonElement(payloadJson).jsonObject
        return DecodedSdJwtVc(issuerJwt, issuerPayload, disclosures, kbJwt)
    }

    /**
     * Build a compact JWS (Ed25519, alg=EdDSA) with custom `typ` header.
     * Used both for the issuer JWT (typ=JWT) and KB-JWT (typ=kb+jwt).
     */
    fun signCompactJws(
        jsonPayload: String,
        kid: String,
        typ: String,
        signer: (ByteArray) -> ByteArray,
    ): String {
        val header = """{"alg":"EdDSA","typ":"$typ","kid":"$kid"}"""
        val encodedHeader = Base64Url.encodeString(header)
        val encodedPayload = Base64Url.encodeString(jsonPayload)
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = signer(signingInput.encodeToByteArray())
        return "$signingInput.${Base64Url.encode(signature)}"
    }

    /**
     * Build a holder presentation: select disclosures, append a KB-JWT signed by the
     * holder, binding to verifier audience + nonce + SHA-256(presentation prefix).
     */
    fun present(
        sdJwtVc: String,
        selectDisclose: Set<String>,
        holderSigner: (ByteArray) -> ByteArray,
        holderDid: String,
        audience: String,
        nonce: String,
        nowEpochSeconds: Long,
    ): String {
        val decoded = decode(sdJwtVc)
        val selected = decoded.disclosures.filter { it.name in selectDisclose }
        val prefix = (listOf(decoded.issuerJwt) + selected.map { it.raw } + listOf("")).joinToString("~")
        val sdHash = Base64Url.encode(sha256(prefix.encodeToByteArray()))
        val didTail = holderDid.removePrefix("did:key:")
        val kbPayload = buildJsonObject {
            put("iat", nowEpochSeconds)
            put("aud", audience)
            put("nonce", nonce)
            put("sd_hash", sdHash)
        }
        val kbJwt = signCompactJws(
            jsonPayload = Json.encodeToString(JsonElement.serializer(), kbPayload),
            kid = "$holderDid#$didTail",
            typ = "kb+jwt",
            signer = holderSigner,
        )
        return prefix + kbJwt
    }
}
