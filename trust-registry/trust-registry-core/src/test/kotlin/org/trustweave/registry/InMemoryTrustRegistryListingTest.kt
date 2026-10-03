package org.trustweave.registry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class InMemoryTrustRegistryListingTest {
    private val registry = InMemoryTrustRegistry()

    private suspend fun seed() {
        for (i in 1..5) {
            registry.registerIssuer(
                IssuerRegistration(
                    did = "did:key:i$i",
                    name = "Issuer $i",
                    credentialTypes = if (i % 2 == 0) listOf("Diploma") else listOf("License"),
                ),
            )
            Thread.sleep(2)
        }
        registry.revokeIssuer("did:key:i2", "fraud")
    }

    @Test
    fun `paged listing slices the ordered, filtered listing`() =
        runBlocking<Unit> {
            seed()
            registry.listIssuers().map { it.did } shouldBe (1..5).map { "did:key:i$it" }
            registry.listIssuers(RegistryFilter(), 2, 0).map { it.did } shouldBe listOf("did:key:i1", "did:key:i2")
            registry.listIssuers(RegistryFilter(), 2, 4).map { it.did } shouldBe listOf("did:key:i5")
            registry.listIssuers(RegistryFilter(), 10, 5).shouldBeEmpty()
            registry
                .listIssuers(RegistryFilter(credentialType = "License"), 2, 1)
                .map { it.did } shouldBe listOf("did:key:i3", "did:key:i5")
            registry.listIssuers(RegistryFilter(status = AccreditationStatus.REVOKED), 5, 0).map { it.did } shouldBe listOf("did:key:i2")
        }

    @Test
    fun `negative limit or offset is rejected`() =
        runBlocking<Unit> {
            shouldThrow<IllegalArgumentException> { registry.listIssuers(RegistryFilter(), -1, 0) }
            shouldThrow<IllegalArgumentException> { registry.listVerifiers(RegistryFilter(), 0, -1) }
        }

    @Test
    fun `accreditation status is UNKNOWN for a DID that was never registered`() =
        runBlocking<Unit> {
            registry.getAccreditationStatus("did:key:ghost") shouldBe AccreditationStatus.UNKNOWN
            registry.registerVerifier(VerifierRegistration(did = "did:key:v", name = "V"))
            registry.getAccreditationStatus("did:key:v") shouldBe AccreditationStatus.ACTIVE
        }

    @Test
    fun `update keeps fields that are not supplied and bumps updatedAt`() =
        runBlocking<Unit> {
            val before = registry.registerIssuer(IssuerRegistration(did = "did:key:u", name = "U", description = "d"))
            Thread.sleep(2)
            val after = registry.updateIssuer("did:key:u", IssuerUpdate(name = "U2"))
            after.name shouldBe "U2"
            after.description shouldBe "d"
            (after.updatedAt > before.updatedAt) shouldBe true
            after.registeredAt shouldBe before.registeredAt
            shouldThrow<NoSuchElementException> { registry.updateVerifier("did:key:none", VerifierUpdate(name = "x")) }
        }

    @Test
    fun `status history records every transition and is kept per DID`() =
        runBlocking<Unit> {
            registry.registerIssuer(IssuerRegistration(did = "did:key:h", name = "H"))
            registry.registerIssuer(IssuerRegistration(did = "did:key:other", name = "O"))
            registry.revokeIssuer("did:key:h", "r1")
            registry.activateIssuer("did:key:h")
            registry.revokeIssuer("did:key:h", "r2")
            registry.statusHistory("did:key:h").map { it.from to it.to } shouldBe
                listOf(
                    null to AccreditationStatus.ACTIVE,
                    AccreditationStatus.ACTIVE to AccreditationStatus.REVOKED,
                    AccreditationStatus.REVOKED to AccreditationStatus.ACTIVE,
                    AccreditationStatus.ACTIVE to AccreditationStatus.REVOKED,
                )
            registry.statusHistory("did:key:h").map { it.reason } shouldBe listOf(null, "r1", null, "r2")
            registry.statusHistory("did:key:other").size shouldBe 1
            registry.statusHistory("did:key:none").shouldBeEmpty()
        }

    @Test
    fun `concurrent revocations are each recorded exactly once`() =
        runBlocking<Unit> {
            registry.registerIssuer(IssuerRegistration(did = "did:key:c", name = "C"))
            (1..50).map { async(Dispatchers.Default) { registry.revokeIssuer("did:key:c", "r$it") } }.awaitAll()
            registry.statusHistory("did:key:c").count { it.to == AccreditationStatus.REVOKED } shouldBe 50
        }
}
