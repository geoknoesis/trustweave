package org.trustweave.referencewallet.lib

import android.content.Context
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Holder-side wallet facade.
 *
 * Phase 2.5b: routing through [HolderKey] means the wallet doesn't know or care
 * whether the holder's private key lives in AndroidKeyStore (API 33+) or in
 * EncryptedSharedPreferences (older). Sign operations delegate to the backend.
 */
class Wallet(private val context: Context) {

    private val storage = Storage(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    data class State(
        val holder: Storage.HolderIdentity,
        val credentials: List<Storage.StoredCredential>,
        /** Where the holder signing key actually lives — "keystore" or "software". */
        val keySource: String,
    )

    fun bootstrap(): State {
        val existing = storage.loadHolder()
        val holder = existing ?: run {
            val generated = HolderKey.generate()
            val identity = Storage.HolderIdentity(
                did = Crypto.publicKeyToDidKey(generated.key.publicKey),
                publicKey = Crypto.b64uEncode(generated.key.publicKey),
                keySource = generated.source,
                softwarePrivateKey = generated.softwarePrivateKey?.let { Crypto.b64uEncode(it) },
                keystoreAlias = generated.keystoreAlias,
                createdAt = Clock.System.now().toString(),
            )
            storage.saveHolder(identity)
            identity
        }
        return State(holder, storage.loadCredentials(), holder.keySource)
    }

    fun list(): List<Storage.StoredCredential> = storage.loadCredentials()
    fun deleteCredential(id: String) = storage.deleteCredential(id)
    fun reset() = storage.reset()

    fun store(
        credential: String,
        format: String,
        selectivelyDisclosable: List<String> = emptyList(),
    ): Storage.StoredCredential {
        val meta = when (format) {
            "vc+sd-jwt" -> extractSdJwtMeta(credential)
            else -> extractVcJwtMeta(credential)
        }
        val cred = Storage.StoredCredential(
            id = UUID.randomUUID().toString(),
            format = format,
            credential = credential,
            receivedAt = Clock.System.now().toString(),
            issuerDid = meta.issuerDid,
            subjectDid = meta.subjectDid,
            type = meta.types,
            previewTitle = meta.title,
            previewSubtitle = meta.subtitle,
            selectivelyDisclosable = selectivelyDisclosable,
        )
        storage.addCredential(cred)
        return cred
    }

    fun createPresentation(
        credentialIds: List<String>,
        verifierAudience: String,
        challenge: String,
        disclose: Set<String> = emptySet(),
    ): String {
        val holderIdentity = storage.loadHolder() ?: throw IllegalStateException("Wallet not bootstrapped")
        val creds = storage.loadCredentials().filter { it.id in credentialIds }
        require(creds.isNotEmpty()) { "No matching credentials to present" }
        val holderKey = restoreHolderKey(holderIdentity)
        val now = Clock.System.now().epochSeconds

        // SD-JWT VC (single credential).
        if (creds.size == 1 && creds[0].format == "vc+sd-jwt") {
            return SdJwt.present(
                sdJwtVc = creds[0].credential,
                selectDisclose = disclose,
                holderSigner = holderKey::sign,
                holderDid = holderIdentity.did,
                audience = verifierAudience,
                nonce = challenge,
                now = now,
            )
        }

        // Legacy VP-JWT path.
        val vpPayload = buildJsonObject {
            put("iss", holderIdentity.did)
            put("sub", holderIdentity.did)
            put("aud", verifierAudience)
            put("nonce", challenge)
            put("iat", now)
            put("exp", now + 300)
            put("vp", buildJsonObject {
                put("@context", buildJsonArray { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) })
                put("type", buildJsonArray { add(JsonPrimitive("VerifiablePresentation")) })
                put("holder", holderIdentity.did)
                put("verifiableCredential", buildJsonArray {
                    creds.forEach { add(JsonPrimitive(it.credential)) }
                })
            })
        }
        val didTail = holderIdentity.did.removePrefix("did:key:")
        return Crypto.signJwsCompact(
            jsonPayload = json.encodeToString(JsonObject.serializer(), vpPayload),
            kid = "${holderIdentity.did}#$didTail",
            signer = holderKey::sign,
        )
    }

    // ----- internal helpers -----

    /** Reconstruct a HolderKey from stored metadata. */
    private fun restoreHolderKey(id: Storage.HolderIdentity): HolderKey {
        val publicKey = Crypto.b64uDecode(id.publicKey)
        return when (id.keySource) {
            "keystore" -> {
                val alias = id.keystoreAlias
                    ?: throw IllegalStateException("keystore key source missing alias")
                HolderKey.restoreKeystore(publicKey, alias)
            }
            else -> {  // "software" or absent → software
                val priv = id.softwarePrivateKey
                    ?: throw IllegalStateException("software key source missing private key")
                HolderKey.restoreSoftware(publicKey, Crypto.b64uDecode(priv))
            }
        }
    }

    private data class Meta(val issuerDid: String, val subjectDid: String, val types: List<String>, val title: String, val subtitle: String?)

    private fun extractVcJwtMeta(vcJwt: String): Meta {
        val parts = vcJwt.split(".")
        require(parts.size == 3) { "Not a JWT" }
        val payload = Json.parseToJsonElement(Crypto.b64uDecodeString(parts[1])).jsonObject
        val vc = payload["vc"]?.jsonObject
        val issuerDid = payload["iss"]?.jsonPrimitive?.content
            ?: vc?.get("issuer")?.jsonPrimitive?.content ?: ""
        val subjectDid = payload["sub"]?.jsonPrimitive?.content ?: ""
        val types = extractTypes(vc)
        val title = types.firstOrNull { it != "VerifiableCredential" } ?: "Credential"
        val subject = vc?.get("credentialSubject")?.jsonObject
        val subtitle = subject?.let {
            it["name"]?.jsonPrimitive?.content
                ?: it["degree"]?.jsonPrimitive?.content
                ?: it["title"]?.jsonPrimitive?.content
        }
        return Meta(issuerDid, subjectDid, types, title, subtitle)
    }

    private fun extractSdJwtMeta(sdJwtVc: String): Meta {
        val decoded = SdJwt.decode(sdJwtVc)
        val issuerDid = decoded.issuerPayload["iss"]?.jsonPrimitive?.content ?: ""
        val subjectDid = decoded.issuerPayload["sub"]?.jsonPrimitive?.content ?: ""
        val vct = decoded.issuerPayload["vct"]?.jsonPrimitive?.content ?: "Credential"
        val subtitle = decoded.disclosures
            .firstOrNull { it.name in listOf("name", "degree", "title") }
            ?.value?.jsonPrimitive?.content
        return Meta(issuerDid, subjectDid, listOf(vct), vct, subtitle)
    }

    private fun extractTypes(vc: JsonObject?): List<String> {
        if (vc == null) return listOf("VerifiableCredential")
        val t = vc["type"] ?: return listOf("VerifiableCredential")
        return when (t) {
            is kotlinx.serialization.json.JsonArray -> t.map { it.jsonPrimitive.content }
            is JsonElement -> listOf(t.jsonPrimitive.content)
        }
    }
}
