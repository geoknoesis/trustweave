package org.trustweave.registry

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryTrustRegistryTest {

    private val registry = InMemoryTrustRegistry()

    @Test
    fun `getAccreditationStatus returns UNKNOWN for unregistered DID`() = runBlocking {
        assertEquals(AccreditationStatus.UNKNOWN, registry.getAccreditationStatus("did:key:never-registered"))
    }

    @Test
    fun `getAccreditationStatus returns ACTIVE for registered issuer`() = runBlocking {
        registry.registerIssuer(IssuerRegistration(did = "did:key:issuer", name = "Issuer"))
        assertEquals(AccreditationStatus.ACTIVE, registry.getAccreditationStatus("did:key:issuer"))
    }

    @Test
    fun `getAccreditationStatus returns REVOKED after revocation`() = runBlocking {
        registry.registerIssuer(IssuerRegistration(did = "did:key:revoked", name = "Revoked Issuer"))
        registry.revokeIssuer("did:key:revoked")
        assertEquals(AccreditationStatus.REVOKED, registry.getAccreditationStatus("did:key:revoked"))
    }

    @Test
    fun `getAccreditationStatus returns ACTIVE for registered verifier`() = runBlocking {
        registry.registerVerifier(VerifierRegistration(did = "did:key:verifier", name = "Verifier"))
        assertEquals(AccreditationStatus.ACTIVE, registry.getAccreditationStatus("did:key:verifier"))
    }
}
