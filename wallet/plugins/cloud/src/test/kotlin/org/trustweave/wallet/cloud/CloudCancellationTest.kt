package org.trustweave.wallet.cloud

import com.google.cloud.storage.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CloudCancellationTest {
    @Test fun `S3 cancellation stops before fetching the next listing page`() =
        runBlocking {
            val job = Job()
            var calls = 0
            val client =
                object : S3Client {
                    override fun serviceName() = "s3"

                    override fun close() {}

                    override fun listObjectsV2(request: ListObjectsV2Request): ListObjectsV2Response {
                        calls++
                        job.cancel()
                        return ListObjectsV2Response
                            .builder()
                            .isTruncated(calls == 1)
                            .nextContinuationToken("next")
                            .build()
                    }
                }
            val wallet = AwsS3Wallet("w", "did:key:w", "did:key:h", "bucket", "wallet", client)
            assertFailsWith<CancellationException> { withContext(job) { wallet.listRecords() } }
            assertEquals(1, calls)
        }

    private val credential =
        VerifiableCredential(
            type = listOf(CredentialType.Custom("Employee")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromIri("did:key:holder"),
        )

    private fun checkOperations(wallet: CloudWallet) =
        runBlocking {
            val operations: List<suspend () -> Any?> =
                listOf(
                    { wallet.store(credential) },
                    { wallet.get("credential") },
                    { wallet.delete("credential") },
                    { wallet.listRecords() },
                )
            operations.forEach { operation ->
                assertEquals("cancelled by provider", assertFailsWith<CancellationException> { operation() }.message)
            }
        }

    @Test fun `S3 preserves cancellation for every storage operation`() {
        val client =
            object : S3Client {
                override fun serviceName() = "s3"

                override fun close() {}

                override fun putObject(
                    request: PutObjectRequest,
                    body: RequestBody,
                ): Nothing = throw CancellationException("cancelled by provider")

                override fun getObject(request: GetObjectRequest): Nothing = throw CancellationException("cancelled by provider")

                override fun deleteObject(request: DeleteObjectRequest): Nothing = throw CancellationException("cancelled by provider")

                override fun listObjectsV2(request: ListObjectsV2Request): Nothing = throw CancellationException("cancelled by provider")
            }
        checkOperations(AwsS3Wallet("w", "did:key:w", "did:key:h", "bucket", "wallet", client))
    }

    @Test fun `Google storage preserves cancellation for every storage operation`() {
        val client =
            Proxy.newProxyInstance(Storage::class.java.classLoader, arrayOf(Storage::class.java)) { _, _, _ ->
                throw CancellationException("cancelled by provider")
            } as Storage
        checkOperations(GoogleCloudStorageWallet("w", "did:key:w", "did:key:h", "bucket", "wallet", client))
    }
}
