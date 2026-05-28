package org.trustweave.registry

import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

class InMemoryTrustRegistry : TrustRegistry {
    private val issuers = ConcurrentHashMap<String, IssuerRecord>()
    private val verifiers = ConcurrentHashMap<String, VerifierRecord>()

    override suspend fun registerIssuer(registration: IssuerRegistration): IssuerRecord {
        val now = Clock.System.now()
        val record = IssuerRecord(
            did = registration.did,
            name = registration.name,
            description = registration.description,
            credentialTypes = registration.credentialTypes,
            serviceEndpoint = registration.serviceEndpoint,
            status = AccreditationStatus.ACTIVE,
            registeredAt = now,
            updatedAt = now,
            metadata = registration.metadata,
        )
        issuers[registration.did] = record
        return record
    }

    override suspend fun getIssuer(did: String): IssuerRecord? = issuers[did]

    override suspend fun listIssuers(filter: RegistryFilter): List<IssuerRecord> =
        issuers.values.filter { record ->
            (filter.status == null || record.status == filter.status) &&
            (filter.credentialType == null || record.credentialTypes.contains(filter.credentialType)) &&
            (filter.nameContains == null || record.name.contains(filter.nameContains, ignoreCase = true))
        }

    override suspend fun updateIssuer(did: String, update: IssuerUpdate): IssuerRecord {
        val existing = issuers[did] ?: throw NoSuchElementException("Issuer not found: $did")
        val updated = existing.copy(
            name = update.name ?: existing.name,
            description = update.description ?: existing.description,
            credentialTypes = update.credentialTypes ?: existing.credentialTypes,
            serviceEndpoint = update.serviceEndpoint ?: existing.serviceEndpoint,
            metadata = update.metadata ?: existing.metadata,
            updatedAt = Clock.System.now(),
        )
        issuers[did] = updated
        return updated
    }

    override suspend fun revokeIssuer(did: String, reason: String?): Boolean {
        val record = issuers[did] ?: return false
        issuers[did] = record.copy(status = AccreditationStatus.REVOKED, updatedAt = Clock.System.now())
        return true
    }

    override suspend fun activateIssuer(did: String): Boolean {
        val record = issuers[did] ?: return false
        issuers[did] = record.copy(status = AccreditationStatus.ACTIVE, updatedAt = Clock.System.now())
        return true
    }

    override suspend fun registerVerifier(registration: VerifierRegistration): VerifierRecord {
        val now = Clock.System.now()
        val record = VerifierRecord(
            did = registration.did,
            name = registration.name,
            description = registration.description,
            serviceEndpoint = registration.serviceEndpoint,
            status = AccreditationStatus.ACTIVE,
            registeredAt = now,
            updatedAt = now,
            metadata = registration.metadata,
        )
        verifiers[registration.did] = record
        return record
    }

    override suspend fun getVerifier(did: String): VerifierRecord? = verifiers[did]

    override suspend fun listVerifiers(filter: RegistryFilter): List<VerifierRecord> =
        verifiers.values.filter { record ->
            (filter.status == null || record.status == filter.status) &&
            (filter.nameContains == null || record.name.contains(filter.nameContains, ignoreCase = true))
        }

    override suspend fun updateVerifier(did: String, update: VerifierUpdate): VerifierRecord {
        val existing = verifiers[did] ?: throw NoSuchElementException("Verifier not found: $did")
        val updated = existing.copy(
            name = update.name ?: existing.name,
            description = update.description ?: existing.description,
            serviceEndpoint = update.serviceEndpoint ?: existing.serviceEndpoint,
            metadata = update.metadata ?: existing.metadata,
            updatedAt = Clock.System.now(),
        )
        verifiers[did] = updated
        return updated
    }

    override suspend fun revokeVerifier(did: String, reason: String?): Boolean {
        val record = verifiers[did] ?: return false
        verifiers[did] = record.copy(status = AccreditationStatus.REVOKED, updatedAt = Clock.System.now())
        return true
    }

    override suspend fun activateVerifier(did: String): Boolean {
        val record = verifiers[did] ?: return false
        verifiers[did] = record.copy(status = AccreditationStatus.ACTIVE, updatedAt = Clock.System.now())
        return true
    }

    override suspend fun getAccreditationStatus(did: String): AccreditationStatus =
        issuers[did]?.status ?: verifiers[did]?.status ?: AccreditationStatus.PENDING

    override suspend fun listCredentialTypes(): List<String> =
        issuers.values.flatMap { it.credentialTypes }.distinct().sorted()
}
