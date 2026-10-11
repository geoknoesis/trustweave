package org.trustweave.did.sidetree

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * File-backed [SidetreeKeyStore] that persists each DID's update + recovery
 * keypairs as a JSON file under [directory].
 *
 * Designed for **integration tests** and **local dev environments** that need
 * keys to survive process restarts (e.g. when validating multi-step
 * update/deactivate flows against a long-running Sidetree node, or when seeding
 * a test fixture). It is intentionally simple — one file per DID, no schema
 * versioning, no encryption.
 *
 * ## Production use
 *
 * Not recommended. Private keys (`d` component for EC keys) are written to disk
 * in plaintext. Use a secret store (HashiCorp Vault, AWS Secrets Manager, a
 * sealed-secret JCE keystore, ...) for production deployments. On POSIX
 * platforms the file is created atomically with mode `600` (owner read+write only); on a file
 * system without POSIX permissions (for example Windows) the store refuses to write.
 *
 * ## File layout
 *
 * - One file per DID: `<directory>/<didSuffix>.json`.
 * - Atomic writes via `tmp + ATOMIC_MOVE` so a crash mid-write doesn't corrupt
 *   a previously-stored keypair.
 * - Reads tolerate missing files (return `null`).
 *
 * ## Concurrency
 *
 * Operations on the same DID are serialised by a per-instance [Mutex]. Multiple
 * instances pointed at the same directory are NOT coordinated — fine for tests,
 * not for multi-process production setups.
 */
class FileSidetreeKeyStore(
    private val directory: Path,
) : SidetreeKeyStore {
    private val mutex = Mutex()
    private val json =
        Json {
            encodeDefaults = true
            prettyPrint = false
        }

    init {
        Files.createDirectories(directory)
    }

    override suspend fun put(
        didSuffix: String,
        keys: SidetreeKeyPair,
    ): Unit =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val target = directory.resolve("${sanitize(didSuffix)}.json")
                val payload = json.encodeToString(JsonObject.serializer(), encode(keys))
                // The temp file is created atomically with owner-only permissions under an unpredictable
                // name, so the private keys are never readable by others, even briefly, and a pre-planted
                // file or symlink cannot be written through.
                val tmp = createOwnerOnlyTempFile(sanitize(didSuffix))
                try {
                    Files.writeString(tmp, payload, StandardCharsets.UTF_8)
                    try {
                        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                } finally {
                    Files.deleteIfExists(tmp)
                }
            }
        }

    override suspend fun get(didSuffix: String): SidetreeKeyPair? =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val target = directory.resolve("${sanitize(didSuffix)}.json")
                if (!Files.exists(target)) return@withContext null
                val text = Files.readString(target, StandardCharsets.UTF_8)
                val obj = Json.parseToJsonElement(text).jsonObject
                decode(obj)
            }
        }

    override suspend fun remove(didSuffix: String): Unit =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val target = directory.resolve("${sanitize(didSuffix)}.json")
                Files.deleteIfExists(target)
            }
        }

    /**
     * Empties the store (deletes every persisted keypair under [directory]).
     * Convenience helper for tests; never call from application code.
     */
    suspend fun clear(): Unit =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!Files.exists(directory)) return@withContext
                Files.list(directory).use { stream ->
                    stream
                        .filter { it.fileName.toString().endsWith(".json") }
                        .forEach { Files.deleteIfExists(it) }
                }
            }
        }

    /**
     * DID suffixes are base64url-encoded SHA-256 digests so they only contain
     * `[A-Za-z0-9_-]`. Replace anything else with `_` to keep the on-disk name
     * safe across filesystems even if a caller passes an unexpected string.
     */
    private fun sanitize(suffix: String): String =
        suffix.map { c -> if (c.isLetterOrDigit() || c == '_' || c == '-') c else '_' }.joinToString("")

    private fun encode(keys: SidetreeKeyPair): JsonObject =
        buildJsonObject {
            put("version", 1)
            put("updatePrivateJwk", SidetreeJcs.mapToJsonObject(keys.updatePrivateJwk))
            put("updatePublicJwk", SidetreeJcs.mapToJsonObject(keys.updatePublicJwk))
            put("recoveryPrivateJwk", SidetreeJcs.mapToJsonObject(keys.recoveryPrivateJwk))
            put("recoveryPublicJwk", SidetreeJcs.mapToJsonObject(keys.recoveryPublicJwk))
        }

    private fun decode(obj: JsonObject): SidetreeKeyPair =
        SidetreeKeyPair(
            updatePrivateJwk = jsonObjectToMap(obj.getValue("updatePrivateJwk").jsonObject),
            updatePublicJwk = jsonObjectToMap(obj.getValue("updatePublicJwk").jsonObject),
            recoveryPrivateJwk = jsonObjectToMap(obj.getValue("recoveryPrivateJwk").jsonObject),
            recoveryPublicJwk = jsonObjectToMap(obj.getValue("recoveryPublicJwk").jsonObject),
        )

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> = obj.entries.associate { (k, v) -> k to elementToValue(v) }

    private fun elementToValue(element: JsonElement): Any? =
        when (element) {
            is JsonNull -> null
            is JsonPrimitive ->
                element.contentOrNull
                    ?: element.booleanOrNull
                    ?: element.longOrNull
                    ?: element.doubleOrNull
            is JsonObject -> jsonObjectToMap(element)
            is kotlinx.serialization.json.JsonArray -> element.map { elementToValue(it) }
        }

    /**
     * Fails closed: a file system that cannot express owner-only POSIX permissions (for example NTFS)
     * is refused rather than writing private keys with default permissions.
     */
    private fun createOwnerOnlyTempFile(prefix: String): Path {
        check(directory.fileSystem.supportedFileAttributeViews().contains("posix")) {
            "FileSidetreeKeyStore requires a POSIX file system to guarantee owner-only key files"
        }
        val perms = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        return Files.createTempFile(directory, "$prefix.", ".tmp", PosixFilePermissions.asFileAttribute(perms))
    }
}
