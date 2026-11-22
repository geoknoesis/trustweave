package org.trustweave.examples.production

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.examples.ExampleContexts
import org.trustweave.testkit.services.TestkitStatusListRegistryFactory
import org.trustweave.testkit.services.TestkitTrustRegistryFactory
import org.trustweave.testkit.services.TestkitWalletFactory
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.KmsProviders
import org.trustweave.trust.dsl.credential.RevocationProviders
import org.trustweave.trust.dsl.credential.TrustProviders
import org.trustweave.trust.types.RevocationResult
import org.trustweave.trust.types.TrustOperationResult
import org.trustweave.trust.types.getOrThrow
import org.trustweave.trust.types.getOrThrowDid

/**
 * Production-shaped wiring example using type-safe provider constants, explicit factory
 * injection, sealed result handling ([IssuanceResult], [VerificationResult], [RevocationResult],
 * [TrustOperationResult]), and coroutine-safe shutdown ([TrustWeave.closeAsync]).
 *
 * Uses local testkit factories so the example compiles and runs standalone without hosted
 * infrastructure; in production, swap:
 * - [KmsProviders.IN_MEMORY] -> [KmsProviders.AWS], [KmsProviders.AZURE], [KmsProviders.GOOGLE],
 *   or [KmsProviders.HASHICORP]
 * - [TestkitTrustRegistryFactory] / [TestkitWalletFactory] / [TestkitStatusListRegistryFactory] ->
 *   database- or cloud-backed factories
 */
fun main(): Unit =
    runBlocking {
        val trustWeave =
            TrustWeave.build(name = "production-wiring-example") {
                factories(
                    trustRegistryFactory = TestkitTrustRegistryFactory(),
                    walletFactory = TestkitWalletFactory(),
                    statusListRegistryFactory = TestkitStatusListRegistryFactory(),
                )
                keys {
                    provider(KmsProviders.IN_MEMORY)
                    algorithm(KeyAlgorithms.ED25519)
                }
                did {
                    method(DidMethods.KEY) {
                        algorithm(KeyAlgorithms.ED25519)
                    }
                }
                trust {
                    provider(TrustProviders.IN_MEMORY)
                }
                revocation {
                    provider(RevocationProviders.IN_MEMORY)
                }
            }

        try {
            // Non-null KMS access when wiring custom DID methods or inspecting key material directly.
            val kms = trustWeave.requireKms()
            check(kms.getSupportedAlgorithms().isNotEmpty()) { "KMS must report supported algorithms" }

            val issuerDid = trustWeave.createDid().getOrThrowDid()
            val holderDid = trustWeave.createDid().getOrThrowDid()

            when (
                val trustOutcome =
                    trustWeave.trustResult {
                        addAnchor(issuerDid.value) {
                            credentialTypes("EmployeeBadgeCredential")
                            description("Corporate HR Issuer")
                        }
                    }
            ) {
                is TrustOperationResult.Completed -> println("Trust anchor registered: ${issuerDid.value}")
                is TrustOperationResult.NotConfigured -> error("Trust registry not configured: ${trustOutcome.reason}")
                is TrustOperationResult.Failure -> error("Trust anchor registration failed: ${trustOutcome.reason}")
            }

            val credential =
                when (
                    val issued =
                        trustWeave.issue {
                            credential {
                                type("EmployeeBadgeCredential")
                                issuer(issuerDid)
                                subject(holderDid.value) {
                                    "department" to "Platform Engineering"
                                    "role" to "Staff Engineer"
                                }
                            }
                            signedBy(issuerDid)
                            additionalOption(ExampleContexts.OPTION_KEY, ExampleContexts.contexts)
                            withRevocation()
                        }
                ) {
                    is IssuanceResult.Success -> issued.credential
                    is IssuanceResult.Failure -> error("Issuance failed: ${issued.errors}")
                }

            val wallet =
                trustWeave
                    .wallet {
                        id("employee-wallet")
                        holder(holderDid)
                    }.getOrThrow()
            val storedId = wallet.store(credential)
            val stored = checkNotNull(wallet.get(storedId)) { "Stored credential missing from wallet" }

            val initialOutcome = describeVerification(trustWeave.verify(stored))
            check(initialOutcome == "valid") { "Expected valid credential, got: $initialOutcome" }

            val statusListUrl = checkNotNull(stored.credentialStatus?.statusListCredential?.value)
            val revocationOutcome =
                trustWeave.revokeResult {
                    credential(storedId)
                    statusList(statusListUrl)
                }
            when (revocationOutcome) {
                is RevocationResult.Revoked -> println("Credential revoked via sealed revokeResult")
                is RevocationResult.NotRevoked -> error("Credential status entry was not changed")
                is RevocationResult.NotConfigured -> error("Revocation not configured: ${revocationOutcome.reason}")
                is RevocationResult.InvalidRequest -> error("Invalid revocation request: ${revocationOutcome.reason}")
                is RevocationResult.TimedOut -> error("Revocation timed out after ${revocationOutcome.timeout}")
                is RevocationResult.Failure -> error("Revocation failed: ${revocationOutcome.reason}")
            }

            val afterRevocation =
                trustWeave.verify {
                    credential(stored)
                    checkRevocation()
                }
            val revokedOutcome = describeVerification(afterRevocation)
            check(revokedOutcome.startsWith("revoked")) {
                "Expected VerificationResult.Invalid.Revoked after revocation, got: $revokedOutcome"
            }
            println("Production wiring verified: initial=$initialOutcome, afterRevocation=$revokedOutcome")
        } finally {
            trustWeave.closeAsync()
        }
    }

private fun describeVerification(result: VerificationResult): String =
    when (result) {
        is VerificationResult.Valid -> "valid"
        is VerificationResult.Invalid.Revoked -> "revoked:${result.revocationReason ?: "unspecified"}"
        is VerificationResult.Invalid.Suspended -> "suspended:${result.reason ?: "unspecified"}"
        is VerificationResult.Invalid.Expired -> "expired:${result.expiredAt}"
        is VerificationResult.Invalid.NotYetValid -> "not-yet-valid:${result.validFrom}"
        is VerificationResult.Invalid.InvalidProof -> "invalid-proof:${result.reason}"
        is VerificationResult.Invalid.InvalidIssuer -> "invalid-issuer:${result.reason}"
        is VerificationResult.Invalid.UntrustedIssuer -> "untrusted-issuer:${result.issuerDid.value}"
        is VerificationResult.Invalid.UnsupportedFormat -> "unsupported-format:${result.format.value}"
        is VerificationResult.Invalid.AdapterNotReady -> "adapter-not-ready:${result.reason ?: "unconfigured"}"
        is VerificationResult.Invalid -> "invalid:${result.errors.joinToString()}"
    }
