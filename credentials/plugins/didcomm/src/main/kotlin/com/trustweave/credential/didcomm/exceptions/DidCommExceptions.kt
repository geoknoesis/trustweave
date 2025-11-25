package com.trustweave.credential.didcomm.exceptions

import com.trustweave.core.exception.TrustWeaveException

/**
 * Exception thrown when DIDComm message packing fails.
 */
class DidCommPackingException(
    message: String,
    cause: Throwable? = null
) : TrustWeaveException.Unknown(
    message = message,
    context = emptyMap(),
    cause = cause
)

/**
 * Exception thrown when DIDComm message unpacking fails.
 */
class DidCommUnpackingException(
    message: String,
    cause: Throwable? = null
) : TrustWeaveException.Unknown(
    message = message,
    context = emptyMap(),
    cause = cause
)

/**
 * Exception thrown when DIDComm message encryption fails.
 */
class DidCommEncryptionException(
    message: String,
    cause: Throwable? = null
) : TrustWeaveException.Unknown(
    message = message,
    context = emptyMap(),
    cause = cause
)

/**
 * Exception thrown when DIDComm message decryption fails.
 */
class DidCommDecryptionException(
    message: String,
    cause: Throwable? = null
) : TrustWeaveException.Unknown(
    message = message,
    context = emptyMap(),
    cause = cause
)

/**
 * Exception thrown when a required DIDComm protocol field is missing.
 */
class DidCommProtocolException(
    message: String,
    cause: Throwable? = null
) : TrustWeaveException.Unknown(
    message = message,
    context = emptyMap(),
    cause = cause
)

