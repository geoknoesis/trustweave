package org.trustweave.kms.cloudhsm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.pkcs11.Pkcs11Config
import org.trustweave.kms.pkcs11.Pkcs11KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudhsmv2.CloudHsmV2Client
import software.amazon.awssdk.services.cloudhsmv2.model.Cluster
import software.amazon.awssdk.services.cloudhsmv2.model.ClusterState
import software.amazon.awssdk.services.cloudhsmv2.model.DescribeClustersRequest
import java.io.Closeable

/**
 * AWS CloudHSM-backed [KeyManagementService].
 *
 * AWS CloudHSM exposes a PKCS#11 interface via `libcloudhsm_pkcs11.so` (Linux) /
 * `cloudhsm_pkcs11.dll` (Windows). This service composes the CloudHSM-specific
 * login PIN (`hsmUser:password`) and delegates all cryptographic operations to
 * [Pkcs11KeyManagementService].
 *
 * The CloudHSM cluster management API (CloudHsmV2) is wrapped as helpers
 * ([describeCluster], [getClusterState], [requireClusterActive]) that operators
 * can call to verify the cluster is ready before issuing keys; these helpers are
 * NOT used by the SPI methods.
 *
 * # Requirements
 *
 *  - **CloudHSM Client SDK 5** installed on the host. The PKCS#11 library reads
 *    its cluster topology from `/opt/cloudhsm/etc/cloudhsm-pkcs11.cfg` (Linux),
 *    which the `cloudhsm-cli` tool configures during cluster bootstrap.
 *  - A **Crypto User (CU)** created on the cluster (`cloudhsm-cli user create`).
 *  - The CU password exported in the env var named by
 *    [CloudHsmKmsConfig.hsmPasswordEnvVar] (default `AWS_CLOUDHSM_HSM_PASSWORD`).
 *  - AWS credentials available to the default credential provider chain (env vars,
 *    profile, or EC2 instance role) for the cluster-management helpers.
 *
 * # Supported algorithms
 *
 * Mirrors the PKCS#11 plugin: Ed25519, P-256, P-384, P-521, RSA-2048 / 3072 / 4096.
 * CloudHSM does not currently expose secp256k1 via PKCS#11.
 *
 * @param config CloudHSM-specific configuration. The PKCS#11 layer is initialized
 *               eagerly so library-loading errors surface at construction.
 * @param cloudHsmV2ClientFactory Factory for the cluster-management client; defaults
 *                                 to building one for [CloudHsmKmsConfig.region].
 *                                 Override in tests with a stub.
 */
class CloudHsmKeyManagementService(
    private val config: CloudHsmKmsConfig,
    cloudHsmV2ClientFactory: (CloudHsmKmsConfig) -> CloudHsmV2Client = ::defaultCloudHsmV2Client,
) : KeyManagementService, Closeable {

    private val logger = LoggerFactory.getLogger(CloudHsmKeyManagementService::class.java)

    /**
     * The underlying PKCS#11 KMS instance, configured against the CloudHSM PKCS#11
     * library and the composite CloudHSM PIN.
     */
    private val delegate: Pkcs11KeyManagementService = run {
        val pin = try {
            config.composeLoginPin()
        } catch (e: IllegalStateException) {
            throw CloudHsmException(
                "Failed to compose CloudHSM login PIN: ${e.message ?: e.javaClass.simpleName}",
                e,
            )
        }
        val pkcs11Config = Pkcs11Config(
            libraryPath = config.pkcs11LibraryPath,
            slot = config.slot,
            providerName = providerNameFor(config),
            pin = pin,
            enableSoftDelete = config.enableSoftDelete,
        )
        try {
            Pkcs11KeyManagementService(pkcs11Config)
        } catch (e: Exception) {
            // Re-wrap as a CloudHsmException so callers see the CloudHSM context, but
            // keep the underlying Pkcs11Exception as the cause for diagnostics.
            throw CloudHsmException(
                "Failed to initialize CloudHSM PKCS#11 layer (clusterId=${config.clusterId}, " +
                    "libraryPath='${config.pkcs11LibraryPath}'): ${e.message ?: e.javaClass.simpleName}",
                e,
            )
        }
    }

    /**
     * Cluster-management client. Built lazily; closed by [close].
     */
    private val cloudHsmClient: CloudHsmV2Client by lazy { cloudHsmV2ClientFactory(config) }

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> =
        delegate.getSupportedAlgorithms()

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>,
    ): GenerateKeyResult = delegate.generateKey(algorithm, options)

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult =
        delegate.getPublicKey(keyId)

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?,
    ): SignResult = delegate.sign(keyId, data, algorithm)

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult =
        delegate.deleteKey(keyId)

    /**
     * Describes the configured cluster via the CloudHsmV2 API.
     *
     * @return The [Cluster] model object, or `null` if the cluster is not found
     *         in the configured region.
     * @throws CloudHsmException if the AWS call fails.
     */
    suspend fun describeCluster(): Cluster? = withContext(Dispatchers.IO) {
        try {
            val response = cloudHsmClient.describeClusters(
                DescribeClustersRequest.builder()
                    .filters(mapOf("clusterIds" to listOf(config.clusterId)))
                    .build(),
            )
            response.clusters().firstOrNull()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("DescribeClusters failed for clusterId=${config.clusterId}", e)
            throw CloudHsmException(
                "DescribeClusters failed for clusterId=${config.clusterId}: " +
                    "${e.message ?: e.javaClass.simpleName}",
                e,
            )
        }
    }

    /**
     * Returns the current [ClusterState] of the configured cluster, or `null` if
     * the cluster cannot be found.
     */
    suspend fun getClusterState(): ClusterState? = describeCluster()?.state()

    /**
     * Throws [CloudHsmException] unless the cluster is currently `ACTIVE`. Use this
     * during application startup to fail fast on misconfiguration.
     */
    suspend fun requireClusterActive() {
        val state = getClusterState()
            ?: throw CloudHsmException(
                "Cluster '${config.clusterId}' not found in region '${config.region}'.",
            )
        if (state != ClusterState.ACTIVE) {
            throw CloudHsmException(
                "Cluster '${config.clusterId}' is in state $state; expected ACTIVE.",
            )
        }
    }

    /**
     * Returns the CA certificate the cluster issues for HSM identity verification,
     * as a PEM-encoded string, or `null` if the cluster has not been initialized yet.
     */
    suspend fun getClusterCertificateAuthorityPem(): String? =
        describeCluster()?.certificates()?.clusterCertificate()

    override fun close() {
        runCatching { cloudHsmClient.close() }
    }

    companion object {
        /**
         * Algorithm set exposed by the CloudHSM KMS. Mirrors the PKCS#11 plugin since
         * CloudHSM presents itself as a PKCS#11 device.
         */
        val SUPPORTED_ALGORITHMS: Set<Algorithm> = Pkcs11KeyManagementService.SUPPORTED_ALGORITHMS

        /**
         * Derives the SunPKCS11 provider-name suffix used to register the underlying
         * provider. CloudHSM clusters are identified by [CloudHsmKmsConfig.clusterId];
         * we embed it in the provider name so multiple cluster-scoped services can
         * coexist in the same JVM without colliding.
         */
        internal fun providerNameFor(config: CloudHsmKmsConfig): String =
            "TrustWeave-CloudHSM-${config.clusterId}"

        /**
         * Default factory for the CloudHsmV2 management client. Uses AWS SDK v2's
         * default credentials provider chain and the region from the config.
         */
        internal fun defaultCloudHsmV2Client(config: CloudHsmKmsConfig): CloudHsmV2Client =
            CloudHsmV2Client.builder()
                .region(Region.of(config.region))
                .build()
    }
}
