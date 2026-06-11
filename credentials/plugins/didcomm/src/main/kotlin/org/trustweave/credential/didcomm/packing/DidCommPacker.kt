package org.trustweave.credential.didcomm.packing

import org.trustweave.core.util.decodeBase58
import org.trustweave.credential.didcomm.crypto.DidCommCryptoInterface
import org.trustweave.credential.didcomm.exception.DidCommException
import org.trustweave.credential.didcomm.models.DidCommEnvelope
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.json.putJsonArray
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.Base64

/**
 * Packs and unpacks DIDComm V2 messages.
 *
 * Supports:
 * - Plain messages (signed or unsigned)
 * - Encrypted messages (AuthCrypt)
 *
 * Following DIDComm V2 specification:
 * - Messages use JWM (JSON Web Message) format
 * - Encryption uses ECDH-1PU (AuthCrypt) or ECDH-ES (AnonCrypt)
 * - Messages can be signed using JWS (`alg: EdDSA` over the JWS signing input)
 *
 * Signed plain messages are verified on unpack: the signer's Ed25519 public key is resolved
 * from its DID document via [resolveDid] and every entry in the `signatures` array must verify,
 * otherwise [DidCommException.UnpackingFailed] is thrown (fail closed). Unsigned plain messages
 * make no authenticity claim and are returned as-is.
 */
class DidCommPacker(
    private val crypto: DidCommCryptoInterface,
    private val resolveDid: suspend (String) -> DidDocument?,
    private val signer: suspend (ByteArray, String) -> ByteArray // Signer for plain messages
) {
    private companion object {
        const val JWS_ALG_EDDSA = "EdDSA"
        const val ED25519_RAW_KEY_LENGTH_BYTES = 32
        const val ED25519_SIGNATURE_LENGTH_BYTES = 64

        /** X.509 SubjectPublicKeyInfo prefix for a raw Ed25519 public key (RFC 8410). */
        val ED25519_SPKI_PREFIX = byteArrayOf(
            0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00,
        )
    }

    /** Compact, deterministic JSON used for JWS payload bytes on both sign and verify. */
    private val compactJson = Json { prettyPrint = false; encodeDefaults = false }

    /**
     * Packs a message for sending.
     *
     * @param message The message to pack
     * @param fromDid Sender DID
     * @param fromKeyId Sender key ID for encryption/signing
     * @param toDid Recipient DID
     * @param toKeyId Recipient key ID (from their DID document)
     * @param encrypt Whether to encrypt the message (default: true)
     * @param sign Whether to sign the message (default: true)
     * @return Packed message as JSON string
     */
    suspend fun pack(
        message: DidCommMessage,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String,
        encrypt: Boolean = true,
        sign: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val messageJson = message.toJsonObject()

        if (encrypt) {
            // Encrypt the message
            val envelope = crypto.encrypt(
                message = messageJson,
                fromDid = fromDid,
                fromKeyId = fromKeyId,
                toDid = toDid,
                toKeyId = toKeyId
            )

            // Serialize envelope to JSON
            val envelopeJson = buildJsonObject {
                put("protected", envelope.protected)
                put("recipients", JsonArray(
                    envelope.recipients.map { recipient ->
                        buildJsonObject {
                            put("header", buildJsonObject {
                                put("kid", recipient.header.kid)
                                put("alg", recipient.header.alg)
                                recipient.header.epk?.let { put("epk", it) }
                            })
                            put("encrypted_key", recipient.encrypted_key)
                        }
                    }
                ))
                put("iv", envelope.iv)
                put("ciphertext", envelope.ciphertext)
                put("tag", envelope.tag)
            }

            Json.encodeToString(JsonObject.serializer(), envelopeJson)
        } else {
            // Plain message (may be signed)
            if (sign) {
                // Sign the message using JWS
                val signedMessage = signMessage(messageJson, fromDid, fromKeyId)
                Json.encodeToString(JsonObject.serializer(), signedMessage)
            } else {
                Json.encodeToString(JsonObject.serializer(), messageJson)
            }
        }
    }

    /**
     * Unpacks a received message.
     *
     * Plain messages that carry a `signatures` array are verified before being returned:
     * every signature must be a valid `EdDSA` JWS by a key resolvable from the signer's
     * DID document, and the signer must match the message `from` / [senderDid] when present.
     * Any verification failure throws [DidCommException.UnpackingFailed] (fail closed).
     *
     * @param packedMessage The packed message (JSON string)
     * @param recipientDid Recipient DID
     * @param recipientKeyId Recipient key ID
     * @param senderDid Expected sender DID (for verification)
     * @return Unpacked message
     */
    suspend fun unpack(
        packedMessage: String,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String? = null
    ): DidCommMessage = withContext(Dispatchers.IO) {
        val json = Json.parseToJsonElement(packedMessage)

        // Check if it's an encrypted envelope
        if (json.jsonObject.containsKey("ciphertext")) {
            // Encrypted message
            val envelope = parseEnvelope(json.jsonObject)

            val resolvedSenderDid = senderDid ?: extractSenderDid(envelope)
                ?: throw IllegalArgumentException("Cannot determine sender DID")

            val decryptedJson = crypto.decrypt(
                envelope = envelope,
                recipientDid = recipientDid,
                recipientKeyId = recipientKeyId,
                senderDid = resolvedSenderDid
            )

            // Parse decrypted message (verifying any nested signatures)
            verifyAndParsePlainMessage(decryptedJson, resolvedSenderDid)
        } else {
            // Plain message (verified when it carries signatures)
            verifyAndParsePlainMessage(json.jsonObject, senderDid)
        }
    }

    /**
     * Verifies the `signatures` array (when present) and parses the plain message.
     *
     * Unsigned messages (no `signatures` key) are parsed as before — they make no claim.
     * Messages that do carry signatures fail closed on any verification problem.
     */
    private suspend fun verifyAndParsePlainMessage(
        json: JsonObject,
        expectedSenderDid: String?
    ): DidCommMessage {
        val signatures = json["signatures"]
        if (signatures != null) {
            verifySignatures(json, signatures, expectedSenderDid)
        }
        return parseMessage(json)
    }

    private suspend fun verifySignatures(
        json: JsonObject,
        signatures: JsonElement,
        expectedSenderDid: String?
    ) {
        val entries = (signatures as? JsonArray)?.takeIf { it.isNotEmpty() }
            ?: signatureFailure("'signatures' must be a non-empty JSON array")

        // Reconstruct the signed payload: the message without its 'signatures' field,
        // re-encoded with the same compact JSON used at signing time.
        val payloadJson = JsonObject(json.filterKeys { it != "signatures" })
        val payloadBytes = compactJson.encodeToString(JsonObject.serializer(), payloadJson)
            .toByteArray(Charsets.UTF_8)
        val payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
        val fromDid = (json["from"] as? JsonPrimitive)?.contentOrNull

        entries.forEach { entry ->
            verifySignatureEntry(entry, payloadBase64, fromDid, expectedSenderDid)
        }
    }

    private suspend fun verifySignatureEntry(
        entry: JsonElement,
        payloadBase64: String,
        fromDid: String?,
        expectedSenderDid: String?
    ) {
        val obj = entry as? JsonObject
            ?: signatureFailure("Signature entry is not a JSON object")
        val protectedBase64 = (obj["protected"] as? JsonPrimitive)?.contentOrNull
            ?: signatureFailure("Signature entry is missing the 'protected' header")
        val signatureBase64 = (obj["signature"] as? JsonPrimitive)?.contentOrNull
            ?: signatureFailure("Signature entry is missing the 'signature' value")

        val header = try {
            Json.parseToJsonElement(
                String(Base64.getUrlDecoder().decode(protectedBase64), Charsets.UTF_8)
            ).jsonObject
        } catch (e: Exception) {
            signatureFailure("Cannot decode the protected JWS header", e)
        }

        val alg = (header["alg"] as? JsonPrimitive)?.contentOrNull
        if (alg != JWS_ALG_EDDSA) {
            signatureFailure("Unsupported or missing JWS 'alg' ('${alg ?: "absent"}'); only '$JWS_ALG_EDDSA' is accepted")
        }
        val kid = (header["kid"] as? JsonPrimitive)?.contentOrNull
            ?: signatureFailure("Protected JWS header is missing 'kid'")

        val signerDid = kid.substringBefore("#")
        if (expectedSenderDid != null && signerDid != expectedSenderDid) {
            signatureFailure("Signature kid '$kid' does not belong to expected sender '$expectedSenderDid'")
        }
        if (fromDid != null && signerDid != fromDid) {
            signatureFailure("Signature kid '$kid' does not belong to message sender '$fromDid'")
        }

        val signerDoc = resolveDid(signerDid)
            ?: signatureFailure("Cannot resolve signer DID '$signerDid' to verify the message signature")
        val verificationMethod = signerDoc.verificationMethod.firstOrNull {
            normalizeKeyRef(it.id.value, signerDoc.id.value) == normalizeKeyRef(kid, signerDid)
        } ?: signatureFailure("Signer DID document '$signerDid' has no verification method '$kid'")

        val publicKey = extractEd25519PublicKey(verificationMethod)
            ?: signatureFailure("Verification method '$kid' carries no usable Ed25519 public key")

        val signatureBytes = try {
            Base64.getUrlDecoder().decode(signatureBase64)
        } catch (e: IllegalArgumentException) {
            signatureFailure("Signature is not valid base64url", e)
        }
        if (signatureBytes.size != ED25519_SIGNATURE_LENGTH_BYTES) {
            signatureFailure("Invalid Ed25519 signature length: ${signatureBytes.size}")
        }

        val signingInput = "$protectedBase64.$payloadBase64".toByteArray(Charsets.US_ASCII)
        val verified = try {
            Signature.getInstance("Ed25519").run {
                initVerify(publicKey)
                update(signingInput)
                verify(signatureBytes)
            }
        } catch (e: Exception) {
            false
        }
        if (!verified) {
            signatureFailure("Signature verification failed for kid '$kid'")
        }
    }

    private fun signatureFailure(reason: String, cause: Throwable? = null): Nothing =
        throw DidCommException.UnpackingFailed(reason = reason, cause = cause)

    private fun normalizeKeyRef(keyRef: String, did: String): String =
        if (keyRef.startsWith("#")) "$did$keyRef" else keyRef

    /**
     * Extracts an Ed25519 [PublicKey] from a verification method's `publicKeyJwk`
     * (OKP/Ed25519) or `publicKeyMultibase` (base58btc, optionally multicodec-prefixed).
     */
    private fun extractEd25519PublicKey(vm: VerificationMethod): PublicKey? {
        val raw = vm.publicKeyJwk?.let { jwk ->
            val kty = jwk["kty"] as? String
            val crv = jwk["crv"] as? String
            val x = jwk["x"] as? String
            if (kty == "OKP" && crv == "Ed25519" && x != null) {
                try {
                    Base64.getUrlDecoder().decode(x)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } else {
                null
            }
        } ?: vm.publicKeyMultibase?.let { decodeMultibaseEd25519(it, vm.type) }

        return raw?.let { rawEd25519ToPublicKey(it) }
    }

    private fun decodeMultibaseEd25519(multibase: String, vmType: String): ByteArray? {
        if (!multibase.startsWith("z")) return null
        val decoded = try {
            multibase.substring(1).decodeBase58()
        } catch (e: Exception) {
            return null
        }
        return when {
            decoded.size == ED25519_RAW_KEY_LENGTH_BYTES + 2 &&
                decoded[0] == 0xED.toByte() && decoded[1] == 0x01.toByte() ->
                decoded.copyOfRange(2, decoded.size)
            decoded.size == ED25519_RAW_KEY_LENGTH_BYTES &&
                vmType.contains("Ed25519", ignoreCase = true) -> decoded
            else -> null
        }
    }

    private fun rawEd25519ToPublicKey(raw: ByteArray): PublicKey? {
        if (raw.size != ED25519_RAW_KEY_LENGTH_BYTES) return null
        return try {
            KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(ED25519_SPKI_PREFIX + raw))
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEnvelope(json: JsonObject): DidCommEnvelope {
        val protected = json["protected"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'protected' in envelope")

        val recipients = json["recipients"]?.jsonArray?.map { recipientJson ->
            val recipientObj = recipientJson.jsonObject
            val headerObj = recipientObj["header"]?.jsonObject
                ?: throw IllegalArgumentException("Missing 'header' in recipient")

            org.trustweave.credential.didcomm.models.DidCommRecipient(
                header = org.trustweave.credential.didcomm.models.DidCommRecipientHeader(
                    kid = headerObj["kid"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing 'kid' in header"),
                    alg = headerObj["alg"]?.jsonPrimitive?.content ?: "ECDH-1PU+A256KW",
                    epk = headerObj["epk"]?.jsonObject
                ),
                encrypted_key = recipientObj["encrypted_key"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing 'encrypted_key' in recipient")
            )
        } ?: throw IllegalArgumentException("Missing 'recipients' in envelope")

        val iv = json["iv"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'iv' in envelope")
        val ciphertext = json["ciphertext"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'ciphertext' in envelope")
        val tag = json["tag"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'tag' in envelope")

        return DidCommEnvelope(
            protected = protected,
            recipients = recipients,
            iv = iv,
            ciphertext = ciphertext,
            tag = tag
        )
    }

    private fun parseMessage(json: JsonObject): DidCommMessage {
        val id = json["id"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'id' in message")
        val type = json["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'type' in message")

        val from = json["from"]?.jsonPrimitive?.content
        val to = json["to"].asStringList()

        val body = json["body"]?.jsonObject ?: buildJsonObject { }
        val created = json["created_time"]?.jsonPrimitive?.content
        val expiresTime = json["expires_time"]?.jsonPrimitive?.content

        val attachments = json["attachments"]?.jsonArray?.mapNotNull { attachmentJson ->
            val attachmentObj = attachmentJson.jsonObject
            val mediaType = attachmentObj["media_type"]?.jsonPrimitive?.content ?: "application/json"
            val dataObj = attachmentObj["data"]?.jsonObject
                ?: throw IllegalArgumentException("Missing 'data' in attachment")

            org.trustweave.credential.didcomm.models.DidCommAttachment(
                id = attachmentObj["id"]?.jsonPrimitive?.content,
                mediaType = mediaType,
                data = org.trustweave.credential.didcomm.models.DidCommAttachmentData(
                    base64 = dataObj["base64"]?.jsonPrimitive?.content,
                    json = dataObj["json"],
                    links = dataObj["links"].asStringListOrNull(),
                )
            )
        } ?: emptyList()

        val thid = json["thid"]?.jsonPrimitive?.content
        val pthid = json["pthid"]?.jsonPrimitive?.content
        val ack = json["ack"].asStringListOrNull()

        return DidCommMessage(
            id = id,
            type = type,
            from = from,
            to = to,
            body = body,
            created = created,
            expiresTime = expiresTime,
            attachments = attachments,
            thid = thid,
            pthid = pthid,
            ack = ack
        )
    }

    private fun extractSenderDid(envelope: DidCommEnvelope): String? {
        // Extract sender DID from protected headers
        return try {
            val protectedJson = Json.parseToJsonElement(
                String(Base64.getUrlDecoder().decode(envelope.protected))
            ).jsonObject

            val skid = protectedJson["skid"]?.jsonPrimitive?.content
            // Extract DID from key ID (format: did:method:id#key-id)
            skid?.substringBefore("#")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Signs a plain message using JWS (JSON Web Signature).
     *
     * The Ed25519 signature is computed over the JWS signing input
     * `ASCII(BASE64URL(protected) || '.' || BASE64URL(payload))` with `alg: EdDSA`,
     * where the payload is the compact JSON encoding of the message (without `signatures`).
     */
    private suspend fun signMessage(
        messageJson: JsonObject,
        fromDid: String,
        fromKeyId: String
    ): JsonObject {
        val messageBytes = compactJson.encodeToString(JsonObject.serializer(), messageJson).toByteArray(Charsets.UTF_8)

        // Protected JWS header; EdDSA is the JOSE algorithm identifier for Ed25519 (RFC 8037).
        val header = buildJsonObject {
            put("alg", JWS_ALG_EDDSA)
            put("typ", "JWS")
            put("kid", fromKeyId)
        }

        val headerBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(compactJson.encodeToString(JsonObject.serializer(), header).toByteArray(Charsets.UTF_8))
        val payloadBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(messageBytes)

        // Sign the JWS signing input using the provided signer
        val signingInput = "$headerBase64.$payloadBase64".toByteArray(Charsets.US_ASCII)
        val signature = signer(signingInput, fromKeyId)
        val signatureBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(signature)

        // Return signed message with JWS
        return buildJsonObject {
            // Copy all entries from messageJson
            messageJson.forEach { (key, value) ->
                put(key, value)
            }
            putJsonArray("signatures") {
                add(buildJsonObject {
                    put("protected", headerBase64)
                    put("signature", signatureBase64)
                })
            }
        }
    }
}

/**
 * DIDComm allows a string or JSON array of strings. kotlinx.serialization.json 1.9+ throws when
 * [JsonElement.jsonArray] is used on a non-array element, so we branch on the runtime type.
 */
private fun JsonElement?.asStringList(): List<String> =
    when (this) {
        null -> emptyList()
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.content }
        is JsonPrimitive -> listOf(this.content)
        else -> emptyList()
    }

private fun JsonElement?.asStringListOrNull(): List<String>? =
    when (this) {
        null -> null
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.content }
        is JsonPrimitive -> listOf(this.content)
        else -> null
    }

