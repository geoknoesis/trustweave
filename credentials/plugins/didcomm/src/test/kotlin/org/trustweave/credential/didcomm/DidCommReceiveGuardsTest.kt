package org.trustweave.credential.didcomm

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.secret.Secret
import org.trustweave.credential.didcomm.crypto.interop.MapSecretResolver
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.didcomm.models.DidCommMessageTypes
import org.trustweave.credential.didcomm.protocol.BasicMessageProtocol
import org.trustweave.credential.didcomm.storage.InMemoryDidCommMessageStorage
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Intake guards on [DidCommService.receiveMessage].
 *
 * A DIDComm message is a bearer artifact: anyone who observes a packed message can hand it back
 * to the recipient unchanged, and it decrypts and authenticates exactly as it did the first time.
 * Accepting it twice re-runs whatever the message authorises. Likewise an `expires_time` the
 * sender set is meaningless unless the recipient enforces it.
 */
class DidCommReceiveGuardsTest {
    private val aliceDid = "did:key:alice"
    private val bobDid = "did:key:bob"
    private val aliceVm = "$aliceDid#agreement-1"
    private val bobVm = "$bobDid#agreement-1"

    private val aliceKp = generateX25519()
    private val bobKp = generateX25519()

    private val docs =
        mapOf(
            aliceDid to docFor(aliceDid, aliceVm, aliceKp),
            bobDid to docFor(bobDid, bobVm, bobKp),
        )
    private val resolveDid: suspend (String) -> DidDocument? = { did -> docs[did] }
    private val kms = InMemoryKeyManagementService()

    private fun generateX25519(): OctetKeyPair = OctetKeyPairGenerator(Curve.X25519).generate()

    private fun docFor(
        did: String,
        vm: String,
        kp: OctetKeyPair,
    ): DidDocument {
        val d = Did(did)
        val vmId = VerificationMethodId.parse(vm)
        return DidDocument(
            id = d,
            verificationMethod =
                listOf(
                    VerificationMethod(
                        id = vmId,
                        type = "JsonWebKey2020",
                        controller = d,
                        publicKeyJwk =
                            mapOf(
                                "kty" to kp.keyType.value,
                                "crv" to kp.curve.name,
                                "x" to kp.x.toString(),
                            ),
                    ),
                ),
            keyAgreement = listOf(vmId),
        )
    }

    private fun secrets(): MapSecretResolver {
        val resolver = MapSecretResolver()
        listOf(aliceVm to aliceKp, bobVm to bobKp).forEach { (vm, kp) ->
            resolver.put(
                vm,
                Secret(
                    vm,
                    VerificationMethodType.JSON_WEB_KEY_2020,
                    VerificationMaterial(VerificationMaterialFormat.JWK, kp.toJSONString()),
                ),
            )
        }
        return resolver
    }

    private suspend fun packFromAlice(message: DidCommMessage): String {
        val packer = DidCommFactory.createPacker(kms, resolveDid, secrets())
        return packer.pack(
            message = message,
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = true,
            sign = false,
        )
    }

    private fun bobService(): DidCommService = DidCommFactory.createInMemoryServiceWithSecretResolver(kms, resolveDid, secrets())

    /** The production, storage-backed implementation must enforce the same guards. */
    private fun bobDatabaseService(): DidCommService =
        DatabaseDidCommService(
            packer = DidCommFactory.createPacker(kms, resolveDid, secrets()),
            resolveDid = resolveDid,
            storage = InMemoryDidCommMessageStorage(),
        )

    @Test
    fun `the same packed message is not accepted twice`() =
        runBlocking {
            val packed =
                packFromAlice(
                    BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "transfer the funds"),
                )
            val service = bobService()

            val first = service.receiveMessage(packed, bobDid, bobVm, aliceDid)
            assertEquals("did:key:alice", first.from)

            assertFails("A replayed DIDComm message must be rejected, not processed again") {
                runBlocking { service.receiveMessage(packed, bobDid, bobVm, aliceDid) }
            }
            Unit
        }

    @Test
    fun `a message whose expires_time has passed is rejected`() =
        runBlocking {
            val expired =
                DidCommMessage(
                    id = "expired-message-1",
                    type = DidCommMessageTypes.BASIC_MESSAGE,
                    from = aliceDid,
                    to = listOf(bobDid),
                    body = buildJsonObject { put("content", "too late") },
                    expiresTime = "1000000000", // 2001-09-09, long past
                )
            val packed = packFromAlice(expired)

            assertFails("An expired DIDComm message must be rejected") {
                runBlocking { bobService().receiveMessage(packed, bobDid, bobVm, aliceDid) }
            }
            Unit
        }

    @Test
    fun `a message whose expires_time is in the future is accepted`() =
        runBlocking {
            val farFuture =
                DidCommMessage(
                    id = "future-message-1",
                    type = DidCommMessageTypes.BASIC_MESSAGE,
                    from = aliceDid,
                    to = listOf(bobDid),
                    body = buildJsonObject { put("content", "still valid") },
                    expiresTime = "4102444800", // 2100-01-01
                )
            val packed = packFromAlice(farFuture)

            val received = bobService().receiveMessage(packed, bobDid, bobVm, aliceDid)

            assertEquals("future-message-1", received.id)
        }

    @Test
    fun `the database service also refuses a replayed message`() =
        runBlocking {
            val packed =
                packFromAlice(
                    BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "transfer the funds"),
                )
            val service = bobDatabaseService()

            service.receiveMessage(packed, bobDid, bobVm, aliceDid)

            assertFails("The storage-backed service must reject a replay too") {
                runBlocking { service.receiveMessage(packed, bobDid, bobVm, aliceDid) }
            }
            Unit
        }

    @Test
    fun `the database service also rejects an expired message`() =
        runBlocking {
            val expired =
                DidCommMessage(
                    id = "expired-message-2",
                    type = DidCommMessageTypes.BASIC_MESSAGE,
                    from = aliceDid,
                    to = listOf(bobDid),
                    body = buildJsonObject { put("content", "too late") },
                    expiresTime = "1000000000",
                )
            val packed = packFromAlice(expired)

            assertFails("The storage-backed service must reject an expired message too") {
                runBlocking { bobDatabaseService().receiveMessage(packed, bobDid, bobVm, aliceDid) }
            }
            Unit
        }
}
