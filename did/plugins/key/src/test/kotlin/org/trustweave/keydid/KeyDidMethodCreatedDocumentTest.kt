package org.trustweave.keydid

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A did:key created by this instance must resolve to the document `createDid` produced, whose verification
 * method fragment is the real KMS key id. Re-deriving it from the DID string instead gives the multibase as the
 * fragment, and signing then asks the KMS for a key that does not exist.
 */
class KeyDidMethodCreatedDocumentTest {
    @Test
    fun `resolving a did created here returns the document with the real KMS key id`() =
        runBlocking {
            val method = KeyDidMethod(InMemoryKeyManagementService())
            val created = method.createDid(DidCreationOptions())

            val resolved = method.resolveDid(created.id)

            assertTrue(resolved is DidResolutionResult.Success, "expected Success, got $resolved")
            assertEquals(
                created.verificationMethod
                    .first()
                    .id.value,
                resolved.document.verificationMethod
                    .first()
                    .id.value,
                "resolution must keep the KMS key id as the verification method fragment",
            )
        }

    @Test
    fun `resolving a did that was not created here is derived from the did itself`() =
        runBlocking {
            val creator = KeyDidMethod(InMemoryKeyManagementService())
            val did = creator.createDid(DidCreationOptions()).id
            val other = KeyDidMethod(InMemoryKeyManagementService())

            val first = other.resolveDid(did)
            val second = other.resolveDid(did)

            assertTrue(first is DidResolutionResult.Success)
            assertTrue(second is DidResolutionResult.Success)
            // Derived from the DID itself: the fragment is the multibase key, deterministically, on every call.
            val fragment = did.value.substringAfter("did:key:")
            assertEquals(
                "${did.value}#$fragment",
                first.document.verificationMethod
                    .first()
                    .id.value,
            )
            assertEquals(
                first.document.verificationMethod
                    .first()
                    .id.value,
                second.document.verificationMethod
                    .first()
                    .id.value,
            )
        }
}
