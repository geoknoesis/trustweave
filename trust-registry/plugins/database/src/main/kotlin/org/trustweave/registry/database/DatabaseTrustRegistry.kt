package org.trustweave.registry.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.trustweave.registry.*
import java.sql.Timestamp
import javax.sql.DataSource

class DatabaseTrustRegistry(private val dataSource: DataSource) : TrustRegistry {

    private val json = Json { ignoreUnknownKeys = true }

    init { initializeSchema() }

    private fun initializeSchema() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS registry_issuers (
                        did VARCHAR(512) PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        description TEXT,
                        credential_types TEXT NOT NULL DEFAULT '[]',
                        service_endpoint VARCHAR(1024),
                        status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                        registered_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        metadata TEXT NOT NULL DEFAULT '{}'
                    )
                    """.trimIndent()
                ).execute()
                conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS registry_verifiers (
                        did VARCHAR(512) PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        description TEXT,
                        service_endpoint VARCHAR(1024),
                        status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                        registered_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        metadata TEXT NOT NULL DEFAULT '{}'
                    )
                    """.trimIndent()
                ).execute()
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw RuntimeException("Failed to initialise trust registry schema: ${e.message}", e)
            }
        }
    }

    override suspend fun registerIssuer(registration: IssuerRegistration): IssuerRecord = withContext(Dispatchers.IO) {
        val now = Clock.System.now()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO registry_issuers (did,name,description,credential_types,service_endpoint,status,registered_at,updated_at,metadata) VALUES (?,?,?,?,?,?,?,?,?)"
            ).apply {
                setString(1, registration.did); setString(2, registration.name)
                setString(3, registration.description)
                setString(4, json.encodeToString(registration.credentialTypes))
                setString(5, registration.serviceEndpoint)
                setString(6, AccreditationStatus.ACTIVE.name)
                setTimestamp(7, Timestamp(now.toEpochMilliseconds()))
                setTimestamp(8, Timestamp(now.toEpochMilliseconds()))
                setString(9, json.encodeToString(registration.metadata))
            }.executeUpdate()
        }
        IssuerRecord(
            did = registration.did, name = registration.name, description = registration.description,
            credentialTypes = registration.credentialTypes, serviceEndpoint = registration.serviceEndpoint,
            status = AccreditationStatus.ACTIVE, registeredAt = now, updatedAt = now, metadata = registration.metadata,
        )
    }

    override suspend fun getIssuer(did: String): IssuerRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM registry_issuers WHERE did = ?")
                .apply { setString(1, did) }.executeQuery()
                .let { rs -> if (rs.next()) rowToIssuer(rs) else null }
        }
    }

    override suspend fun listIssuers(filter: RegistryFilter): List<IssuerRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM registry_issuers").executeQuery().let { rs ->
                val results = mutableListOf<IssuerRecord>()
                while (rs.next()) results.add(rowToIssuer(rs))
                results
            }.filter { r ->
                (filter.status == null || r.status == filter.status) &&
                (filter.credentialType == null || r.credentialTypes.contains(filter.credentialType)) &&
                (filter.nameContains == null || r.name.contains(filter.nameContains!!, ignoreCase = true))
            }
        }
    }

    override suspend fun updateIssuer(did: String, update: IssuerUpdate): IssuerRecord = withContext(Dispatchers.IO) {
        val existing = getIssuer(did) ?: throw NoSuchElementException("Issuer not found: $did")
        val now = Clock.System.now()
        val updated = existing.copy(
            name = update.name ?: existing.name,
            description = update.description ?: existing.description,
            credentialTypes = update.credentialTypes ?: existing.credentialTypes,
            serviceEndpoint = update.serviceEndpoint ?: existing.serviceEndpoint,
            metadata = update.metadata ?: existing.metadata,
            updatedAt = now,
        )
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE registry_issuers SET name=?,description=?,credential_types=?,service_endpoint=?,metadata=?,updated_at=? WHERE did=?"
            ).apply {
                setString(1, updated.name); setString(2, updated.description)
                setString(3, json.encodeToString(updated.credentialTypes))
                setString(4, updated.serviceEndpoint)
                setString(5, json.encodeToString(updated.metadata))
                setTimestamp(6, Timestamp(now.toEpochMilliseconds())); setString(7, did)
            }.executeUpdate()
        }
        updated
    }

    override suspend fun revokeIssuer(did: String, reason: String?): Boolean = withContext(Dispatchers.IO) {
        updateIssuerStatus(did, AccreditationStatus.REVOKED)
    }

    override suspend fun activateIssuer(did: String): Boolean = withContext(Dispatchers.IO) {
        updateIssuerStatus(did, AccreditationStatus.ACTIVE)
    }

    override suspend fun registerVerifier(registration: VerifierRegistration): VerifierRecord = withContext(Dispatchers.IO) {
        val now = Clock.System.now()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO registry_verifiers (did,name,description,service_endpoint,status,registered_at,updated_at,metadata) VALUES (?,?,?,?,?,?,?,?)"
            ).apply {
                setString(1, registration.did); setString(2, registration.name)
                setString(3, registration.description); setString(4, registration.serviceEndpoint)
                setString(5, AccreditationStatus.ACTIVE.name)
                setTimestamp(6, Timestamp(now.toEpochMilliseconds()))
                setTimestamp(7, Timestamp(now.toEpochMilliseconds()))
                setString(8, json.encodeToString(registration.metadata))
            }.executeUpdate()
        }
        VerifierRecord(
            did = registration.did, name = registration.name, description = registration.description,
            serviceEndpoint = registration.serviceEndpoint, status = AccreditationStatus.ACTIVE,
            registeredAt = now, updatedAt = now, metadata = registration.metadata,
        )
    }

    override suspend fun getVerifier(did: String): VerifierRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM registry_verifiers WHERE did = ?")
                .apply { setString(1, did) }.executeQuery()
                .let { rs -> if (rs.next()) rowToVerifier(rs) else null }
        }
    }

    override suspend fun listVerifiers(filter: RegistryFilter): List<VerifierRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM registry_verifiers").executeQuery().let { rs ->
                val results = mutableListOf<VerifierRecord>()
                while (rs.next()) results.add(rowToVerifier(rs))
                results
            }.filter { r ->
                (filter.status == null || r.status == filter.status) &&
                (filter.nameContains == null || r.name.contains(filter.nameContains!!, ignoreCase = true))
            }
        }
    }

    override suspend fun updateVerifier(did: String, update: VerifierUpdate): VerifierRecord = withContext(Dispatchers.IO) {
        val existing = getVerifier(did) ?: throw NoSuchElementException("Verifier not found: $did")
        val now = Clock.System.now()
        val updated = existing.copy(
            name = update.name ?: existing.name,
            description = update.description ?: existing.description,
            serviceEndpoint = update.serviceEndpoint ?: existing.serviceEndpoint,
            metadata = update.metadata ?: existing.metadata,
            updatedAt = now,
        )
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE registry_verifiers SET name=?,description=?,service_endpoint=?,metadata=?,updated_at=? WHERE did=?"
            ).apply {
                setString(1, updated.name); setString(2, updated.description)
                setString(3, updated.serviceEndpoint)
                setString(4, json.encodeToString(updated.metadata))
                setTimestamp(5, Timestamp(now.toEpochMilliseconds())); setString(6, did)
            }.executeUpdate()
        }
        updated
    }

    override suspend fun revokeVerifier(did: String, reason: String?): Boolean = withContext(Dispatchers.IO) {
        updateVerifierStatus(did, AccreditationStatus.REVOKED)
    }

    override suspend fun activateVerifier(did: String): Boolean = withContext(Dispatchers.IO) {
        updateVerifierStatus(did, AccreditationStatus.ACTIVE)
    }

    override suspend fun getAccreditationStatus(did: String): AccreditationStatus =
        getIssuer(did)?.status ?: getVerifier(did)?.status ?: AccreditationStatus.UNKNOWN

    override suspend fun listCredentialTypes(): List<String> =
        listIssuers().flatMap { it.credentialTypes }.distinct().sorted()

    private fun updateIssuerStatus(did: String, status: AccreditationStatus): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE registry_issuers SET status=?,updated_at=? WHERE did=?").apply {
                setString(1, status.name)
                setTimestamp(2, Timestamp(Clock.System.now().toEpochMilliseconds()))
                setString(3, did)
            }.executeUpdate() > 0
        }

    private fun updateVerifierStatus(did: String, status: AccreditationStatus): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE registry_verifiers SET status=?,updated_at=? WHERE did=?").apply {
                setString(1, status.name)
                setTimestamp(2, Timestamp(Clock.System.now().toEpochMilliseconds()))
                setString(3, did)
            }.executeUpdate() > 0
        }

    private fun rowToIssuer(rs: java.sql.ResultSet): IssuerRecord = IssuerRecord(
        did = rs.getString("did"), name = rs.getString("name"),
        description = rs.getString("description"),
        credentialTypes = json.decodeFromString(rs.getString("credential_types")),
        serviceEndpoint = rs.getString("service_endpoint"),
        status = AccreditationStatus.valueOf(rs.getString("status")),
        registeredAt = rs.getTimestamp("registered_at").toInstant().toKotlinInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant().toKotlinInstant(),
        metadata = json.decodeFromString(rs.getString("metadata")),
    )

    private fun rowToVerifier(rs: java.sql.ResultSet): VerifierRecord = VerifierRecord(
        did = rs.getString("did"), name = rs.getString("name"),
        description = rs.getString("description"),
        serviceEndpoint = rs.getString("service_endpoint"),
        status = AccreditationStatus.valueOf(rs.getString("status")),
        registeredAt = rs.getTimestamp("registered_at").toInstant().toKotlinInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant().toKotlinInstant(),
        metadata = json.decodeFromString(rs.getString("metadata")),
    )
}
