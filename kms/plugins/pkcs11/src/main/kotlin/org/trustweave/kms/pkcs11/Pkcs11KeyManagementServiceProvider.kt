package org.trustweave.kms.pkcs11

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for [Pkcs11KeyManagementService].
 *
 * Automatically discovered via [java.util.ServiceLoader] when this module is on the classpath.
 * Discovery file: `META-INF/services/org.trustweave.kms.spi.KeyManagementServiceProvider`.
 *
 * **Required options** (passed via [create]):
 *  - `libraryPath: String` — absolute path to the vendor PKCS#11 shared library.
 *
 * **Optional options:**
 *  - `slot: Int` (default 0)
 *  - `providerName: String` (default "TrustWeave-PKCS11")
 *  - `pin: CharArray` *or* `pin: String` (string PINs are converted to CharArray)
 *  - `enableSoftDelete: Boolean` (default false)
 *
 * **Example:**
 * ```kotlin
 * val kms = KeyManagementServices.create("pkcs11", mapOf(
 *     "libraryPath" to "/usr/lib/softhsm/libsofthsm2.so",
 *     "slot" to 0,
 *     "pin" to "1234"
 * ))
 * ```
 */
class Pkcs11KeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "pkcs11"

    override val supportedAlgorithms: Set<Algorithm> = Pkcs11KeyManagementService.SUPPORTED_ALGORITHMS

    /**
     * PKCS#11 needs a configured library path; there is no useful environment-variable default
     * (the library is a per-deployment file system path). Returning an empty list keeps the
     * provider available for SPI discovery; callers must still pass `libraryPath` in options.
     */
    override val requiredEnvironmentVariables: List<String> = emptyList()

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val libraryPath = (options[KEY_LIBRARY_PATH] as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(
                "PKCS#11 KMS requires '$KEY_LIBRARY_PATH' in options (absolute path to the vendor " +
                    "PKCS#11 shared library, e.g. /usr/lib/softhsm/libsofthsm2.so).",
            )

        val slot = when (val raw = options[KEY_SLOT]) {
            null -> 0
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
                ?: throw IllegalArgumentException("PKCS#11 option '$KEY_SLOT' must be an integer; got '$raw'")
            else -> throw IllegalArgumentException(
                "PKCS#11 option '$KEY_SLOT' must be an integer; got ${raw::class.simpleName}",
            )
        }

        val providerName = (options[KEY_PROVIDER_NAME] as? String)?.takeIf { it.isNotBlank() }
            ?: "TrustWeave-PKCS11"

        val pin: CharArray? = when (val raw = options[KEY_PIN]) {
            null -> null
            is CharArray -> raw
            is String -> raw.toCharArray()
            else -> throw IllegalArgumentException(
                "PKCS#11 option '$KEY_PIN' must be a String or CharArray; got ${raw::class.simpleName}",
            )
        }

        val enableSoftDelete = when (val raw = options[KEY_ENABLE_SOFT_DELETE]) {
            null -> false
            is Boolean -> raw
            is String -> raw.toBooleanStrictOrNull()
                ?: throw IllegalArgumentException(
                    "PKCS#11 option '$KEY_ENABLE_SOFT_DELETE' must be a boolean; got '$raw'",
                )
            else -> throw IllegalArgumentException(
                "PKCS#11 option '$KEY_ENABLE_SOFT_DELETE' must be a boolean; got ${raw::class.simpleName}",
            )
        }

        val config = Pkcs11Config(
            libraryPath = libraryPath,
            slot = slot,
            providerName = providerName,
            pin = pin,
            enableSoftDelete = enableSoftDelete,
        )
        return Pkcs11KeyManagementService(config)
    }

    companion object {
        /** Option key: absolute path to the vendor PKCS#11 shared library. Required. */
        const val KEY_LIBRARY_PATH: String = "libraryPath"

        /** Option key: PKCS#11 slot index. Optional; defaults to 0. */
        const val KEY_SLOT: String = "slot"

        /** Option key: friendly provider name suffix. Optional; defaults to "TrustWeave-PKCS11". */
        const val KEY_PROVIDER_NAME: String = "providerName"

        /** Option key: user PIN. Optional. */
        const val KEY_PIN: String = "pin"

        /** Option key: whether to ignore deletion failures. Optional; defaults to false. */
        const val KEY_ENABLE_SOFT_DELETE: String = "enableSoftDelete"
    }
}
