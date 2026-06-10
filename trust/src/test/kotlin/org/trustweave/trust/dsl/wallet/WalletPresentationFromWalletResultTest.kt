package org.trustweave.trust.dsl.wallet

import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.trust.types.getOrThrow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.services.TestkitTrustRegistryFactory
import org.trustweave.testkit.services.TestkitWalletFactory
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.createTestCredentialService
import org.trustweave.trust.dsl.withTestClaimContexts
import org.trustweave.trust.types.PresentationResult
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract tests for [presentationFromWalletResult]: wallet credential resolution and sealed errors.
 */
class WalletPresentationFromWalletResultTest {

    @Test
    fun `presentationFromWalletResult returns InvalidRequest when fromWallet id not in wallet`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
            when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                is SignResult.Success -> result.signature
                else -> error("Signing failed: $result")
            }
        }
        val tempTw = TrustWeave.build {
            factories(
                trustRegistryFactory = TestkitTrustRegistryFactory(),
                walletFactory = TestkitWalletFactory(),
            )
            keys {
                custom(kms)
                signer(signer)
            }
            did { method("key") { algorithm("Ed25519") } }
            trust { provider("inMemory") }
        }
        val didResolver = DidResolver { did -> tempTw.configuration.didRegistry.resolve(did.value) }
        val cs = createTestCredentialService(kms = kms, didResolver = didResolver)
        val tw = TrustWeave.build {
            factories(
                trustRegistryFactory = TestkitTrustRegistryFactory(),
                walletFactory = TestkitWalletFactory(),
            )
            keys {
                custom(kms)
                signer(signer)
            }
            did { method("key") { algorithm("Ed25519") } }
            trust { provider("inMemory") }
            credentialService(cs)
        }
        val issuer = tw.createDid { method("key"); algorithm("Ed25519") }.getOrThrowDid()
        val credential = tw.issue {
            credential {
                type("PersonCredential")
                issuer(issuer)
                subject {
                    id(Did("did:key:holder"))
                    "name" to "Alice"
                }
                issued(Clock.System.now())
            }
            signedBy(issuer)
            withTestClaimContexts() // Define ad-hoc test claims in the credential @context
        }.getOrThrow()
        val wallet = tw.wallet {
            holder("did:key:holder")
            inMemory()
        }.getOrThrow()
        wallet.store(credential)
        val r = tw.presentationFromWalletResult(wallet) {
            fromWallet("nonexistent-credential-id")
            holder("did:key:holder")
        }
        val inv = assertIs<PresentationResult.Failure.InvalidRequest>(r)
        assertTrue(inv.errors.any { it.contains("not found", ignoreCase = true) })
    }
}
