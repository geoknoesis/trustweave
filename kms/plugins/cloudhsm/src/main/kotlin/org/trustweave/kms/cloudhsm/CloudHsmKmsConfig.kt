package org.trustweave.kms.cloudhsm

/**
 * Configuration for the AWS CloudHSM-backed [org.trustweave.kms.KeyManagementService].
 *
 * AWS CloudHSM exposes a standards-compliant PKCS#11 interface via the
 * `libcloudhsm_pkcs11.so` (Linux) / `cloudhsm_pkcs11.dll` (Windows) shared library
 * shipped with the CloudHSM Client SDK 5. This config carries the parameters needed
 * to (a) authenticate against the cluster (HSM user + password) and (b) load the
 * PKCS#11 library from disk.
 *
 * The CloudHSM PKCS#11 login uses the composite PIN `"<hsmUser>:<hsmPassword>"`; the
 * password is read from an environment variable (never inlined in code or config files)
 * to keep secrets out of build artifacts.
 *
 * @property clusterId AWS CloudHSM cluster identifier, e.g. `cluster-abcd1234efg`.
 *                     Used for the cluster management helpers (DescribeClusters);
 *                     not consumed by the PKCS#11 layer.
 * @property region AWS region the cluster lives in. Defaults to `us-east-1`.
 * @property hsmUser CloudHSM Crypto User (CU) name. Created via the CloudHSM CLI
 *                   (`cloudhsm-cli user create`). Required.
 * @property hsmPasswordEnvVar Name of the environment variable that holds the HSM
 *                              user's password. The actual password is resolved at
 *                              construction time via [resolvedHsmPassword]; the raw
 *                              string is never stored in the config object.
 *                              Defaults to `AWS_CLOUDHSM_HSM_PASSWORD`.
 * @property partition CloudHSM partition / slot label, typically `PARTITION_1` for
 *                     Client SDK 5. Optional; defaults to `PARTITION_1`.
 * @property pkcs11LibraryPath Absolute path to the CloudHSM PKCS#11 shared library.
 *                              Linux default: `/opt/cloudhsm/lib/libcloudhsm_pkcs11.so`.
 *                              Windows default: `C:\Program Files\Amazon\CloudHSM\lib\cloudhsm_pkcs11.dll`.
 * @property slot PKCS#11 slot index. CloudHSM Client SDK 5 always presents the cluster
 *                on slot 0; override only if you have a custom configuration.
 * @property classicLoadBalancerEndpoint Optional. The classic cluster load-balancer
 *                                        endpoint (`<cluster-id>.cloudhsm.<region>.amazonaws.com`)
 *                                        used by tooling that bypasses Client SDK
 *                                        auto-discovery. The PKCS#11 library reads its
 *                                        cluster topology from its own configuration
 *                                        file, so this field is informational only.
 * @property enableSoftDelete When `true`, [org.trustweave.kms.results.DeleteKeyResult]
 *                            returns `Deleted` even if the underlying token refuses to
 *                            destroy the key object. CloudHSM Crypto Users CAN delete
 *                            their own keys, so this defaults to `false`.
 */
data class CloudHsmKmsConfig(
    val clusterId: String,
    val region: String = DEFAULT_REGION,
    val hsmUser: String,
    val hsmPasswordEnvVar: String = DEFAULT_PASSWORD_ENV_VAR,
    val partition: String = DEFAULT_PARTITION,
    val pkcs11LibraryPath: String = defaultPkcs11LibraryPath(),
    val slot: Int = 0,
    val classicLoadBalancerEndpoint: String? = null,
    val enableSoftDelete: Boolean = false,
) {
    init {
        require(clusterId.isNotBlank()) {
            "CloudHsmKmsConfig.clusterId must not be blank"
        }
        require(CLUSTER_ID_PATTERN.matches(clusterId)) {
            "CloudHsmKmsConfig.clusterId must match 'cluster-<alphanumeric>' (got '$clusterId')"
        }
        require(region.isNotBlank()) {
            "CloudHsmKmsConfig.region must not be blank"
        }
        require(hsmUser.isNotBlank()) {
            "CloudHsmKmsConfig.hsmUser must not be blank"
        }
        require(hsmPasswordEnvVar.isNotBlank()) {
            "CloudHsmKmsConfig.hsmPasswordEnvVar must not be blank"
        }
        require(pkcs11LibraryPath.isNotBlank()) {
            "CloudHsmKmsConfig.pkcs11LibraryPath must not be blank"
        }
        require(partition.isNotBlank()) {
            "CloudHsmKmsConfig.partition must not be blank"
        }
        require(slot >= 0) {
            "CloudHsmKmsConfig.slot must be >= 0 (got $slot)"
        }
    }

    /**
     * Reads the HSM user password from the environment variable named by
     * [hsmPasswordEnvVar]. Returns `null` if the variable is unset or blank.
     *
     * The password is read on demand to avoid keeping it resident in the
     * config object's heap representation longer than necessary.
     */
    fun resolvedHsmPassword(): String? =
        System.getenv(hsmPasswordEnvVar)?.takeIf { it.isNotEmpty() }

    /**
     * Composes the CloudHSM PKCS#11 login PIN as `"<hsmUser>:<hsmPassword>"`.
     *
     * @throws IllegalStateException if the password env var is not set; callers
     *                               must surface this as a configuration error.
     */
    fun composeLoginPin(): CharArray {
        val password = resolvedHsmPassword()
            ?: throw IllegalStateException(
                "HSM password environment variable '$hsmPasswordEnvVar' is not set. " +
                    "Export it (e.g. `export $hsmPasswordEnvVar='...'`) before constructing " +
                    "the CloudHSM KMS.",
            )
        val composed = "$hsmUser:$password"
        return composed.toCharArray()
    }

    /**
     * String form deliberately omits the password env var *value* (read separately).
     */
    override fun toString(): String =
        "CloudHsmKmsConfig(clusterId='$clusterId', region='$region', hsmUser='$hsmUser', " +
            "partition='$partition', pkcs11LibraryPath='$pkcs11LibraryPath', slot=$slot, " +
            "classicLoadBalancerEndpoint=${classicLoadBalancerEndpoint?.let { "'$it'" }}, " +
            "enableSoftDelete=$enableSoftDelete, hsmPasswordEnvVar='$hsmPasswordEnvVar')"

    companion object {
        /** Default AWS region. CloudHSM is region-scoped; override per deployment. */
        const val DEFAULT_REGION: String = "us-east-1"

        /** Default environment variable name for the HSM user password. */
        const val DEFAULT_PASSWORD_ENV_VAR: String = "AWS_CLOUDHSM_HSM_PASSWORD"

        /** Default partition label used by CloudHSM Client SDK 5. */
        const val DEFAULT_PARTITION: String = "PARTITION_1"

        /** Linux default path for the CloudHSM PKCS#11 shared library. */
        const val LINUX_PKCS11_LIBRARY: String = "/opt/cloudhsm/lib/libcloudhsm_pkcs11.so"

        /** Windows default path for the CloudHSM PKCS#11 shared library. */
        const val WINDOWS_PKCS11_LIBRARY: String =
            "C:\\Program Files\\Amazon\\CloudHSM\\lib\\cloudhsm_pkcs11.dll"

        /**
         * AWS CloudHSM cluster identifiers have the format `cluster-<alphanumeric>`.
         * See https://docs.aws.amazon.com/cloudhsm/latest/userguide/clusters.html.
         */
        private val CLUSTER_ID_PATTERN: Regex = Regex("^cluster-[a-z0-9]+$")

        /** Picks the platform-appropriate default PKCS#11 library path. */
        fun defaultPkcs11LibraryPath(): String {
            val os = System.getProperty("os.name")?.lowercase().orEmpty()
            return if (os.contains("win")) WINDOWS_PKCS11_LIBRARY else LINUX_PKCS11_LIBRARY
        }

        /**
         * Loads a config from environment variables. Returns `null` if the minimum
         * required variables (cluster ID + HSM user) are not present. The password
         * env var name itself is configurable; the *value* of that env var is read
         * lazily via [resolvedHsmPassword].
         *
         * Recognized env vars:
         *  - `AWS_CLOUDHSM_CLUSTER_ID` (required)
         *  - `AWS_CLOUDHSM_HSM_USER` (required)
         *  - `AWS_CLOUDHSM_HSM_PASSWORD_ENV` (optional; defaults to `AWS_CLOUDHSM_HSM_PASSWORD`)
         *  - `AWS_REGION` / `AWS_DEFAULT_REGION` (optional; defaults to `us-east-1`)
         *  - `AWS_CLOUDHSM_PARTITION` (optional)
         *  - `AWS_CLOUDHSM_PKCS11_LIBRARY` (optional; platform default otherwise)
         *  - `AWS_CLOUDHSM_SLOT` (optional; defaults to 0)
         *  - `AWS_CLOUDHSM_CLB_ENDPOINT` (optional)
         */
        fun fromEnvironment(): CloudHsmKmsConfig? {
            val clusterId = System.getenv("AWS_CLOUDHSM_CLUSTER_ID")?.takeIf { it.isNotBlank() }
                ?: return null
            val hsmUser = System.getenv("AWS_CLOUDHSM_HSM_USER")?.takeIf { it.isNotBlank() }
                ?: return null
            val region = System.getenv("AWS_REGION")?.takeIf { it.isNotBlank() }
                ?: System.getenv("AWS_DEFAULT_REGION")?.takeIf { it.isNotBlank() }
                ?: DEFAULT_REGION
            val passwordEnvVar = System.getenv("AWS_CLOUDHSM_HSM_PASSWORD_ENV")
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_PASSWORD_ENV_VAR
            val partition = System.getenv("AWS_CLOUDHSM_PARTITION")?.takeIf { it.isNotBlank() }
                ?: DEFAULT_PARTITION
            val libraryPath = System.getenv("AWS_CLOUDHSM_PKCS11_LIBRARY")?.takeIf { it.isNotBlank() }
                ?: defaultPkcs11LibraryPath()
            val slot = System.getenv("AWS_CLOUDHSM_SLOT")?.toIntOrNull() ?: 0
            val clbEndpoint = System.getenv("AWS_CLOUDHSM_CLB_ENDPOINT")?.takeIf { it.isNotBlank() }

            return CloudHsmKmsConfig(
                clusterId = clusterId,
                region = region,
                hsmUser = hsmUser,
                hsmPasswordEnvVar = passwordEnvVar,
                partition = partition,
                pkcs11LibraryPath = libraryPath,
                slot = slot,
                classicLoadBalancerEndpoint = clbEndpoint,
            )
        }

        /**
         * Builds a config from a generic options map. Used by the SPI provider.
         *
         * @throws IllegalArgumentException if required keys are missing or have the wrong type.
         */
        fun fromMap(options: Map<String, Any?>): CloudHsmKmsConfig {
            val clusterId = options[KEY_CLUSTER_ID] as? String
                ?: throw IllegalArgumentException(
                    "CloudHSM KMS requires '$KEY_CLUSTER_ID' in options " +
                        "(e.g. 'cluster-abcd1234efg').",
                )
            val hsmUser = options[KEY_HSM_USER] as? String
                ?: throw IllegalArgumentException(
                    "CloudHSM KMS requires '$KEY_HSM_USER' in options " +
                        "(the CloudHSM Crypto User name).",
                )
            val region = (options[KEY_REGION] as? String)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_REGION
            val passwordEnvVar = (options[KEY_HSM_PASSWORD_ENV_VAR] as? String)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_PASSWORD_ENV_VAR
            val partition = (options[KEY_PARTITION] as? String)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_PARTITION
            val libraryPath = (options[KEY_PKCS11_LIBRARY_PATH] as? String)?.takeIf { it.isNotBlank() }
                ?: defaultPkcs11LibraryPath()
            val slot = when (val raw = options[KEY_SLOT]) {
                null -> 0
                is Int -> raw
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull()
                    ?: throw IllegalArgumentException(
                        "CloudHSM option '$KEY_SLOT' must be an integer; got '$raw'",
                    )
                else -> throw IllegalArgumentException(
                    "CloudHSM option '$KEY_SLOT' must be an integer; got ${raw::class.simpleName}",
                )
            }
            val clbEndpoint = (options[KEY_CLB_ENDPOINT] as? String)?.takeIf { it.isNotBlank() }
            val enableSoftDelete = when (val raw = options[KEY_ENABLE_SOFT_DELETE]) {
                null -> false
                is Boolean -> raw
                is String -> raw.toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException(
                        "CloudHSM option '$KEY_ENABLE_SOFT_DELETE' must be a boolean; got '$raw'",
                    )
                else -> throw IllegalArgumentException(
                    "CloudHSM option '$KEY_ENABLE_SOFT_DELETE' must be a boolean; got ${raw::class.simpleName}",
                )
            }

            return CloudHsmKmsConfig(
                clusterId = clusterId,
                region = region,
                hsmUser = hsmUser,
                hsmPasswordEnvVar = passwordEnvVar,
                partition = partition,
                pkcs11LibraryPath = libraryPath,
                slot = slot,
                classicLoadBalancerEndpoint = clbEndpoint,
                enableSoftDelete = enableSoftDelete,
            )
        }

        /** Options-map key: cluster id. Required. */
        const val KEY_CLUSTER_ID: String = "clusterId"

        /** Options-map key: region. Optional. */
        const val KEY_REGION: String = "region"

        /** Options-map key: HSM user (CU) name. Required. */
        const val KEY_HSM_USER: String = "hsmUser"

        /** Options-map key: name of env var holding HSM password. Optional. */
        const val KEY_HSM_PASSWORD_ENV_VAR: String = "hsmPasswordEnvVar"

        /** Options-map key: partition label. Optional. */
        const val KEY_PARTITION: String = "partition"

        /** Options-map key: PKCS#11 library path. Optional (platform default used otherwise). */
        const val KEY_PKCS11_LIBRARY_PATH: String = "pkcs11LibraryPath"

        /** Options-map key: PKCS#11 slot index. Optional. */
        const val KEY_SLOT: String = "slot"

        /** Options-map key: classic load-balancer endpoint. Optional. */
        const val KEY_CLB_ENDPOINT: String = "classicLoadBalancerEndpoint"

        /** Options-map key: whether to ignore delete failures. Optional. */
        const val KEY_ENABLE_SOFT_DELETE: String = "enableSoftDelete"
    }
}
