package org.trustweave.wallet.cloud

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real HTTP/S3 SDK contract against an S3-compatible server (Adobe S3Mock); this is not a claim of live AWS coverage. */
class S3StorageContractTest {
    private companion object {
        /**
         * Adobe S3Mock, pinned by digest (the multi-arch index of `adobe/s3mock:latest`).
         *
         * MinIO withdrew its public images: `quay.io/minio/minio` and Docker Hub's `minio/minio` now
         * answer 401, so every runner without a warm cache failed to pull. This test only needs the
         * plain S3 API (buckets, put/get/list with continuation tokens, delete), which S3Mock
         * implements. A digest also makes the image immutable, which a tag is not.
         */
        const val S3_PORT = 9090

        const val S3_IMAGE =
            "adobe/s3mock@sha256:ab01a6946750f451ca215a47e91030695b260e4003b8a5a6201d25029b8fca92"
    }

    @Test
    fun `S3 storage preserves anonymous credentials paginates and reports corrupt objects`() =
        runBlocking {
            val storage =
                GenericContainer<Nothing>(S3_IMAGE).apply {
                    withExposedPorts(S3_PORT)
                    waitingFor(Wait.forListeningPort())
                }
            storage.start()
            try {
                var listCalls = 0
                S3Client
                    .builder()
                    .endpointOverride(URI("http://${storage.host}:${storage.getMappedPort(S3_PORT)}"))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("contract-user", "contract-password")))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .overrideConfiguration {
                        it
                            .apiCallTimeout(java.time.Duration.ofSeconds(10))
                            .addExecutionInterceptor(
                                object : software.amazon.awssdk.core.interceptor.ExecutionInterceptor {
                                    override fun modifyRequest(
                                        context: software.amazon.awssdk.core.interceptor.Context.ModifyRequest,
                                        attributes: software.amazon.awssdk.core.interceptor.ExecutionAttributes,
                                    ): software.amazon.awssdk.core.SdkRequest {
                                        val request = context.request()
                                        if (request is ListObjectsV2Request) {
                                            listCalls++
                                            return request.toBuilder().maxKeys(2).build()
                                        }
                                        return request
                                    }
                                },
                            )
                    }.build()
                    .use { client ->
                        val bucket = "wallet-contract"
                        client.createBucket { it.bucket(bucket) }
                        val wallet = AwsS3Wallet("contract", "did:key:w", "did:key:h", bucket, "contract", client)
                        val credential =
                            VerifiableCredential(
                                type = listOf(CredentialType.Custom("Employee")),
                                issuer = Issuer.fromDid(Did("did:key:issuer")),
                                credentialSubject = CredentialSubject.fromIri("did:key:holder"),
                            )
                        val anonymousId = wallet.store(credential)
                        assertNull(wallet.get(anonymousId)?.id)
                        repeat(5) { index ->
                            val id = "record-$index"
                            val bytes = Json.encodeToString(VerifiableCredential.serializer(), credential.copy(id = CredentialId(id)))
                            client.putObject({ it.bucket(bucket).key("contract/credentials/$id.json") }, RequestBody.fromString(bytes))
                        }
                        val records = wallet.listRecords()
                        assertEquals(6, records.size)
                        assertEquals(6, records.map { it.storageId }.toSet().size)
                        // S3-compatible services may require a final empty page to end traversal.
                        assertTrue(listCalls in 3..4)
                        assertTrue(records.any { it.storageId == anonymousId && it.credential.id == null })
                        val other = AwsS3Wallet("other", "did:key:o", "did:key:o", bucket, "other", client)
                        assertTrue(other.listRecords().isEmpty())
                        client.putObject({ it.bucket(bucket).key("contract/credentials/broken.json") }, RequestBody.fromString("not-json"))
                        assertFailsWith<Exception> { wallet.listRecords() }
                        val recovered = wallet.recoverRecords()
                        assertFalse(recovered.complete)
                        assertEquals(6, recovered.records.size)
                        assertEquals(1, recovered.failures.size)
                        assertTrue(wallet.delete(anonymousId))
                        assertNull(wallet.get(anonymousId))
                    }
            } finally {
                storage.stop()
            }
        }
}
