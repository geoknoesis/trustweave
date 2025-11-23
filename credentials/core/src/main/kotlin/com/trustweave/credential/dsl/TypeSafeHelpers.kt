package com.trustweave.credential.dsl

/**
 * Type-Safe Helpers.
 * 
 * Provides type-safe constants and helpers to reduce string-based errors.
 * 
 * **Example Usage**:
 * ```kotlin
 * credential {
 *     type(CredentialTypes.EDUCATION)
 *     proofType(ProofTypes.ED25519)
 * }
 * ```
 */

/**
 * Type-safe credential types.
 */
object CredentialTypes {
    const val EDUCATION = "EducationCredential"
    const val EMPLOYMENT = "EmploymentCredential"
    const val CERTIFICATION = "CertificationCredential"
    const val DEGREE = "DegreeCredential"
    const val PERSON = "PersonCredential"
    const val VERIFIABLE_CREDENTIAL = "VerifiableCredential"
}

/**
 * Type-safe proof types.
 */
object ProofTypes {
    const val ED25519 = "Ed25519Signature2020"
    const val JWT = "JsonWebSignature2020"
    const val BBS_BLS = "BbsBlsSignature2020"
}

/**
 * Type-safe DID methods.
 */
object DidMethods {
    const val KEY = "key"
    const val WEB = "web"
    const val ION = "ion"
    const val ETHR = "ethr"
}

/**
 * Type-safe key algorithms.
 */
object KeyAlgorithms {
    const val ED25519 = "Ed25519"
    const val SECP256K1 = "secp256k1"
    const val RSA = "RSA"
}

/**
 * Type-safe status purposes.
 */
object StatusPurposes {
    const val REVOCATION = "revocation"
    const val SUSPENSION = "suspension"
}

/**
 * Type-safe schema validator types.
 */
object SchemaValidatorTypes {
    const val JSON_SCHEMA = "JsonSchemaValidator2018"
    const val SHACL = "ShaclValidator2020"
}

/**
 * Type-safe service types.
 */
object ServiceTypes {
    const val LINKED_DOMAINS = "LinkedDomains"
    const val DID_COMM_MESSAGING = "DIDCommMessaging"
    const val CREDENTIAL_REVOCATION = "CredentialRevocation"
}

/**
 * Type-safe proof purposes.
 */
object ProofPurposes {
    const val ASSERTION_METHOD = "assertionMethod"
    const val AUTHENTICATION = "authentication"
    const val KEY_AGREEMENT = "keyAgreement"
    const val CAPABILITY_INVOCATION = "capabilityInvocation"
    const val CAPABILITY_DELEGATION = "capabilityDelegation"
}


