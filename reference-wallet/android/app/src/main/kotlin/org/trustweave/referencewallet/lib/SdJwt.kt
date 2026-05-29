package org.trustweave.referencewallet.lib

import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SD-JWT and SD-JWT VC helpers (IETF drafts).
 *
 * Mirror of the web wallet's `reference-wallet/lib/sdjwt.ts`. Keep in sync.
 *
 * Spec references:
 *  - draft-ietf-oauth-selective-disclosure-jwt (SD-JWT core)
 *  - draft-ietf-oauth-sd-jwt-vc (SD-JWT VC profile)
 */
object SdJwt {

    private val secureRandom = SecureRandom()

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    /** `_sd_alg`="sha-256" — base64url(SHA-256(disclosure)). */
    fun disclosureHash(disclosure: String): String =
        Crypto.b64uEncode(sha256(disclosure.toByteArray(Charsets.UTF_8)))

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Crypto.b64uEncode(bytes)
    }

    /** Build one disclosure for an object-property claim: `base64url([salt, name, value])`. */
    fun createObjectDisclosure(name: String, value: JsonElement): Disclosure {
        val salt = randomSalt()
        val arr = buildJsonArray {
            add(JsonPrimitive(salt))
            add(JsonPrimitive(name))
            add(value)
        }
        val disclosureJson = Json.encodeToString(JsonElement.serializer(), arr)
        val disclosure = Crypto.b64uEncodeString(disclosureJson)
        return Disclosure(raw = disclosure, hash = disclosureHash(disclosure), salt = salt, name = name, value = value)
    }

    /** Parse a disclosure back to its components (without verifying any signature). */
    fun parseDisclosure(raw: String): Disclosure {
        val json = Crypto.b64uDecodeString(raw)
        val arr = Json.parseToJsonElement(json).jsonArray
        require(arr.size == 3) { "Object disclosure must be [salt, name, value], got length ${arr.size}" }
        val salt = arr[0].jsonPrimitive.content
        val name = arr[1].jsonPrimitive.content
        val value = arr[2]
        return Disclosure(raw = raw, hash = disclosureHash(raw), salt = salt, name = name, value = value)
    }

    data class Disclosure(
        val raw: String,
        val hash: String,
        val salt: String,
        val name: String,
        val value: JsonElement,
    )

    data class DecodedSdJwtVc(
        val issuerJwt: String,
        val issuerPayload: kotlinx.serialization.json.JsonObject,
        val disclosures: List<Disclosure>,
        val kbJwt: String?,
    )

    /** Structural decode of an SD-JWT VC. Does NOT verify signatures or hashes. */
    fun decode(sdJwtVc: String): DecodedSdJwtVc {
        val parts = sdJwtVc.split("~")
        require(parts.isNotEmpty()) { "Empty SD-JWT VC" }
        val issuerJwt = parts[0]
        // Last segment is empty if there's no KB-JWT, non-empty otherwise.
        var lastIdx = parts.size - 1
        val kbJwt: String? = if (parts[lastIdx].isNotEmpty()) {
            parts[lastIdx].also { lastIdx -= 1 }
        } else null
        val disclosureSegments = parts.subList(1, lastIdx + 1).filter { it.isNotEmpty() }
        val disclosures = disclosureSegments.map { parseDisclosure(it) }

        val jwtParts = issuerJwt.split(".")
        require(jwtParts.size == 3) { "Issuer JWT must have three parts" }
        val payloadJson = Crypto.b64uDecodeString(jwtParts[1])
        val issuerPayload = Json.parseToJsonElement(payloadJson).jsonObject
        return DecodedSdJwtVc(issuerJwt, issuerPayload, disclosures, kbJwt)
    }

    /**
     * Build a holder presentation. Selects only the named disclosures and appends a
     * KB-JWT signed by the holder, binding to the verifier's audience + nonce + the
     * SHA-256 of the presentation prefix (`sd_hash`).
     *
     * Phase 2.5b: takes a signing closure so a Keystore-bound holder key works
     * transparently (its private bytes never leave AndroidKeyStore).
     */
    fun present(
        sdJwtVc: String,
        selectDisclose: Set<String>,
        holderSigner: (ByteArray) -> ByteArray,
        holderDid: String,
        audience: String,
        nonce: String,
        now: Long,
    ): String {
        val decoded = decode(sdJwtVc)
        val selected = decoded.disclosures.filter { it.name in selectDisclose }
        val prefix = (listOf(decoded.issuerJwt) + selected.map { it.raw } + listOf("")).joinToString("~")
        val sdHash = Crypto.b64uEncode(sha256(prefix.toByteArray(Charsets.UTF_8)))
        val didTail = holderDid.removePrefix("did:key:")
        val kbPayload = buildJsonObject {
            put("iat", now)
            put("aud", audience)
            put("nonce", nonce)
            put("sd_hash", sdHash)
        }
        val kbJwt = Crypto.signJwsCompact(
            jsonPayload = Json.encodeToString(JsonElement.serializer(), kbPayload),
            kid = "$holderDid#$didTail",
            typ = "kb+jwt",
            signer = holderSigner,
        )
        return prefix + kbJwt
    }
}
