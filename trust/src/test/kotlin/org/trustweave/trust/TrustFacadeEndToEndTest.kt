package org.trustweave.trust

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.core.exception.ConfigException
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.domain.treasury.KmsKeyRef
import org.trustweave.trust.domain.treasury.SettlementStatus
import org.trustweave.trust.dsl.because
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.trusts
import org.trustweave.trust.dsl.trustsAll
import org.trustweave.trust.dsl.trustsPath
import org.trustweave.trust.dsl.withTestClaimContexts
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.TrustOperationResult
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.types.VerifierIdentity
import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.trust.types.valid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Behaviour of the facade with real in-memory services: issue, verify against the trust registry,
 * configuration errors and Trusted Domain wiring.
 */
class TrustFacadeEndToEndTest {
    private suspend fun issued(
        tw: TrustWeave,
        issuer: Did,
        type: String,
    ) = tw
        .issue {
            credential {
                type(type)
                issuer(issuer.value)
                subject {
                    id("did:key:holder")
                    "name" to "Alice"
                }
                issued(Clock.System.now())
            }
            signedBy(issuer)
            withTestClaimContexts()
        }.getOrThrow()

    private suspend fun verifyAgainstRegistry(
        tw: TrustWeave,
        credential: org.trustweave.credential.model.vc.VerifiableCredential,
    ): VerificationResult {
        val registry = assertNotNull(tw.configuration.trustRegistry)
        return tw.verify {
            credential(credential)
            requireTrust(registry)
        }
    }

    // ---- issue then verify with trust policies ----

    @Test
    fun `an issuer that is not a trust anchor is rejected as untrusted`() =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                val issuer = tw.createDid().getOrThrowDid()
                val credential = issued(tw, issuer, "EducationCredential")

                val result = verifyAgainstRegistry(tw, credential)

                val untrusted = assertIs<VerificationResult.Invalid.UntrustedIssuer>(result)
                assertEquals(issuer, untrusted.issuerDid)
                assertTrue(untrusted.errors.single().contains(issuer.value))
                assertFalse(result.valid)
            } finally {
                tw.close()
            }
        }

    @Test
    fun `an anchor restricted to the credential type accepts it and a different type is refused`(): Unit =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                val issuer = tw.createDid().getOrThrowDid()
                tw.trust { addAnchor(issuer.value) { credentialTypes("EducationCredential") } }

                assertIs<VerificationResult.Valid>(verifyAgainstRegistry(tw, issued(tw, issuer, "EducationCredential")))
                assertIs<VerificationResult.Invalid.UntrustedIssuer>(verifyAgainstRegistry(tw, issued(tw, issuer, "DriverLicense")))
            } finally {
                tw.close()
            }
        }

    @Test
    fun `an anchor trusted for all types accepts any credential until it is removed`(): Unit =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                val issuer = tw.createDid().getOrThrowDid()
                tw.trust { addAnchor(issuer.value) }
                val credential = issued(tw, issuer, "AnythingCredential")

                assertIs<VerificationResult.Valid>(verifyAgainstRegistry(tw, credential))

                var removed = false
                tw.trust { removed = removeAnchor(issuer.value) }
                assertTrue(removed)
                assertIs<VerificationResult.Invalid.UntrustedIssuer>(verifyAgainstRegistry(tw, credential))
            } finally {
                tw.close()
            }
        }

    @Test
    fun `an empty type restriction trusts the issuer for nothing`(): Unit =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                val issuer = tw.createDid().getOrThrowDid()
                tw.trust { addAnchor(issuer.value) { credentialTypes(emptyList()) } }

                assertIs<VerificationResult.Invalid.UntrustedIssuer>(verifyAgainstRegistry(tw, issued(tw, issuer, "EducationCredential")))
            } finally {
                tw.close()
            }
        }

    @Test
    fun `allowUntrusted after requireTrust restores plain signature verification`(): Unit =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                val issuer = tw.createDid().getOrThrowDid()
                val credential = issued(tw, issuer, "EducationCredential")
                val registry = assertNotNull(tw.configuration.trustRegistry)

                val result =
                    tw.verify {
                        credential(credential)
                        requireTrust(registry)
                        allowUntrusted()
                    }

                assertIs<VerificationResult.Valid>(result)
            } finally {
                tw.close()
            }
        }

    @Test
    fun `a custom trust policy decides on the issuer DID`(): Unit =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                val issuer = tw.createDid().getOrThrowDid()
                val credential = issued(tw, issuer, "EducationCredential")

                val accepting =
                    tw.verify {
                        credential(credential)
                        withTrustPolicy(
                            object : org.trustweave.credential.trust.TrustEvaluator {
                                override suspend fun isTrusted(issuer: Did) = true
                            },
                        )
                    }
                val rejecting =
                    tw.verify {
                        credential(credential)
                        withTrustPolicy(
                            object : org.trustweave.credential.trust.TrustEvaluator {
                                override suspend fun isTrusted(issuer: Did) = false
                            },
                        )
                    }

                assertIs<VerificationResult.Valid>(accepting)
                assertIs<VerificationResult.Invalid.UntrustedIssuer>(rejecting)
            } finally {
                tw.close()
            }
        }

    // ---- trust DSL ----

    @Test
    fun `the infix trust DSL registers anchors with their reasons and types`() =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                val uni = Did("did:key:uni")
                val gov = Did("did:key:gov")
                var addedUni = false
                var addedGov = false
                tw.trust {
                    addedUni = addAnchor(uni, uni trusts listOf("Degree", "Transcript") because { description("registrar") })
                    addedGov = addAnchor(gov, gov.trustsAll because { description("national authority") })

                    assertTrue(isTrusted(uni.value, "Degree"))
                    assertFalse(isTrusted(uni.value, "DriverLicense"))
                    assertTrue(isTrusted(gov.value, "DriverLicense"))
                    assertEquals(setOf(uni.value, gov.value), getTrustedIssuers().toSet())
                    assertEquals(listOf(gov.value), getTrustedIssuers("DriverLicense"))
                }
                assertTrue(addedUni && addedGov)
            } finally {
                tw.close()
            }
        }

    @Test
    fun `trust paths are reported through the DSL for connected and unconnected DIDs`(): Unit =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                val a = Did("did:key:a")
                val b = Did("did:key:b")
                var viaDsl: TrustPath? = null
                var unknown: TrustPath? = null
                tw.trust {
                    addAnchor(a.value)
                    viaDsl = resolve(a trustsPath a)
                    unknown = findTrustPath(VerifierIdentity(b), IssuerIdentity(Did("did:key:c")))
                }

                assertIs<TrustPath.Verified>(viaDsl)
                assertIs<TrustPath.NotFound>(unknown)
            } finally {
                tw.close()
            }
        }

    @Test
    fun `trustResult reports completion and captures failures instead of throwing`() =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                assertEquals(TrustOperationResult.Completed, tw.trustResult { addAnchor("did:key:ok") })

                val failed = tw.trustResult { error("registry exploded") }
                assertIs<TrustOperationResult.Failure>(failed)
                assertEquals("registry exploded", failed.cause.message)
            } finally {
                tw.close()
            }
        }

    @Test
    fun `without a trust registry the trust block reports not-configured`(): Unit =
        runBlocking {
            val tw = TrustWeave.build { keys { provider("inMemory") } }
            try {
                var ran = false
                val notConfigured = tw.trust { ran = true }

                assertNotNull(notConfigured)
                assertFalse(ran, "the block must not run without a registry")
                assertIs<TrustOperationResult.NotConfigured>(tw.trustResult { })
            } finally {
                tw.close()
            }
        }

    // ---- configuration validation ----

    @Test
    fun `an unknown revocation provider is a configuration error naming the field`() {
        val error =
            assertFailsWith<ConfigException.UnsupportedValue> {
                runBlocking {
                    TrustWeave.build {
                        keys { provider("inMemory") }
                        revocation(provider = "postgres")
                    }
                }
            }

        assertTrue(error.message!!.contains("revocation.provider"), error.message)
        assertTrue(error.message!!.contains("postgres"), error.message)
    }

    @Test
    fun `an unknown trust provider without a factory names the provider`() {
        val error =
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    TrustWeave.build {
                        keys { provider("inMemory") }
                        trust { provider("ldap") }
                    }
                }
            }

        assertTrue(error.message!!.contains("'ldap'") && error.message!!.contains("factory is required"), error.message)
    }

    @Test
    fun `an unknown KMS provider is rejected at build time`() {
        val error =
            assertFailsWith<Exception> {
                runBlocking { TrustWeave.build { keys { provider("no-such-kms") } } }
            }

        assertTrue(error.message!!.contains("no-such-kms") || error.message!!.contains("KMS provider"), error.message)
    }

    @Test
    fun `an anchor chain with an unknown provider fails the build`() {
        val error =
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    TrustWeave.build {
                        keys { provider("inMemory") }
                        anchor { chain("eip155:1") { provider("not-a-provider") } }
                    }
                }
            }

        assertTrue(error.message!!.contains("'not-a-provider'") && error.message!!.contains("eip155:1"), error.message)
    }

    @Test
    fun `a domain definition missing its chain accounts fails while configuring`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    TrustWeave.build {
                        keys { provider("inMemory") }
                        domain {
                            domainId("edu")
                            payerDid("did:web:payer")
                        }
                    }
                }
            }

        assertTrue(error.message!!.contains("at least one chainAccount"), error.message)
    }

    @Test
    fun `a domain account on a chain that was never configured is reported with the fix`() {
        val error =
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    TrustWeave.build {
                        keys { provider("inMemory") }
                        domain {
                            domainId("edu")
                            payerDid("did:web:payer")
                            chainAccount("eip155:137") {
                                keyRef = KmsKeyRef("inMemory", "k")
                                address = "0xabc"
                            }
                        }
                    }
                }
            }

        assertTrue(error.message!!.contains("eip155:137") && error.message!!.contains("anchor { chain("), error.message)
    }

    // ---- trusted domain wiring ----

    private suspend fun domainFacade(): TrustWeave =
        TrustWeave.build {
            keys { provider("inMemory") }
            anchor { chain("eip155:137") { inMemory() } }
            domain {
                domainId("edu")
                payerDid("did:web:payer.example")
                chainAccount("eip155:137") {
                    keyRef = KmsKeyRef("inMemory", "polygon")
                    address = "0xabc"
                }
            }
        }

    @Test
    fun `anchoring without a configured domain explains how to configure one`() =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                assertNull(tw.activeDomain)
                val error =
                    assertFailsWith<IllegalStateException> { tw.anchorThroughDomain("did.anchor", "eip155:137", JsonObject(emptyMap())) }
                assertTrue(error.message!!.contains("No active Trusted Domain"), error.message)
            } finally {
                tw.close()
            }
        }

    @Test
    fun `anchoring through a configured domain writes the payload and records the spend`() =
        runBlocking {
            val tw = domainFacade()
            try {
                val domain = assertNotNull(tw.activeDomain)
                assertEquals("edu", domain.domainId.value)
                val payload = JsonObject(mapOf("digest" to JsonPrimitive("abc123")))

                val result = tw.anchorThroughDomain("did.anchor", "eip155:137", payload)

                assertEquals("eip155:137", result.ref.chainId)
                assertEquals(payload, result.payload)
                val entries = domain.treasury().ledger().all("eip155:137")
                assertEquals(listOf(SettlementStatus.SETTLED), entries.map { it.status })
                assertEquals("did:web:payer.example", entries.single().payerDid)
            } finally {
                tw.close()
            }
        }

    @Test
    fun `a chain outside the domain is refused and sponsor-pays is not allowed by the default policy`(): Unit =
        runBlocking {
            val tw = domainFacade()
            try {
                val payload = JsonObject(emptyMap())

                val unknown = assertFailsWith<BlockchainException.ChainNotRegistered> { tw.anchorThroughDomain("k", "eip155:1", payload) }
                assertEquals(listOf("eip155:137"), unknown.availableChains)

                assertFailsWith<TreasuryException.StrategyNotAllowed> {
                    tw.anchorThroughDomain("k", "eip155:137", payload, feeStrategy = FeeStrategy.Sponsored("did:web:sponsor"))
                }
            } finally {
                tw.close()
            }
        }
}
