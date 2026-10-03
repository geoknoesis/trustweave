package org.trustweave.registry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class InMemoryTrustRegistryContractTest {
    private val registry = InMemoryTrustRegistry()

    @Test
    fun `re-registering a revoked issuer is rejected and keeps it revoked`() =
        runBlocking<Unit> {
            registry.registerIssuer(IssuerRegistration(did = "did:key:i", name = "I"))
            registry.revokeIssuer("did:key:i", "fraud")

            val e =
                shouldThrow<ParticipantAlreadyRegisteredException> {
                    registry.registerIssuer(IssuerRegistration(did = "did:key:i", name = "I again"))
                }
            e.did shouldBe "did:key:i"
            registry.getAccreditationStatus("did:key:i") shouldBe AccreditationStatus.REVOKED
            registry.getIssuer("did:key:i")!!.name shouldBe "I"
        }

    @Test
    fun `re-registering a verifier is rejected`() =
        runBlocking<Unit> {
            registry.registerVerifier(VerifierRegistration(did = "did:key:v", name = "V"))
            registry.revokeVerifier("did:key:v")
            shouldThrow<ParticipantAlreadyRegisteredException> {
                registry.registerVerifier(VerifierRegistration(did = "did:key:v", name = "V"))
            }
            registry.getAccreditationStatus("did:key:v") shouldBe AccreditationStatus.REVOKED
        }

    @Test
    fun `revocation reason is persisted and cleared on activation`() =
        runBlocking<Unit> {
            registry.registerIssuer(IssuerRegistration(did = "did:key:i", name = "I"))
            registry.revokeIssuer("did:key:i", "key compromise") shouldBe true
            registry.getIssuer("did:key:i")!!.revocationReason shouldBe "key compromise"
            registry.activateIssuer("did:key:i") shouldBe true
            registry.getIssuer("did:key:i")!!.revocationReason shouldBe null

            registry.registerVerifier(VerifierRegistration(did = "did:key:v", name = "V"))
            registry.revokeVerifier("did:key:v", "expired accreditation")
            registry.getVerifier("did:key:v")!!.revocationReason shouldBe "expired accreditation"
        }

    @Test
    fun `revoke and activate of unknown DIDs return false and update throws`() =
        runBlocking<Unit> {
            registry.revokeIssuer("did:key:none") shouldBe false
            registry.activateVerifier("did:key:none") shouldBe false
            shouldThrow<NoSuchElementException> { registry.updateIssuer("did:key:none", IssuerUpdate(name = "x")) }
        }

    @Test
    fun `concurrent updates and revocation do not lose the revocation`() =
        runBlocking<Unit> {
            registry.registerIssuer(IssuerRegistration(did = "did:key:i", name = "I"))
            (0 until 200)
                .map { i ->
                    async(Dispatchers.Default) {
                        if (i == 100) {
                            registry.revokeIssuer("did:key:i", "r")
                        } else {
                            registry.updateIssuer("did:key:i", IssuerUpdate(description = "d$i"))
                        }
                    }
                }.awaitAll()
            registry.getAccreditationStatus("did:key:i") shouldBe AccreditationStatus.REVOKED
        }

    @Test
    fun `concurrent registrations of the same DID let exactly one win`() =
        runBlocking<Unit> {
            val results =
                (0 until 50)
                    .map { i ->
                        async(Dispatchers.Default) {
                            runCatching { registry.registerIssuer(IssuerRegistration(did = "did:key:race", name = "n$i")) }
                        }
                    }.awaitAll()
            results.count { it.isSuccess } shouldBe 1
        }
}
