package org.trustweave.credential.mdl

import org.trustweave.core.exception.TrustWeaveException

class MdocException(
    override val code: String,
    override val message: String,
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, emptyMap(), cause) {
    companion object {
        fun digestMismatch(namespace: String, digestId: Int) =
            MdocException("DIGEST_MISMATCH", "MSO digest mismatch in namespace=$namespace digestId=$digestId")

        fun invalidCoseSign1(reason: String) =
            MdocException("INVALID_COSE_SIGN1", "Invalid COSE_Sign1: $reason")

        fun unsupportedDocType(docType: String) =
            MdocException("UNSUPPORTED_DOC_TYPE", "Unsupported docType: $docType")

        fun missingDeviceKey() =
            MdocException("MISSING_DEVICE_KEY", "DeviceKeyInfo is required in MSO for device binding")
    }
}
