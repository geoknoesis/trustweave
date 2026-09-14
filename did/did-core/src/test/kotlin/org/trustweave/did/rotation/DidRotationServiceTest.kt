package org.trustweave.did.rotation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.DidState
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.model.UpdateDidOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Rotation replaces a key, and the reason it usually happens is that the old key is compromised.
 *
 * That makes the interesting question not "is the new key present" but "is the old key gone from
 * everywhere". A DID document declares five verification relationships, and they are what say
 * what a key is still allowed to do — so a rotation that repoints two of them and leaves three
 * pointing at the retired key has not rotated anything for those three purposes, and has left a
 * document whose references do not resolve.
 */
class DidRotationServiceTest {
    private val did = Did("did:example:subject")
    private val oldKeyId = "did:example:subject#key-1"
    private val newKeyId = "did:example:subject#key-2"

    private fun vmId(fragment: String) = VerificationMethodId(did = did, keyId = KeyId(fragment))

    private fun method(fragment: String) =
        VerificationMethod(
            id = vmId(fragment),
            type = "Ed25519VerificationKey2020",
            controller = did,
            publicKeyMultibase = "z$fragment",
        )

    private val oldKey = method("key-1")
    private val newKey = method("key-2")

    /** A document where the old key holds every relationship, which is the case that matters. */
    private fun document(
        methods: List<VerificationMethod> = listOf(oldKey),
        relationships: List<VerificationMethodId> = listOf(vmId("key-1")),
    ) = DidDocument(
        id = did,
        verificationMethod = methods,
        authentication = relationships,
        assertionMethod = relationships,
        keyAgreement = relationships,
        capabilityInvocation = relationships,
        capabilityDelegation = relationships,
    )

    private class RecordingRegistrar(
        private val state: OperationState = OperationState.FINISHED,
        private val failWith: Throwable? = null,
    ) : DidRegistrar {
        var updated: DidDocument? = null

        override suspend fun createDid(
            method: String,
            options: CreateDidOptions,
        ): DidRegistrationResponse = DidRegistrationResponse(didState = DidState(state = state, did = "did:$method:new"))

        override suspend fun updateDid(
            did: String,
            document: DidDocument,
            options: UpdateDidOptions,
        ): DidRegistrationResponse {
            failWith?.let { throw it }
            updated = document
            return DidRegistrationResponse(didState = DidState(state = state))
        }

        override suspend fun deactivateDid(
            did: String,
            options: DeactivateDidOptions,
        ): DidRegistrationResponse = DidRegistrationResponse(didState = DidState(state = state))
    }

    private fun service(
        resolution: DidResolutionResult = DidResolutionResult.Success(document()),
        registrar: DidRegistrar = RecordingRegistrar(),
    ) = DefaultDidRotationService(registrar = registrar, resolver = DidResolver { resolution })

    // ---------------------------------------------------------------- the point of rotating

    @Test
    fun `a rotated-out key is left in no relationship at all`() =
        runBlocking<Unit> {
            val registrar = RecordingRegistrar()
            val result = service(registrar = registrar).rotateVerificationMethod(did, oldKeyId, newKey)

            assertTrue(result.success, result.error ?: "")
            val updated = assertNotNull(registrar.updated)

            val everyReference =
                updated.authentication + updated.assertionMethod + updated.keyAgreement +
                    updated.capabilityInvocation + updated.capabilityDelegation
            assertFalse(
                everyReference.any { it.value == oldKeyId },
                "the retired key still holds relationships: ${everyReference.map { it.value }}",
            )
            assertTrue(everyReference.all { it.value == newKeyId })
        }

    @Test
    fun `the capability relationships are repointed, not only authentication and assertion`() =
        runBlocking<Unit> {
            // Named separately because these three were the ones a "similar updates for other
            // relationship arrays" comment used to stand in for. capabilityInvocation and
            // capabilityDelegation are precisely the relationships that grant authority.
            val registrar = RecordingRegistrar()
            service(registrar = registrar).rotateVerificationMethod(did, oldKeyId, newKey)
            val updated = assertNotNull(registrar.updated)

            assertEquals(listOf(newKeyId), updated.keyAgreement.map { it.value })
            assertEquals(listOf(newKeyId), updated.capabilityInvocation.map { it.value })
            assertEquals(listOf(newKeyId), updated.capabilityDelegation.map { it.value })
        }

    @Test
    fun `no reference is left dangling outside verificationMethod`() =
        runBlocking<Unit> {
            val registrar = RecordingRegistrar()
            service(registrar = registrar).rotateVerificationMethod(did, oldKeyId, newKey)
            val updated = assertNotNull(registrar.updated)

            val declared = updated.verificationMethod.map { it.id.value }.toSet()
            val referenced =
                (
                    updated.authentication + updated.assertionMethod + updated.keyAgreement +
                        updated.capabilityInvocation + updated.capabilityDelegation
                ).map { it.value }.toSet()
            assertTrue(
                declared.containsAll(referenced),
                "references point at methods the document does not declare: ${referenced - declared}",
            )
        }

    @Test
    fun `the old key is removed and the new one added`() =
        runBlocking<Unit> {
            val registrar = RecordingRegistrar()
            service(registrar = registrar).rotateVerificationMethod(did, oldKeyId, newKey)
            val updated = assertNotNull(registrar.updated)
            assertEquals(listOf(newKeyId), updated.verificationMethod.map { it.id.value })
        }

    @Test
    fun `relationships belonging to other keys are left alone`() =
        runBlocking<Unit> {
            val other = method("key-9")
            val resolution =
                DidResolutionResult.Success(
                    document(
                        methods = listOf(oldKey, other),
                        relationships = listOf(vmId("key-1"), vmId("key-9")),
                    ),
                )
            val registrar = RecordingRegistrar()
            service(resolution = resolution, registrar = registrar)
                .rotateVerificationMethod(did, oldKeyId, newKey)
            val updated = assertNotNull(registrar.updated)

            assertEquals(listOf(newKeyId, "did:example:subject#key-9"), updated.authentication.map { it.value })
            assertTrue(updated.verificationMethod.any { it.id.value == "did:example:subject#key-9" })
        }

    // ---------------------------------------------------------------- refusals

    @Test
    fun `rotating a key the document does not have is refused`() =
        runBlocking<Unit> {
            val registrar = RecordingRegistrar()
            val result =
                service(registrar = registrar)
                    .rotateVerificationMethod(did, "did:example:subject#never-existed", newKey)
            assertFalse(result.success)
            assertTrue("not found" in (result.error ?: "").lowercase(), result.error ?: "")
            assertEquals(null, registrar.updated, "nothing should be written when the key is unknown")
        }

    @Test
    fun `an unresolvable DID is refused rather than rotated blind`() =
        runBlocking<Unit> {
            val registrar = RecordingRegistrar()
            val result =
                service(resolution = DidResolutionResult.Failure.NotFound(did), registrar = registrar)
                    .rotateVerificationMethod(did, oldKeyId, newKey)
            assertFalse(result.success)
            assertEquals(null, registrar.updated)
        }

    @Test
    fun `a registrar that refuses the update is reported as a failed rotation`() =
        runBlocking<Unit> {
            val result =
                service(registrar = RecordingRegistrar(state = OperationState.FAILED))
                    .rotateVerificationMethod(did, oldKeyId, newKey)
            assertFalse(result.success, "an unfinished registrar operation is not a completed rotation")
        }

    @Test
    fun `a registrar that throws is reported rather than propagated`() =
        runBlocking<Unit> {
            val result =
                service(registrar = RecordingRegistrar(failWith = IllegalStateException("registrar down")))
                    .rotateVerificationMethod(did, oldKeyId, newKey)
            assertFalse(result.success)
            assertTrue("registrar down" in (result.error ?: ""), result.error ?: "")
        }

    // ---------------------------------------------------------------- controller rotation

    @Test
    fun `rotating the controller replaces it and keeps the keys`() =
        runBlocking<Unit> {
            val registrar = RecordingRegistrar()
            val newController = Did("did:example:new-controller")
            val result = service(registrar = registrar).rotateController(did, newController)

            assertTrue(result.success)
            val updated = assertNotNull(registrar.updated)
            assertEquals(listOf(newController), updated.controller)
            assertEquals(listOf(oldKeyId), updated.verificationMethod.map { it.id.value })
        }

    @Test
    fun `controller rotation refuses an unresolvable DID`() =
        runBlocking<Unit> {
            val registrar = RecordingRegistrar()
            val result =
                service(resolution = DidResolutionResult.Failure.NotFound(did), registrar = registrar)
                    .rotateController(did, Did("did:example:new-controller"))
            assertFalse(result.success)
            assertEquals(null, registrar.updated)
        }

    // ---------------------------------------------------------------- whole-DID rotation

    @Test
    fun `the migration guide names every relationship the old DID declared`() =
        runBlocking<Unit> {
            // A guide listing only authentication and assertionMethod tells an operator the
            // migration is done while capabilityInvocation still points at the old DID.
            val result =
                service().rotateDid(did, NewDidOptions(method = "example"))

            assertTrue(result.success, result.error ?: "")
            val guide = assertNotNull(result.migrationGuide)
            assertEquals(listOf(oldKeyId), guide.relationshipsToUpdate, "one key in five relationships is one entry")
        }

    @Test
    fun `the migration guide covers relationships no other array mentions`() =
        runBlocking<Unit> {
            val resolution =
                DidResolutionResult.Success(
                    DidDocument(
                        id = did,
                        verificationMethod = listOf(oldKey, method("key-9")),
                        authentication = listOf(vmId("key-1")),
                        capabilityDelegation = listOf(vmId("key-9")),
                    ),
                )
            val guide = assertNotNull(service(resolution = resolution).rotateDid(did, NewDidOptions("example")).migrationGuide)
            assertTrue(
                "did:example:subject#key-9" in guide.relationshipsToUpdate,
                "a key held only by capabilityDelegation must still be migrated: ${guide.relationshipsToUpdate}",
            )
        }

    @Test
    fun `a DID rotation that cannot create the new DID is refused`() =
        runBlocking<Unit> {
            val result =
                service(registrar = RecordingRegistrar(state = OperationState.FAILED))
                    .rotateDid(did, NewDidOptions(method = "example"))
            assertFalse(result.success)
            assertEquals(did, result.oldDid)
        }

    @Test
    fun `a DID rotation that cannot resolve the old DID is refused`() =
        runBlocking<Unit> {
            val result =
                service(resolution = DidResolutionResult.Failure.NotFound(did))
                    .rotateDid(did, NewDidOptions(method = "example"))
            assertFalse(result.success)
        }

    // ---------------------------------------------------------------- how a refusal is explained

    @Test
    fun `a resolution error is reported with the resolver's own reason`() =
        runBlocking<Unit> {
            // The error a caller sees should name what actually went wrong. Collapsing every
            // resolution failure into one message is how an operator ends up checking DNS for a
            // problem that was a timeout.
            val result =
                service(
                    resolution =
                        DidResolutionResult.Failure.ResolutionError(did = did, reason = "upstream registry timed out"),
                ).rotateVerificationMethod(did, oldKeyId, newKey)

            assertFalse(result.success)
            assertTrue("upstream registry timed out" in (result.error ?: ""), result.error ?: "")
        }

    @Test
    fun `a not-found DID says so rather than reporting an unknown error`() =
        runBlocking<Unit> {
            val result =
                service(resolution = DidResolutionResult.Failure.NotFound(did))
                    .rotateVerificationMethod(did, oldKeyId, newKey)
            assertTrue("not found" in (result.error ?: "").lowercase(), result.error ?: "")
        }

    @Test
    fun `a failure shape with no dedicated message still produces one`() =
        runBlocking<Unit> {
            // MethodNotRegistered has no branch of its own, so it takes the fallback. Asserting
            // it produces *something* keeps the fallback from being a silent empty string.
            val result =
                service(
                    resolution = DidResolutionResult.Failure.MethodNotRegistered(method = "example"),
                ).rotateVerificationMethod(did, oldKeyId, newKey)

            assertFalse(result.success)
            assertTrue((result.error ?: "").isNotBlank(), "a refusal must explain itself")
        }

    @Test
    fun `a registrar that throws during controller rotation is reported`() =
        runBlocking<Unit> {
            val result =
                service(registrar = RecordingRegistrar(failWith = IllegalStateException("registrar down")))
                    .rotateController(did, Did("did:example:new-controller"))
            assertFalse(result.success)
            assertTrue("registrar down" in (result.error ?: ""), result.error ?: "")
        }

    @Test
    fun `a new DID that finishes without returning a DID is refused`() =
        runBlocking<Unit> {
            // FINISHED with no did is a registrar contract violation, and rotating onto a DID
            // nobody named would leave the caller believing it had migrated somewhere.
            val registrar =
                object : DidRegistrar by RecordingRegistrar() {
                    override suspend fun createDid(
                        method: String,
                        options: CreateDidOptions,
                    ): DidRegistrationResponse = DidRegistrationResponse(didState = DidState(OperationState.FINISHED, did = null))
                }
            val result = service(registrar = registrar).rotateDid(did, NewDidOptions(method = "example"))

            assertFalse(result.success)
            assertTrue("no DID returned" in (result.error ?: ""), result.error ?: "")
            assertEquals(did, result.oldDid, "the caller still needs to know which DID it tried to rotate")
        }

    @Test
    fun `method-specific options are passed to the registrar`() =
        runBlocking<Unit> {
            var seen: CreateDidOptions? = null
            val registrar =
                object : DidRegistrar by RecordingRegistrar() {
                    override suspend fun createDid(
                        method: String,
                        options: CreateDidOptions,
                    ): DidRegistrationResponse {
                        seen = options
                        return DidRegistrationResponse(
                            didState = DidState(state = OperationState.FINISHED, did = "did:example:new"),
                        )
                    }
                }
            val result =
                service(registrar = registrar)
                    .rotateDid(did, NewDidOptions(method = "example", options = mapOf("network" to JsonPrimitive("testnet"))))

            assertTrue(result.success, result.error ?: "")
            assertEquals("did:example:new", result.newDid?.value)
            assertTrue(
                assertNotNull(seen).methodSpecificOptions.containsKey("network"),
                "a caller's method options must reach the registrar, or they are decoration",
            )
        }
}
