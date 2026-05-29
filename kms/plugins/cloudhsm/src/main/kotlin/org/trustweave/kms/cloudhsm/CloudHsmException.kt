package org.trustweave.kms.cloudhsm

/**
 * Internal exception for unrecoverable AWS CloudHSM configuration / cluster errors.
 *
 * Like [org.trustweave.kms.pkcs11.Pkcs11Exception], this never escapes the
 * [org.trustweave.kms.KeyManagementService] SPI boundary; SPI methods translate it
 * into the appropriate sealed `Result.Failure` variant. It is used for problems
 * specific to CloudHSM (cluster state queries, login PIN composition, etc.) that
 * are not naturally PKCS#11 errors.
 */
class CloudHsmException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
