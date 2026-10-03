package org.trustweave.kms.thalesluna

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

/**
 * SPI entry for the **Thales Luna stub**.
 *
 * **NOT FUNCTIONAL.** The Thales Luna plugin has no HSM client behind it, so it is deliberately not
 * advertised as a working provider: [supportedAlgorithms] is empty and [create] validates the
 * options and then fails loudly with [UnsupportedOperationException] instead of handing back a
 * service whose every operation would fail later. The service class remains constructible
 * directly for tests, where each operation returns an explicit "not implemented" `Failure`.
 */
class ThalesLunaKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "thales-luna"

    /** Empty on purpose: this provider cannot perform any operation. */
    override val supportedAlgorithms: Set<Algorithm> = emptySet()

    /**
     * @throws IllegalArgumentException if the options are invalid
     * @throws UnsupportedOperationException always: the Thales Luna plugin is a stub
     */
    override fun create(options: Map<String, Any?>): KeyManagementService {
        // Validate first so configuration mistakes are still reported precisely.
        ThalesLunaKmsConfig.fromMap(options)
        throw UnsupportedOperationException(
            "The Thales Luna KMS plugin is a stub and is not implemented (requires the vendor SDK and HSM access). " +
                "Use a functional provider instead.",
        )
    }
}
