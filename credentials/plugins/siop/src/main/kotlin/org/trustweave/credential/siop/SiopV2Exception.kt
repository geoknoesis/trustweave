package org.trustweave.credential.siop

import org.trustweave.core.exception.TrustWeaveException

class SiopV2Exception(override val code: String, override val message: String, cause: Throwable? = null) :
    TrustWeaveException(code, message, emptyMap(), cause)
