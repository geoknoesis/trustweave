package org.trustweave.referencewallet.lib

import android.content.Context
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
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
 * Mirrors the TypeScript `reference-wallet/lib/wallet.ts` so the two reference
 * implementations stay structurally aligned. When the Kotlin wallet-core-mp SDK
 * gains the corresponding capability surface, this becomes a thin wrapper.
 */
class Wallet(private val context: Context) {

    private val storage = Storage(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    data class State(val holder: Storage.HolderIdentity, val credentials: List<Storage.StoredCredential>)

    /** Idempotent. First call generates a holder identity; subsequent calls load it. */
    fun bootstrap(): State {
        val existing = storage.loadHolder()
        val holder = existing ?: run {
            val kp = Crypto.generateEd25519()
            val fresh = Storage.HolderIdentity(
                did = Crypto.publicKeyToDidKey(kp.publicKey),
                publicKey = Crypto.b64uEncode(kp.publicKey),
                privateKey = Crypto.b64uEncode(kp.privateKey),
                createdAt = Clock.System.now().toString(),
            )
            storage.saveHolder(fresh)
            fresh
        }
        return State(holder, storage.loadCredentials())
    }

    fun list(): List<Storage.StoredCredential> = storage.loadCredentials()

    fun deleteCredential(id: String) = storage.deleteCredential(id)

    fun reset() = storage.reset()

    /**
     * Store a received VC-JWT. Decodes the unverified payload to extract preview metadata.
     * Issuer signature verification happens at the verifier side; on receipt we trust the
     * issuer claim purely for indexing/display.
     */
    fun store(vcJwt: String): Storage.StoredCredential {
        val payload = decodeJwtPayload(vcJwt)
        val vc = payload["vc"]?.jsonObject
        val issuerDid = (payload["iss"]?.jsonPrimitive?.content
            ?: vc?.get("issuer")?.jsonPrimitive?.content
            ?: "")
        val subjectDid = payload["sub"]?.jsonPrimitive?.content ?: ""
        val types = extractTypes(vc)
        val preview = buildPreview(types, vc)
        val cred = Storage.StoredCredential(
            id = UUID.randomUUID().toString(),
            vcJwt = vcJwt,
            receivedAt = Clock.System.now().toString(),
            issuerDid = issuerDid,
            subjectDid = subjectDid,
            type = types,
            previewTitle = preview.first,
            previewSubtitle = preview.second,
        )
        storage.addCredential(cred)
        return cred
    }

    /**
     * Build a Verifiable Presentation containing one or more credentials, signed by the
     * holder. Returns the compact VP-JWT. Verifier must check both the outer VP signature
     * (proves holder possession) AND each inner VC signature.
     */
    fun createPresentation(
        credentialIds: List<String>,
        verifierAudience: String,
        challenge: String,
    ): String {
        val holder = storage.loadHolder()
            ?: throw IllegalStateException("Wallet not bootstrapped")
        val creds = storage.loadCredentials().filter { it.id in credentialIds }
        require(creds.isNotEmpty()) { "No matching credentials to present" }

        val now = Clock.System.now().epochSeconds
        val vpPayload = buildJsonObject {
            put("iss", holder.did)
            put("sub", holder.did)
            put("aud", verifierAudience)
            put("nonce", challenge)
            put("iat", now)
            put("exp", now + 300)  // 5 minute window
            put("vp", buildJsonObject {
                put("@context", buildJsonArray { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) })
                put("type", buildJsonArray { add(JsonPrimitive("VerifiablePresentation")) })
                put("holder", holder.did)
                put("verifiableCredential", buildJsonArray {
                    creds.forEach { add(JsonPrimitive(it.vcJwt)) }
                })
            })
        }

        val privateKey = Crypto.b64uDecode(holder.privateKey)
        val didTail = holder.did.removePrefix("did:key:")
        return Crypto.signJwsCompact(
            jsonPayload = json.encodeToString(JsonObject.serializer(), vpPayload),
            privateKey = privateKey,
            kid = "${holder.did}#$didTail",
        )
    }

    // ----- internal helpers -----

    private fun decodeJwtPayload(jwt: String): JsonObject {
        val parts = jwt.split(".")
        require(parts.size == 3) { "Not a JWT" }
        val payloadJson = Crypto.b64uDecodeString(parts[1])
        return Json.parseToJsonElement(payloadJson).jsonObject
    }

    private fun extractTypes(vc: JsonObject?): List<String> {
        if (vc == null) return listOf("VerifiableCredential")
        val t = vc["type"]
        return when {
            t == null -> listOf("VerifiableCredential")
            t is kotlinx.serialization.json.JsonArray -> t.map { it.jsonPrimitive.content }
            else -> listOf(t.jsonPrimitive.content)
        }
    }

    /** Returns (title, subtitle). */
    private fun buildPreview(types: List<String>, vc: JsonObject?): Pair<String, String?> {
        val title = types.firstOrNull { it != "VerifiableCredential" } ?: "Credential"
        val subject = vc?.get("credentialSubject")?.jsonObject
        val subtitle = subject?.let {
            it["name"]?.jsonPrimitive?.content
                ?: it["degree"]?.jsonPrimitive?.content
                ?: it["title"]?.jsonPrimitive?.content
        }
        return title to subtitle
    }
}
