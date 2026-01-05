package org.trustweave.trust.dsl.credential

import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.ProofType

/**
 * Type-Safe Helpers.
 *
 * Provides type-safe constants for string-based identifiers (DID methods, algorithms, providers).
 * For credential and proof types, use CredentialType.* and ProofType.* directly.
 *
 * **Example Usage**:
 * ```kotlin
 * credential {
 *     type(CredentialType.Education)
 *     // Use DidMethods.KEY, KeyAlgorithms.ED25519, etc. for string identifiers
 * }
 * ```
 */

// CredentialTypes and ProofTypes removed - use CredentialType.* and ProofType.* directly

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

/**
 * Type-safe KMS providers.
 * 
 * Use these constants for well-known providers:
 * ```kotlin
 * keys { provider(KmsProviders.IN_MEMORY); algorithm(ED25519) }
 * ```
 * 
 * For third-party/custom providers, use strings directly:
 * ```kotlin
 * keys { provider("myCustomKms"); ... }
 * ```
 */
object KmsProviders {
    const val IN_MEMORY = "inMemory"
    const val AWS = "awsKms"
    const val AZURE = "azureKms"
    const val GOOGLE = "googleKms"
    const val HASHICORP = "hashicorp"
    const val FORTANIX = "fortanix"
    const val THALES = "thales"
    const val CYBERARK = "cyberark"
    const val IBM = "ibm"
}

/**
 * Type-safe blockchain anchor providers.
 * 
 * Use these constants for well-known blockchain providers:
 * ```kotlin
 * anchor { chain("algorand:testnet") { provider(AnchorProviders.ALGORAND) } }
 * ```
 */
object AnchorProviders {
    const val IN_MEMORY = "inMemory"
    const val ALGORAND = "algorand"
    const val ETHEREUM = "ethereum"
    const val POLYGON = "polygon"
    const val BASE = "base"
    const val ARBITRUM = "arbitrum"
}

/**
 * Type-safe trust registry providers.
 */
object TrustProviders {
    const val IN_MEMORY = "inMemory"
}

/**
 * Type-safe revocation providers.
 */
object RevocationProviders {
    const val IN_MEMORY = "inMemory"
}

