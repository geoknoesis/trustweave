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

/** Real HTTP/S3 SDK contract against MinIO; this is not a claim of live AWS coverage. */
class S3StorageContractTest {
    private companion object {
        /**
         * Pinned by digest on MinIO's own registry.
         *
         * The Docker Hub copy of this release is no longer resolvable, so the previous
         * `minio/minio:RELEASE.…` reference failed to pull on any runner without a warm cache —
         * it passed locally and failed in CI. A digest also makes the image immutable, which a
         * `RELEASE.` tag is not: MinIO retags and removes them.
         */
        const val MINIO_IMAGE =
            "quay.io/minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e"
    }

    @Test
    fun `S3 storage preserves anonymous credentials paginates and reports corrupt objects`() =
        runBlocking {
            val storage =
                GenericContainer<Nothing>(MINIO_IMAGE).apply {
                    withEnv("MINIO_ROOT_USER", "contract-user")
                    withEnv("MINIO_ROOT_PASSWORD", "contract-password")
                    withCommand("server", "/data")
                    withExposedPorts(9000)
                    waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))
                }
            storage.start()
            try {
                var listCalls = 0
                S3Client
                    .builder()
                    .endpointOverride(URI("http://${storage.host}:${storage.getMappedPort(9000)}"))
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
