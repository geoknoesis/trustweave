package org.trustweave.registry

interface TrustRegistry {
    suspend fun registerIssuer(registration: IssuerRegistration): IssuerRecord
    suspend fun getIssuer(did: String): IssuerRecord?
    suspend fun listIssuers(filter: RegistryFilter = RegistryFilter()): List<IssuerRecord>
    suspend fun updateIssuer(did: String, update: IssuerUpdate): IssuerRecord
    suspend fun revokeIssuer(did: String, reason: String? = null): Boolean
    suspend fun activateIssuer(did: String): Boolean

    suspend fun registerVerifier(registration: VerifierRegistration): VerifierRecord
    suspend fun getVerifier(did: String): VerifierRecord?
    suspend fun listVerifiers(filter: RegistryFilter = RegistryFilter()): List<VerifierRecord>
    suspend fun updateVerifier(did: String, update: VerifierUpdate): VerifierRecord
    suspend fun revokeVerifier(did: String, reason: String? = null): Boolean
    suspend fun activateVerifier(did: String): Boolean

    suspend fun getAccreditationStatus(did: String): AccreditationStatus
    suspend fun listCredentialTypes(): List<String>
}
