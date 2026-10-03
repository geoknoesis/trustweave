package org.trustweave.did.model

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId

/**
 * Re-expresses a document that was produced for [from] as a document for [to], consistently.
 *
 * Methods that delegate to another method (did:polygon and did:ens over did:ethr) must not
 * merely `copy(id = ...)`: that leaves every verification method id, controller and relationship
 * reference pointing at the delegate's DID, so the returned document names itself `did:polygon:X`
 * while its keys are `did:ethr:X#controller` controlled by `did:ethr:X`. Verifiers that resolve
 * a key reference against the document id then fail or, worse, trust a key under the wrong DID.
 *
 * This rewrites, for references that are exactly [from] (or a DID URL under it):
 * - the document `id`, `controller` entries and service ids
 * - every verification method `id` and `controller`
 * - every relationship reference (authentication, assertionMethod, keyAgreement,
 *   capabilityInvocation, capabilityDelegation)
 *
 * Controllers and key references that name some *other* DID are left untouched: they are
 * external, not an alias of the subject.
 */
public fun DidDocument.rebasedTo(
    from: Did,
    to: Did,
): DidDocument {
    fun Did.map(): Did = if (value == from.value) to else this

    fun VerificationMethodId.map(): VerificationMethodId = if (did.value == from.value) VerificationMethodId(to, keyId) else this

    fun String.mapUrl(): String =
        when {
            this == from.value -> to.value
            startsWith(from.value + "#") -> to.value + substring(from.value.length)
            else -> this
        }
    return copy(
        id = id.map(),
        controller = controller.map { it.map() },
        verificationMethod =
            verificationMethod.map {
                it.copy(id = it.id.map(), controller = it.controller.map())
            },
        authentication = authentication.map { it.map() },
        assertionMethod = assertionMethod.map { it.map() },
        keyAgreement = keyAgreement.map { it.map() },
        capabilityInvocation = capabilityInvocation.map { it.map() },
        capabilityDelegation = capabilityDelegation.map { it.map() },
        service = service.map { it.copy(id = it.id.mapUrl()) },
    )
}
