package org.trustweave.registry

import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [TrustRegistry].
 *
 * Semantics shared with the database implementation:
 * - registering a DID that is already registered in the same role throws
 *   [ParticipantAlreadyRegisteredException] and leaves the existing record untouched;
 * - every read-modify-write is a single atomic map operation, so concurrent revoke / update /
 *   activate calls cannot lose each other's changes;
 * - [revokeIssuer] / [revokeVerifier] persist the supplied reason in `revocationReason`, and
 *   activation clears it.
 */
class InMemoryTrustRegistry : TrustRegistry {
    private val issuers = ConcurrentHashMap<String, IssuerRecord>()
    private val verifiers = ConcurrentHashMap<String, VerifierRecord>()

    override suspend fun registerIssuer(registration: IssuerRegistration): IssuerRecord {
        val now = Clock.System.now()
        val record =
            IssuerRecord(
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
        if (issuers.putIfAbsent(registration.did, record) != null) {
            throw ParticipantAlreadyRegisteredException(registration.did, "Issuer")
        }
        return record
    }

    override suspend fun getIssuer(did: String): IssuerRecord? = issuers[did]

    override suspend fun listIssuers(filter: RegistryFilter): List<IssuerRecord> =
        issuers.values.filter { record ->
            (filter.status == null || record.status == filter.status) &&
                (filter.credentialType == null || record.credentialTypes.contains(filter.credentialType)) &&
                (filter.nameContains == null || record.name.contains(filter.nameContains, ignoreCase = true))
        }

    override suspend fun updateIssuer(
        did: String,
        update: IssuerUpdate,
    ): IssuerRecord =
        issuers.computeIfPresent(did) { _, existing ->
            existing.copy(
                name = update.name ?: existing.name,
                description = update.description ?: existing.description,
                credentialTypes = update.credentialTypes ?: existing.credentialTypes,
                serviceEndpoint = update.serviceEndpoint ?: existing.serviceEndpoint,
                metadata = update.metadata ?: existing.metadata,
                updatedAt = Clock.System.now(),
            )
        } ?: throw NoSuchElementException("Issuer not found: $did")

    override suspend fun revokeIssuer(
        did: String,
        reason: String?,
    ): Boolean =
        issuers.computeIfPresent(did) { _, record ->
            record.copy(status = AccreditationStatus.REVOKED, revocationReason = reason, updatedAt = Clock.System.now())
        } != null

    override suspend fun activateIssuer(did: String): Boolean =
        issuers.computeIfPresent(did) { _, record ->
            record.copy(status = AccreditationStatus.ACTIVE, revocationReason = null, updatedAt = Clock.System.now())
        } != null

    override suspend fun registerVerifier(registration: VerifierRegistration): VerifierRecord {
        val now = Clock.System.now()
        val record =
            VerifierRecord(
                did = registration.did,
                name = registration.name,
                description = registration.description,
                serviceEndpoint = registration.serviceEndpoint,
                status = AccreditationStatus.ACTIVE,
                registeredAt = now,
                updatedAt = now,
                metadata = registration.metadata,
            )
        if (verifiers.putIfAbsent(registration.did, record) != null) {
            throw ParticipantAlreadyRegisteredException(registration.did, "Verifier")
        }
        return record
    }

    override suspend fun getVerifier(did: String): VerifierRecord? = verifiers[did]

    override suspend fun listVerifiers(filter: RegistryFilter): List<VerifierRecord> =
        verifiers.values.filter { record ->
            (filter.status == null || record.status == filter.status) &&
                (filter.nameContains == null || record.name.contains(filter.nameContains, ignoreCase = true))
        }

    override suspend fun updateVerifier(
        did: String,
        update: VerifierUpdate,
    ): VerifierRecord =
        verifiers.computeIfPresent(did) { _, existing ->
            existing.copy(
                name = update.name ?: existing.name,
                description = update.description ?: existing.description,
                serviceEndpoint = update.serviceEndpoint ?: existing.serviceEndpoint,
                metadata = update.metadata ?: existing.metadata,
                updatedAt = Clock.System.now(),
            )
        } ?: throw NoSuchElementException("Verifier not found: $did")

    override suspend fun revokeVerifier(
        did: String,
        reason: String?,
    ): Boolean =
        verifiers.computeIfPresent(did) { _, record ->
            record.copy(status = AccreditationStatus.REVOKED, revocationReason = reason, updatedAt = Clock.System.now())
        } != null

    override suspend fun activateVerifier(did: String): Boolean =
        verifiers.computeIfPresent(did) { _, record ->
            record.copy(status = AccreditationStatus.ACTIVE, revocationReason = null, updatedAt = Clock.System.now())
        } != null

    override suspend fun getAccreditationStatus(did: String): AccreditationStatus =
        issuers[did]?.status ?: verifiers[did]?.status ?: AccreditationStatus.UNKNOWN

    override suspend fun listCredentialTypes(): List<String> =
        issuers.values
            .flatMap { it.credentialTypes }
            .distinct()
            .sorted()
}
