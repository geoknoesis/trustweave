package org.trustweave.trust

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.trust.TypeAwareTrustEvaluator
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.services.DefaultTrustRegistryFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.trustweave.testkit.trust.InMemoryTrustRegistry as TestkitRegistry

/** A typeless trust question must not be answered "yes" for an issuer trusted only for some types. */
class TrustRegistryTypeSafetyTest {
    private val restricted = Did("did:key:restricted")
    private val open = Did("did:key:open")

    private suspend fun registries(): List<Pair<String, TrustRegistry>> =
        listOf(
            "testkit" to TestkitRegistry(),
            "default" to DefaultTrustRegistryFactory.create("inMemory"),
        ).onEach { (_, r) ->
            r.addTrustAnchor(restricted.value, TrustAnchorMetadata(credentialTypes = listOf("EducationCredential")))
            r.addTrustAnchor(open.value, TrustAnchorMetadata(credentialTypes = null))
        }

    @Test
    fun `typeless trust fails closed for a type-restricted anchor`() =
        runBlocking<Unit> {
            for ((name, r) in registries()) {
                assertFalse(r.isTrusted(restricted), "$name: restricted anchor must not be trusted for 'any' type")
                assertTrue(r.isTrusted(open), "$name: unrestricted anchor is trusted")
                assertFalse(r.isTrusted(Did("did:key:unknown")), "$name: unknown issuer")
            }
        }

    @Test
    fun `an empty type restriction trusts nothing`() =
        runBlocking<Unit> {
            for ((name, r) in registries()) {
                r.addTrustAnchor("did:key:none", TrustAnchorMetadata(credentialTypes = emptyList()))
                assertFalse(r.isTrusted(Did("did:key:none")), name)
                assertFalse((r as TypeAwareTrustEvaluator).isTrusted(Did("did:key:none"), listOf("VerifiableCredential")), name)
            }
        }

    @Test
    fun `typed trust honours the anchor's type restriction`() =
        runBlocking<Unit> {
            for ((name, r) in registries()) {
                val typed = r as TypeAwareTrustEvaluator
                assertTrue(typed.isTrusted(restricted, listOf("VerifiableCredential", "EducationCredential")), name)
                assertFalse(typed.isTrusted(restricted, listOf("VerifiableCredential", "BankAccountCredential")), name)
                assertFalse(
                    typed.isTrusted(restricted, listOf("VerifiableCredential", "EducationCredential", "BankAccountCredential")),
                    name,
                )
                assertFalse(typed.isTrusted(restricted, emptyList()), "$name: no type given, restricted anchor")
                assertTrue(typed.isTrusted(open, listOf("VerifiableCredential", "Anything")), name)
            }
        }

    @Test
    fun `isTrustedIssuer with a null type keeps its documented any-type meaning`() =
        runBlocking<Unit> {
            for ((name, r) in registries()) {
                assertTrue(r.isTrustedIssuer(restricted.value, null), name)
            }
        }
}
