package org.trustweave.referencewallet.lib

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Encrypted local storage for the wallet — holder identity, credentials,
 * trusted issuers, activity history, and preferences.
 *
 * Additive v2 schema: all new fields carry safe defaults so existing wallets upgrade smoothly.
 */
class Storage(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "trustweave-wallet",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        val existingVersion = prefs.getString(KEY_SCHEMA_VERSION, null)
        if (existingVersion == null) {
            prefs.edit().putString(KEY_SCHEMA_VERSION, CURRENT_VERSION.toString()).apply()
        } else if (existingVersion.toIntOrNull() != CURRENT_VERSION) {
            prefs.edit().clear().putString(KEY_SCHEMA_VERSION, CURRENT_VERSION.toString()).apply()
        }
    }

    @Serializable
    data class HolderIdentity(
        val did: String,
        val publicKey: String,
        /** "software" (API 29-32) or "keystore" (API 33+ Keystore-bound). */
        val keySource: String = "software",
        val softwarePrivateKey: String? = null,
        val keystoreAlias: String? = null,
        val createdAt: String,
    )

    @Serializable
    data class StoredCredential(
        val id: String,
        val format: String,
        val credential: String,
        val receivedAt: String,
        val issuerDid: String,
        val subjectDid: String,
        val type: List<String>,
        val previewTitle: String,
        val previewSubtitle: String? = null,
        val selectivelyDisclosable: List<String> = emptyList(),
        /** "configured", "backend", "confirmed", or "unverified". */
        val trustSource: String = "confirmed",
        val issuerName: String? = null,
    )

    @Serializable
    data class ActivityEntry(
        val id: String,
        val timestamp: String,
        /** "added", "shared", "removed", "trusted_issuer", "backup", "restored". */
        val kind: String,
        val title: String,
        val subtitle: String? = null,
        val counterparty: String? = null,
        val detailsShared: Int? = null,
        val totalDetails: Int? = null,
        val success: Boolean = true,
    )

    @Serializable
    data class WalletPreferences(
        val onboardingComplete: Boolean = false,
        val biometricLockEnabled: Boolean = true,
        val developerMode: Boolean = false,
        val useDynamicColor: Boolean = false,
    )

    fun loadHolder(): HolderIdentity? =
        prefs.getString(KEY_HOLDER, null)?.let { json.decodeFromString(HolderIdentity.serializer(), it) }

    fun saveHolder(holder: HolderIdentity) {
        prefs.edit().putString(KEY_HOLDER, json.encodeToString(HolderIdentity.serializer(), holder)).apply()
    }

    fun loadCredentials(): List<StoredCredential> =
        prefs.getString(KEY_CREDENTIALS, null)?.let {
            json.decodeFromString(ListSerializer(StoredCredential.serializer()), it)
        } ?: emptyList()

    fun saveCredentials(creds: List<StoredCredential>) {
        prefs.edit().putString(
            KEY_CREDENTIALS,
            json.encodeToString(ListSerializer(StoredCredential.serializer()), creds),
        ).apply()
    }

    /** Issuers the user explicitly confirmed. Sanitised on every read (shape, duplicates, size). */
    fun loadAcceptedIssuers(): List<String> =
        try {
            prefs.getString(KEY_ACCEPTED_ISSUERS, null)
                ?.let { json.decodeFromString(ListSerializer(serializer<String>()), it) }
                ?.let(IssuerTrust::sanitize)
                ?: emptyList()
        } catch (_: kotlinx.serialization.SerializationException) {
            emptyList()
        }

    /** Persist an issuer only after an explicit user confirmation. */
    fun addAcceptedIssuer(did: String) = saveAcceptedIssuers(IssuerTrust.withAccepted(loadAcceptedIssuers(), did))

    fun removeAcceptedIssuer(did: String) = saveAcceptedIssuers(loadAcceptedIssuers().filterNot { it == did })

    private fun saveAcceptedIssuers(issuers: List<String>) {
        prefs.edit().putString(
            KEY_ACCEPTED_ISSUERS,
            json.encodeToString(ListSerializer(serializer<String>()), issuers),
        ).apply()
    }

    fun loadActivity(): List<ActivityEntry> =
        try {
            prefs.getString(KEY_ACTIVITY, null)?.let {
                json.decodeFromString(ListSerializer(ActivityEntry.serializer()), it)
            } ?: emptyList()
        } catch (_: kotlinx.serialization.SerializationException) {
            emptyList()
        }

    fun appendActivity(entry: ActivityEntry) {
        val next = (listOf(entry) + loadActivity()).take(MAX_ACTIVITY)
        prefs.edit().putString(
            KEY_ACTIVITY,
            json.encodeToString(ListSerializer(ActivityEntry.serializer()), next),
        ).apply()
    }

    fun clearActivity() {
        prefs.edit().remove(KEY_ACTIVITY).apply()
    }

    fun loadPreferences(): WalletPreferences =
        try {
            prefs.getString(KEY_PREFS, null)?.let {
                json.decodeFromString(WalletPreferences.serializer(), it)
            } ?: WalletPreferences()
        } catch (_: kotlinx.serialization.SerializationException) {
            WalletPreferences()
        }

    fun savePreferences(preferences: WalletPreferences) {
        prefs.edit().putString(
            KEY_PREFS,
            json.encodeToString(WalletPreferences.serializer(), preferences),
        ).apply()
    }

    fun addCredential(cred: StoredCredential) { saveCredentials(loadCredentials() + cred) }
    fun deleteCredential(id: String) { saveCredentials(loadCredentials().filterNot { it.id == id }) }
    fun reset() { prefs.edit().clear().putString(KEY_SCHEMA_VERSION, CURRENT_VERSION.toString()).apply() }

    companion object {
        private const val KEY_HOLDER = "holder"
        private const val KEY_CREDENTIALS = "credentials"
        private const val KEY_ACCEPTED_ISSUERS = "accepted-issuers"
        private const val KEY_ACTIVITY = "activity-v1"
        private const val KEY_PREFS = "prefs-v1"
        private const val KEY_SCHEMA_VERSION = "schema-version"
        private const val CURRENT_VERSION = 2
        private const val MAX_ACTIVITY = 100
    }
}
