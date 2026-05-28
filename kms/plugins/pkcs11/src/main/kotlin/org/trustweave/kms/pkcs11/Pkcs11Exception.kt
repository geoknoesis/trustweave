package org.trustweave.kms.pkcs11

/**
 * Internal exception for unrecoverable PKCS#11 errors.
 *
 * This exception never escapes the [org.trustweave.kms.KeyManagementService] SPI boundary —
 * all SPI methods translate it (and any underlying JCA/PKCS#11 exception) into the appropriate
 * sealed [org.trustweave.kms.results] failure variant. It exists only to give internal helpers
 * a single type to throw and catch for unexpected HSM failures.
 */
class Pkcs11Exception(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
