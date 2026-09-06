package org.trustweave.examples.comprehensive

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.examples.ExampleContexts
import org.trustweave.testkit.services.TestkitStatusListRegistryFactory
import org.trustweave.testkit.services.TestkitWalletFactory
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.revocation
import org.trustweave.trust.types.getOrThrow
import org.trustweave.trust.types.getOrThrowDid

/** Local credential lifecycle: issue, store, retrieve, verify, then revoke and reject. */
fun main(): Unit =
    runBlocking {
        val sdk =
            TrustWeave.build {
                factories(
                    walletFactory = TestkitWalletFactory(),
                    statusListRegistryFactory = TestkitStatusListRegistryFactory(),
                )
                keys {
                    provider("inMemory")
                    algorithm("Ed25519")
                }
                did { method("key") { algorithm("Ed25519") } }
                revocation { provider("inMemory") }
            }
        try {
            val issuer = sdk.createDid().getOrThrowDid()
            val holder = sdk.createDid().getOrThrowDid()
            val credential =
                sdk
                    .issue {
                        credential {
                            type("TrainingCredential")
                            issuer(issuer)
                            subject(holder.value) { "course" to "Example safety training" }
                        }
                        signedBy(issuer)
                        additionalOption(ExampleContexts.OPTION_KEY, ExampleContexts.contexts)
                        withRevocation()
                    }.getOrThrow()
            val wallet =
                sdk
                    .wallet {
                        id("lifecycle-demo")
                        holder(holder)
                    }.getOrThrow()
            val storedId = wallet.store(credential)
            val stored = checkNotNull(wallet.get(storedId))
            check(stored == credential)
            val verified = sdk.verify(stored)
            check(verified is VerificationResult.Valid) { "Before revocation: $verified" }
            val statusList = checkNotNull(stored.credentialStatus?.statusListCredential)
            val manager = sdk.revocation { statusList(statusList.value) }
            check(!manager.check(stored).revoked)
            check(
                sdk.revoke {
                    credential(storedId)
                    statusList(statusList.value)
                },
            )
            check(manager.check(stored).revoked)
            check(
                sdk.verify {
                    credential(stored)
                    checkRevocation()
                } !is VerificationResult.Valid,
            )
            println("Lifecycle verified: issued, stored, retrieved, verified, revoked and rejected")
        } finally {
            sdk.close()
        }
    }
