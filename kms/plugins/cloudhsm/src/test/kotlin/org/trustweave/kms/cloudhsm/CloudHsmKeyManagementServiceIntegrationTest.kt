package org.trustweave.kms.cloudhsm

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.trustweave.kms.Algorithm
import org.trustweave.kms.pkcs11.Pkcs11KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import software.amazon.awssdk.services.cloudhsmv2.model.ClusterState
import java.nio.file.Files
import java.nio.file.Path

/**
 * End-to-end integration tests for [CloudHsmKeyManagementService] against a real
 * AWS CloudHSM cluster.
 *
 * # Why these tests are gated
 *
 * There is no Docker image or sandbox for CloudHSM. The PKCS#11 library is shipped
 * with the AWS-provided CloudHSM Client SDK 5 and only links against a cluster you
 * have provisioned in your AWS account. These tests therefore run *only* when the
 * required environment variables are set.
 *
 * # Required environment variables
 *
 *  - `AWS_CLOUDHSM_CLUSTER_ID` — e.g. `cluster-abcd1234efg`
 *  - `AWS_CLOUDHSM_HSM_USER` — a pre-created Crypto User on the cluster
 *  - `AWS_CLOUDHSM_HSM_PASSWORD` — password for that CU
 *  - `AWS_CLOUDHSM_PKCS11_LIBRARY` — absolute path to `libcloudhsm_pkcs11.so`
 *
 *  Optional:
 *  - `AWS_REGION` (default `us-east-1`)
 *  - `AWS_CLOUDHSM_PARTITION` (default `PARTITION_1`)
 *  - `AWS_CLOUDHSM_SLOT` (default `0`)
 *
 * Standard AWS credentials must also be available (env vars, profile, or EC2 role)
 * for the [software.amazon.awssdk.services.cloudhsmv2.CloudHsmV2Client] used by the
 * cluster-management helpers.
 *
 * # What is verified
 *
 *  - The cluster is reachable via CloudHsmV2 and reports state `ACTIVE`.
 *  - `generateKey(P256)` produces a usable key on the HSM.
 *  - `sign(P256)` returns a non-empty signature.
 *  - `getPublicKey` round-trips the generated key.
 *  - `deleteKey` actually removes the key.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(
    named = "AWS_CLOUDHSM_CLUSTER_ID",
    matches = "cluster-.+",
    disabledReason = "CloudHSM integration tests require AWS_CLOUDHSM_CLUSTER_ID, " +
        "AWS_CLOUDHSM_HSM_USER, AWS_CLOUDHSM_HSM_PASSWORD, and AWS_CLOUDHSM_PKCS11_LIBRARY.",
)
@EnabledIfEnvironmentVariable(
    named = "AWS_CLOUDHSM_HSM_USER",
    matches = ".+",
    disabledReason = "AWS_CLOUDHSM_HSM_USER not set",
)
@EnabledIfEnvironmentVariable(
    named = "AWS_CLOUDHSM_HSM_PASSWORD",
    matches = ".+",
    disabledReason = "AWS_CLOUDHSM_HSM_PASSWORD not set",
)
@EnabledIfEnvironmentVariable(
    named = "AWS_CLOUDHSM_PKCS11_LIBRARY",
    matches = ".+",
    disabledReason = "AWS_CLOUDHSM_PKCS11_LIBRARY not set",
)
@DisplayName("CloudHsmKeyManagementService — live cluster integration")
class CloudHsmKeyManagementServiceIntegrationTest {

    private lateinit var kms: CloudHsmKeyManagementService

    @BeforeAll
    fun setUp() {
        val libraryPath = requireNotNull(System.getenv("AWS_CLOUDHSM_PKCS11_LIBRARY")) {
            "AWS_CLOUDHSM_PKCS11_LIBRARY env var is required"
        }
        check(Files.exists(Path.of(libraryPath))) {
            "AWS_CLOUDHSM_PKCS11_LIBRARY points at '$libraryPath' but no such file exists"
        }
        val config = requireNotNull(CloudHsmKmsConfig.fromEnvironment()) {
            "CloudHsmKmsConfig.fromEnvironment() returned null; check AWS_CLOUDHSM_* env vars"
        }
        kms = CloudHsmKeyManagementService(config)
    }

    @AfterAll
    fun tearDown() {
        if (::kms.isInitialized) kms.close()
    }

    @Test
    fun `cluster is ACTIVE`() = runTest {
        val state = kms.getClusterState()
        assertEquals(
            ClusterState.ACTIVE,
            state,
            "expected cluster to be ACTIVE; got $state",
        )
    }

    @Test
    fun `requireClusterActive does not throw`() = runTest {
        kms.requireClusterActive()
    }

    @Test
    fun `generateKey sign and deleteKey round-trip on the HSM`() = runTest {
        val label = "trustweave-it-${System.nanoTime()}"
        val gen = kms.generateKey(
            Algorithm.P256,
            options = mapOf(Pkcs11KeyManagementService.OPTION_LABEL to label),
        )
        assertTrue(gen is GenerateKeyResult.Success, "generateKey failed: $gen")
        val handle = (gen as GenerateKeyResult.Success).keyHandle
        assertEquals(label, handle.id.value)

        val pub = kms.getPublicKey(handle.id)
        assertTrue(pub is GetPublicKeyResult.Success, "getPublicKey failed: $pub")

        val signed = kms.sign(handle.id, "trustweave-cloudhsm-it".toByteArray(Charsets.UTF_8), Algorithm.P256)
        assertTrue(signed is SignResult.Success, "sign failed: $signed")
        val signature = (signed as SignResult.Success).signature
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty(), "signature must be non-empty")

        val del = kms.deleteKey(handle.id)
        assertTrue(del is DeleteKeyResult.Deleted, "deleteKey failed: $del")

        val gone = kms.getPublicKey(handle.id)
        assertTrue(
            gone is GetPublicKeyResult.Failure.KeyNotFound,
            "expected KeyNotFound after delete, got $gone",
        )
    }
}
