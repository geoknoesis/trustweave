package org.trustweave.core.util

import org.trustweave.core.exception.TrustWeaveException

/**
 * JVM-only extension on [ValidationResult] for projecting an Invalid result onto the
 * TrustWeave exception hierarchy. Lives here (not in the multiplatform `ValidationResult`
 * class itself) because [TrustWeaveException] is still JVM-only.
 *
 * @return null if valid, [TrustWeaveException.ValidationFailed] if invalid
 */
fun ValidationResult.toException(): TrustWeaveException.ValidationFailed? =
    when (this) {
        is ValidationResult.Valid -> null
        is ValidationResult.Invalid -> TrustWeaveException.ValidationFailed(
            field = field,
            reason = message,
            value = value
        )
    }
