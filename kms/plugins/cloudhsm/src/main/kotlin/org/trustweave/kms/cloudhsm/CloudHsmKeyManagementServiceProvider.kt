package org.trustweave.kms.cloudhsm

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for [CloudHsmKeyManagementService].
 *
 * Discovered via [java.util.ServiceLoader] when this module is on the classpath.
 *
 * # Configuration
 *
 * Either pass options to [create] explicitly, or set the environment variables
 * documented on [CloudHsmKmsConfig.fromEnvironment]. The HSM password itself is
 * ALWAYS read from an environment variable (default `AWS_CLOUDHSM_HSM_PASSWORD`)
 * to keep secrets out of the options map.
 *
 * ```kotlin
 * val kms = KeyManagementServices.create("cloudhsm", mapOf(
 *     "clusterId" to "cluster-abcd1234efg",
 *     "hsmUser" to "trustweave-cu",
 *     "region" to "us-east-1",
 *     // AWS_CLOUDHSM_HSM_PASSWORD env var must be set
 * ))
 * ```
 */
class CloudHsmKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "cloudhsm"

    override val supportedAlgorithms: Set<Algorithm> = CloudHsmKeyManagementService.SUPPORTED_ALGORITHMS

    /**
     * Environment variables required for the env-driven configuration path. When the
     * options map provides `clusterId`/`hsmUser` directly, the env vars are not
     * strictly required (apart from the password) — but advertising them here lets
     * test harnesses auto-skip when CloudHSM credentials are absent.
     */
    override val requiredEnvironmentVariables: List<String> = listOf(
        "AWS_CLOUDHSM_CLUSTER_ID",
        "AWS_CLOUDHSM_HSM_USER",
        "AWS_CLOUDHSM_HSM_PASSWORD",
        "AWS_CLOUDHSM_PKCS11_LIBRARY",
    )

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = if (options.containsKey(CloudHsmKmsConfig.KEY_CLUSTER_ID)) {
            CloudHsmKmsConfig.fromMap(options)
        } else {
            CloudHsmKmsConfig.fromEnvironment()
                ?: throw IllegalArgumentException(
                    "CloudHSM KMS requires either '${CloudHsmKmsConfig.KEY_CLUSTER_ID}' + " +
                        "'${CloudHsmKmsConfig.KEY_HSM_USER}' in options, or the " +
                        "AWS_CLOUDHSM_CLUSTER_ID + AWS_CLOUDHSM_HSM_USER environment variables.",
                )
        }
        return CloudHsmKeyManagementService(config)
    }
}
