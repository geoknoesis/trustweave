package org.trustweave.referencewallet.lib

import android.content.Context
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.referencewallet.BuildConfig

/**
 * Holder-side wallet facade with fail-closed verification, issuer trust management,
 * activity history, preferences, and credential-only backup/restore.
 */
class Wallet(
    private val context: Context,
    private val trustedIssuers: Set<String> = IssuerTrust.parseConfigured(BuildConfig.TRUSTED_ISSUERS),
) {

    private val storage = Storage(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val prettyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    data class State(
        val holder: Storage.HolderIdentity,
        val credentials: List<Storage.StoredCredential>,
        /** Where the holder signing key actually lives — "keystore" or "software". */
        val keySource: String,
    )

    @Serializable
    data class BackupItem(
        val format: String,
        val credential: String,
        val selectivelyDisclosable: List<String> = emptyList(),
    )

    @Serializable
    data class CredentialBackupPayload(
        val schema: String = "trustweave-credential-backup-v1",
        val exportedAt: String,
        val holderDid: String,
        val containsPrivateKeys: Boolean = false,
        val credentials: List<BackupItem>,
    )

    data class RestoreResult(
        val restored: Int,
        val skipped: Int,
        val untrustedIssuers: List<String>,
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

    fun deleteCredential(id: String) {
        val target = storage.loadCredentials().firstOrNull { it.id == id }
        storage.deleteCredential(id)
        if (target != null) {
            recordActivity(
                kind = "removed",
                title = target.previewTitle,
                subtitle = target.previewSubtitle,
                success = true,
            )
        }
    }

    fun reset() = storage.reset()

    fun configuredIssuers(): Set<String> = trustedIssuers

    /** Issuers the user confirmed earlier (removable); configured and backend issuers are not listed. */
    fun acceptedIssuers(): List<String> = storage.loadAcceptedIssuers()
    fun removeAcceptedIssuer(did: String) = storage.removeAcceptedIssuer(did)

    fun loadActivity(): List<Storage.ActivityEntry> = storage.loadActivity()
    fun clearActivity() = storage.clearActivity()

    fun recordActivity(
        kind: String,
        title: String,
        subtitle: String? = null,
        counterparty: String? = null,
        detailsShared: Int? = null,
        totalDetails: Int? = null,
        success: Boolean = true,
    ) {
        storage.appendActivity(
            Storage.ActivityEntry(
                id = UUID.randomUUID().toString(),
                timestamp = Clock.System.now().toString(),
                kind = kind,
                title = title,
                subtitle = subtitle,
                counterparty = counterparty,
                detailsShared = detailsShared,
                totalDetails = totalDetails,
                success = success,
            ),
        )
    }

    fun loadPreferences(): Storage.WalletPreferences = storage.loadPreferences()
    fun savePreferences(preferences: Storage.WalletPreferences) = storage.savePreferences(preferences)

    /**
     * Verify and store a received credential.
     * Fails closed unless the issuer is configured, in [backendIssuers], previously accepted, or
     * explicitly passed as [confirmedIssuer].
     */
    fun store(
        credential: String,
        format: String,
        selectivelyDisclosable: List<String> = emptyList(),
        backendIssuers: Set<String> = emptySet(),
        confirmedIssuer: String? = null,
    ): Storage.StoredCredential {
        val holder = storage.loadHolder() ?: throw IllegalStateException("Wallet not bootstrapped")
        val existingAccepted = storage.loadAcceptedIssuers()
        CredentialVerification.verifyImportedCredential(
            compact = credential,
            format = format,
            holderDid = holder.did,
            nowEpochSeconds = Clock.System.now().epochSeconds,
            issuerPolicy = IssuerTrust.policy(trustedIssuers, backendIssuers, existingAccepted, confirmedIssuer),
        )
        val meta = when (format) {
            "vc+sd-jwt" -> extractSdJwtMeta(credential)
            else -> extractVcJwtMeta(credential)
        }
        val trustSource = when {
            meta.issuerDid in trustedIssuers -> "configured"
            meta.issuerDid in backendIssuers -> "backend"
            else -> "confirmed"
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
            trustSource = trustSource,
        )
        storage.addCredential(cred)
        // Trust is persisted only now: the user confirmed this issuer AND the credential passed every check.
        if (confirmedIssuer != null && confirmedIssuer == meta.issuerDid &&
            !IssuerTrust.policy(trustedIssuers, backendIssuers, existingAccepted).isTrusted(confirmedIssuer)
        ) {
            storage.addAcceptedIssuer(confirmedIssuer)
            recordActivity(
                kind = "trusted_issuer",
                title = "Trusted new issuer",
                subtitle = meta.title,
                counterparty = meta.issuerDid,
                success = true,
            )
        }
        recordActivity(
            kind = "added",
            title = meta.title,
            subtitle = meta.subtitle,
            counterparty = meta.issuerDid,
            success = true,
        )
        return cred
    }

    /** Export credential records ONLY — never includes private keys. */
    fun exportCredentialBackup(): String {
        val state = bootstrap()
        val payload = CredentialBackupPayload(
            exportedAt = Clock.System.now().toString(),
            holderDid = state.holder.did,
            containsPrivateKeys = false,
            credentials = state.credentials.map {
                BackupItem(
                    format = it.format,
                    credential = it.credential,
                    selectivelyDisclosable = it.selectivelyDisclosable,
                )
            },
        )
        recordActivity(
            kind = "backup",
            title = "Exported credential backup",
            subtitle = "${payload.credentials.size} credential(s) · no private keys included",
            success = true,
        )
        return prettyJson.encodeToString(CredentialBackupPayload.serializer(), payload)
    }

    /** Verify and restore credentials from a credential-only backup. */
    fun restoreCredentialBackup(
        rawJson: String,
        backendIssuers: Set<String> = emptySet(),
        confirmedIssuer: String? = null,
    ): RestoreResult {
        val state = bootstrap()
        val payload = try {
            json.decodeFromString(CredentialBackupPayload.serializer(), rawJson)
        } catch (_: Exception) {
            throw IllegalArgumentException("Backup text is not valid JSON.")
        }
        require(payload.schema == "trustweave-credential-backup-v1") { "Unrecognized backup format." }
        if (payload.holderDid != state.holder.did) {
            throw IllegalArgumentException(
                "This backup belongs to a different wallet key. Because backups never contain private keys, ask your issuers to reissue credentials for this device.",
            )
        }

        var restored = 0
        var skipped = 0
        val untrusted = linkedSetOf<String>()
        for (item in payload.credentials) {
            try {
                store(
                    credential = item.credential,
                    format = item.format,
                    selectivelyDisclosable = item.selectivelyDisclosable,
                    backendIssuers = backendIssuers,
                    confirmedIssuer = confirmedIssuer,
                )
                restored += 1
            } catch (e: UntrustedIssuerException) {
                untrusted.add(e.issuerDid)
                skipped += 1
            } catch (_: Exception) {
                skipped += 1
            }
        }
        if (restored > 0) {
            recordActivity(
                kind = "restored",
                title = "Restored credential backup",
                subtitle = "$restored credential(s) verified and restored",
                success = true,
            )
        }
        return RestoreResult(restored = restored, skipped = skipped, untrustedIssuers = untrusted.toList())
    }

    fun createPresentation(
        credentialIds: List<String>,
        verifierAudience: String,
        challenge: String,
        disclose: Set<String> = emptySet(),
        backendIssuers: Set<String> = emptySet(),
    ): String {
        val holderIdentity = storage.loadHolder() ?: throw IllegalStateException("Wallet not bootstrapped")
        val creds = storage.loadCredentials().filter { it.id in credentialIds }
        require(creds.isNotEmpty()) { "No matching credentials to present" }

        // Enforce removed-issuer policy: if user removed an accepted issuer, block presentation.
        val policy = IssuerTrust.policy(trustedIssuers, backendIssuers, storage.loadAcceptedIssuers())
        for (cred in creds) {
            val trusted = policy.isTrusted(cred.issuerDid) ||
                (cred.trustSource == "backend" && backendIssuers.isEmpty())
            require(trusted) {
                "This credential's issuer was removed from your trusted issuers list and can no longer be shared."
            }
        }

        val holderKey = restoreHolderKey(holderIdentity)
        val now = Clock.System.now().epochSeconds

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

    private fun restoreHolderKey(id: Storage.HolderIdentity): HolderKey {
        val publicKey = Crypto.b64uDecode(id.publicKey)
        return when (id.keySource) {
            "keystore" -> {
                val alias = id.keystoreAlias
                    ?: throw IllegalStateException("keystore key source missing alias")
                HolderKey.restoreKeystore(publicKey, alias)
            }
            else -> {
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
