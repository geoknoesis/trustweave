package org.trustweave.signatures.cades

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult

/**
 * The KMS sign call must run in the caller's coroutine (not inside a `runBlocking` bridge in Bouncy
 * Castle's synchronous ContentSigner), so cancelling the caller cancels a hung KMS call.
 */
class CadesSignerCancellationTest {
    @Test
    fun `cancelling the caller cancels a hung KMS sign`() =
        runBlocking<Unit> {
            val delegate = TestKms()
            val keyId =
                (delegate.generateKey(Algorithm.P256, mapOf("keyId" to "k")) as GenerateKeyResult.Success)
                    .keyHandle.id
            val chain = TestCa().issueChainBytes(delegate.publicKey(keyId), "CN=Hung")
            val signEntered = CompletableDeferred<Unit>()
            val signCancelled = CompletableDeferred<Unit>()
            val hungKms =
                object : KeyManagementService by delegate {
                    override suspend fun sign(
                        keyId: KeyId,
                        data: ByteArray,
                        algorithm: Algorithm?,
                    ): SignResult {
                        signEntered.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            signCancelled.complete(Unit)
                        }
                    }
                }

            val job =
                async(Dispatchers.Default) {
                    DefaultCadesSigner(hungKms).sign(
                        CadesSigningRequest(
                            profile = CadesProfile.B_B,
                            keyId = keyId,
                            payload = "x".toByteArray(),
                            signerCertificateChain = chain,
                        ),
                    )
                }
            withTimeout(10_000) { signEntered.await() }
            job.cancel()
            withTimeout(10_000) { signCancelled.await() }
            assertTrue(job.isCancelled)
        }
}
