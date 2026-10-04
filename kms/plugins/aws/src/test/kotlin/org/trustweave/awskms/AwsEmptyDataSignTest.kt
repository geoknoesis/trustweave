package org.trustweave.awskms

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.results.SignResult
import software.amazon.awssdk.services.kms.KmsClient
import java.lang.reflect.Proxy
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AwsEmptyDataSignTest {
    /** A client that fails the test if the service is ever called: the rejection must be local. */
    private val untouchedClient: KmsClient =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(KmsClient::class.java)) { _, method, _ ->
            if (method.name == "close") null else throw AssertionError("AWS must not be called: ${method.name}")
        } as KmsClient

    private val kms =
        AwsKeyManagementService(
            AwsKmsConfig.builder().region("us-east-1").build(),
            untouchedClient,
        )

    @Test
    fun `empty data is rejected locally with a message naming the AWS constraint`() =
        runBlocking<Unit> {
            val result = kms.sign(KeyId("alias/example"), ByteArray(0))

            val failure = assertIs<SignResult.Failure.Error>(result)
            assertTrue("AWS KMS" in failure.reason, failure.reason)
            assertTrue("1 to 4096 bytes" in failure.reason, failure.reason)
        }
}
