package org.trustweave.wallet.database

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.CredentialFilter
import org.trustweave.wallet.StoredCredentialStatus
import org.trustweave.wallet.WalletStatusResolver
import org.trustweave.wallet.resolveStoredStatus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

// Row <-> model mapping helpers of [DatabaseWallet]. Internal; behaviour identical to the
// members they were extracted from.

/** Converts the `credential_metadata.metadata` JSON column to and from a map. */
internal class MetadataCodec(
    private val json: Json,
) {
    /** Serialize a metadata map to JSON. Non-primitive values are stored via toString(). */
    fun toJson(metadata: Map<String, Any>): String {
        val obj =
            buildJsonObject {
                metadata.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Boolean -> put(key, value)
                        is Number -> put(key, JsonPrimitive(value))
                        is JsonElement -> put(key, value)
                        else -> put(key, value.toString())
                    }
                }
            }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    /** Deserialize a metadata JSON column back into a map of primitives. */
    fun toMap(metadataJson: String?): Map<String, Any> {
        if (metadataJson.isNullOrBlank()) return emptyMap()
        val obj = json.decodeFromString(JsonObject.serializer(), metadataJson)
        return obj.mapValues { (_, value) ->
            when {
                value is JsonPrimitive && value.isString -> value.content
                value is JsonPrimitive && value.booleanOrNull != null -> value.booleanOrNull as Any
                value is JsonPrimitive && value.longOrNull != null -> value.longOrNull as Any
                value is JsonPrimitive && value.doubleOrNull != null -> value.doubleOrNull as Any
                else -> value
            }
        }
    }
}

internal fun java.sql.ResultSet.instantOrNow(column: String): Instant =
    getTimestamp(column)?.toInstant()?.toKotlinInstant() ?: Clock.System.now()

/** Expiry that applies to [credential]: VC 2.0 `validUntil`, else VC 1.1 `expirationDate`. */
internal fun effectiveExpiry(credential: VerifiableCredential): Instant? =
    if (credential.isVc2 && !credential.isVc1) {
        credential.validUntil
    } else {
        credential.validUntil ?: credential.expirationDate
    }

/** Whether [credential] matches [filter]; revocation is resolved through [statusResolver]. */
internal suspend fun matchesFilter(
    credential: VerifiableCredential,
    filter: CredentialFilter,
    statusResolver: WalletStatusResolver?,
): Boolean {
    if (filter.issuer != null && credential.issuer.id.value != filter.issuer) return false
    filter.type?.let { filterTypes ->
        if (!filterTypes.any { filterType -> credential.type.any { it.value == filterType } }) return false
    }
    if (filter.subjectId != null) {
        val subjectId = credential.credentialSubject.id?.value
        if (subjectId != filter.subjectId) return false
    }
    if (filter.expired != null) {
        val isExpired = effectiveExpiry(credential)?.let { Clock.System.now() > it } ?: false
        if (isExpired != filter.expired) return false
    }
    if (filter.hasStatusEntry != null && (credential.credentialStatus != null) != filter.hasStatusEntry) return false
    if (filter.revoked != null) {
        val status = resolveStoredStatus(credential, statusResolver)
        if (status == StoredCredentialStatus.UNKNOWN) return false
        if ((status == StoredCredentialStatus.REVOKED) != filter.revoked) return false
    }
    return true
}
