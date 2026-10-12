package org.trustweave.trust.services

import kotlinx.coroutines.runBlocking
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.TrustAnchorMetadata
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.types.VerifierIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultTrustRegistryTest {
    private fun verifier(did: String) = VerifierIdentity(Did(did))

    private fun issuer(did: String) = IssuerIdentity(Did(did))

    private fun registry() = InMemoryTrustRegistry()

    @Test
    fun `the default factory serves only the in-memory provider names`() =
        runBlocking {
            for (name in listOf("inMemory", "in-memory", "default")) {
                assertIs<InMemoryTrustRegistry>(DefaultTrustRegistryFactory.create(name))
            }
            val error = assertFailsWith<IllegalStateException> { DefaultTrustRegistryFactory.create("postgres") }
            assertTrue(
                error.message!!.contains("'postgres' is not supported") && error.message!!.contains("TrustRegistryFactory"),
                error.message,
            )
        }

    @Test
    fun `each factory call yields an independent registry`() =
        runBlocking {
            val a = DefaultTrustRegistryFactory.create("inMemory")
            val b = DefaultTrustRegistryFactory.create("inMemory")
            a.addTrustAnchor("did:key:x", TrustAnchorMetadata())

            assertTrue(a.isTrustedIssuer("did:key:x", null))
            assertFalse(b.isTrustedIssuer("did:key:x", null))
        }

    @Test
    fun `an issuer is trusted only after being added and only once`() =
        runBlocking {
            val r = registry()

            assertFalse(r.isTrustedIssuer("did:key:a", null))
            assertTrue(r.addTrustAnchor("did:key:a", TrustAnchorMetadata()))
            assertFalse(r.addTrustAnchor("did:key:a", TrustAnchorMetadata(description = "second")), "duplicate add must be refused")
            assertTrue(r.isTrustedIssuer("did:key:a", null))
        }

    @Test
    fun `a type-restricted anchor is trusted for its types and nothing else`() =
        runBlocking {
            val r = registry()
            r.addTrustAnchor("did:key:uni", TrustAnchorMetadata(credentialTypes = listOf("Degree", "Transcript")))

            assertTrue(r.isTrustedIssuer("did:key:uni", "Degree"))
            assertTrue(r.isTrustedIssuer("did:key:uni", "Transcript"))
            assertFalse(r.isTrustedIssuer("did:key:uni", "DriverLicense"))
            assertFalse(r.isTrustedForAllTypes("did:key:uni"))
        }

    @Test
    fun `an unrestricted anchor is trusted for every type and for all types`() =
        runBlocking {
            val r = registry()
            r.addTrustAnchor("did:key:gov", TrustAnchorMetadata(credentialTypes = null))

            assertTrue(r.isTrustedIssuer("did:key:gov", "Anything"))
            assertTrue(r.isTrustedForAllTypes("did:key:gov"))
            assertFalse(r.isTrustedForAllTypes("did:key:unknown"))
        }

    @Test
    fun `an anchor with an empty type list is trusted for no type`() =
        runBlocking {
            val r = registry()
            r.addTrustAnchor("did:key:none", TrustAnchorMetadata(credentialTypes = emptyList()))

            assertFalse(r.isTrustedIssuer("did:key:none", "Degree"))
            assertFalse(r.isTrustedForAllTypes("did:key:none"))
        }

    @Test
    fun `trusted issuers can be listed per credential type`() =
        runBlocking {
            val r = registry()
            r.addTrustAnchor("did:key:uni", TrustAnchorMetadata(credentialTypes = listOf("Degree")))
            r.addTrustAnchor("did:key:gov", TrustAnchorMetadata(credentialTypes = null))
            r.addTrustAnchor("did:key:dmv", TrustAnchorMetadata(credentialTypes = listOf("DriverLicense")))

            assertEquals(setOf("did:key:uni", "did:key:gov"), r.getTrustedIssuers("Degree").toSet())
            assertEquals(setOf("did:key:dmv", "did:key:gov"), r.getTrustedIssuers("DriverLicense").toSet())
            assertEquals(3, r.getTrustedIssuers(null as String?).size)
            assertEquals(listOf("did:key:gov"), r.getTrustedIssuers("Unlisted"))
        }

    @Test
    fun `removing an anchor reports whether it existed and stops trusting it`() =
        runBlocking {
            val r = registry()
            r.addTrustAnchor("did:key:a", TrustAnchorMetadata())

            assertTrue(r.removeTrustAnchor("did:key:a"))
            assertFalse(r.isTrustedIssuer("did:key:a", null))
            assertFalse(r.removeTrustAnchor("did:key:a"))
            assertTrue(r.addTrustAnchor("did:key:a", TrustAnchorMetadata()), "a removed anchor can be added again")
        }

    @Test
    fun `a verifier trusts itself with a perfect score and no intermediate anchors`() =
        runBlocking {
            val path = registry().findTrustPath(verifier("did:key:me"), issuer("did:key:me"))

            val verified = assertIs<TrustPath.Verified>(path)
            assertTrue(verified.anchors.isEmpty())
            assertEquals(1.0, verified.trustScore)
            assertEquals(1, verified.length)
        }

    @Test
    fun `no path is reported when neither end is a trust anchor`() =
        runBlocking {
            val path = registry().findTrustPath(verifier("did:key:a"), issuer("did:key:b"))

            val notFound = assertIs<TrustPath.NotFound>(path)
            assertTrue(notFound.reason!!.contains("Neither did:key:a nor did:key:b are trust anchors"), notFound.reason)
        }

    @Test
    fun `two unconnected anchors have no path`() =
        runBlocking {
            val r = registry()
            r.addTrustAnchor("did:key:a", TrustAnchorMetadata())
            r.addTrustAnchor("did:key:b", TrustAnchorMetadata())

            val notFound = assertIs<TrustPath.NotFound>(r.findTrustPath(verifier("did:key:a"), issuer("did:key:b")))
            assertTrue(notFound.reason!!.startsWith("No trust path found between did:key:a and did:key:b"), notFound.reason)
        }

    @Test
    fun `a chain of delegations yields the intermediate anchors and a decreasing score`() =
        runBlocking {
            val r = registry()
            listOf("a", "b", "c").forEach { r.addTrustAnchor("did:key:$it", TrustAnchorMetadata(description = it)) }
            r.addTrustRelationship("did:key:a", "did:key:b")
            r.addTrustRelationship("did:key:b", "did:key:c")

            val direct = assertIs<TrustPath.Verified>(r.findTrustPath(verifier("did:key:a"), issuer("did:key:b")))
            assertEquals(0.8, direct.trustScore, 1e-9, "a direct delegation scores below self-trust")
            assertTrue(direct.anchors.isEmpty())

            val viaB = assertIs<TrustPath.Verified>(r.findTrustPath(verifier("did:key:a"), issuer("did:key:c")))
            assertEquals(listOf("did:key:b"), viaB.anchors.map { it.did.value })
            assertEquals(
                "b",
                viaB.anchors
                    .single()
                    .metadata.description,
                "intermediate anchors carry their registered metadata",
            )
            assertEquals(listOf("did:key:a", "did:key:b", "did:key:c"), viaB.fullPath.map { it.value })
            assertEquals(0.6, viaB.trustScore, 1e-9)
            assertEquals(2, viaB.length)
        }

    @Test
    fun `trust relationships are directional`() =
        runBlocking {
            val r = registry()
            r.addTrustAnchor("did:key:a", TrustAnchorMetadata())
            r.addTrustAnchor("did:key:b", TrustAnchorMetadata())
            r.addTrustRelationship("did:key:a", "did:key:b")

            assertIs<TrustPath.Verified>(r.findTrustPath(verifier("did:key:a"), issuer("did:key:b")))
            assertIs<TrustPath.NotFound>(r.findTrustPath(verifier("did:key:b"), issuer("did:key:a")))
        }

    @Test
    fun `very long chains bottom out at the minimum score`() =
        runBlocking {
            val r = registry()
            val ids = (0..8).map { "did:key:n$it" }
            ids.forEach { r.addTrustAnchor(it, TrustAnchorMetadata()) }
            ids.zipWithNext().forEach { (a, b) -> r.addTrustRelationship(a, b) }

            val verified = assertIs<TrustPath.Verified>(r.findTrustPath(verifier(ids.first()), issuer(ids.last())))

            assertEquals(0.1, verified.trustScore, 1e-9)
            assertEquals(7, verified.anchors.size)
        }

    @Test
    fun `cycles in the relationship graph do not hang the search`() =
        runBlocking {
            val r = registry()
            listOf("a", "b", "c").forEach { r.addTrustAnchor("did:key:$it", TrustAnchorMetadata()) }
            r.addTrustRelationship("did:key:a", "did:key:b")
            r.addTrustRelationship("did:key:b", "did:key:a")

            assertIs<TrustPath.NotFound>(r.findTrustPath(verifier("did:key:a"), issuer("did:key:c")))
        }

    @Test
    fun `removing an intermediate anchor breaks paths that used it`() =
        runBlocking {
            val r = registry()
            listOf("a", "b", "c").forEach { r.addTrustAnchor("did:key:$it", TrustAnchorMetadata()) }
            r.addTrustRelationship("did:key:a", "did:key:b")
            r.addTrustRelationship("did:key:b", "did:key:c")
            assertNotNull((r.findTrustPath(verifier("did:key:a"), issuer("did:key:c")) as? TrustPath.Verified))

            r.removeTrustAnchor("did:key:b")

            assertIs<TrustPath.NotFound>(r.findTrustPath(verifier("did:key:a"), issuer("did:key:c")))
        }
}
