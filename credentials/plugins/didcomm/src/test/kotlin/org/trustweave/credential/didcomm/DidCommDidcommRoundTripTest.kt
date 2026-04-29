package org.trustweave.credential.didcomm

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.secret.Secret
import org.trustweave.credential.didcomm.crypto.interop.MapSecretResolver
import org.trustweave.credential.didcomm.protocol.BasicMessageProtocol
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
class DidCommDidcommRoundTripTest {

    @Test
    fun packAndUnpackEncryptedBasicMessage() = runBlocking {
        val gen = OctetKeyPairGenerator(Curve.X25519)
        val aliceKp = gen.generate()
        val bobKp = gen.generate()

        val aliceDid = "did:key:alice"
        val bobDid = "did:key:bob"
        val aliceVm = "$aliceDid#agreement-1"
        val bobVm = "$bobDid#agreement-1"

        fun publicJwkMap(kp: com.nimbusds.jose.jwk.OctetKeyPair): Map<String, Any?> =
            mapOf(
                "kty" to kp.keyType.value,
                "crv" to kp.curve.name,
                "x" to kp.x.toString(),
            )

        fun docFor(did: String, vm: String, kp: com.nimbusds.jose.jwk.OctetKeyPair): DidDocument {
            val d = Did(did)
            val vmId = VerificationMethodId.parse(vm)
            return DidDocument(
                id = d,
                verificationMethod = listOf(
                    VerificationMethod(
                        id = vmId,
                        type = "JsonWebKey2020",
                        controller = d,
                        publicKeyJwk = publicJwkMap(kp),
                    ),
                ),
                keyAgreement = listOf(vmId),
            )
        }

        val aliceDoc = docFor(aliceDid, aliceVm, aliceKp)
        val bobDoc = docFor(bobDid, bobVm, bobKp)

        val resolveDid: suspend (String) -> DidDocument? = { did ->
            when (did) {
                aliceDid -> aliceDoc
                bobDid -> bobDoc
                else -> null
            }
        }

        val secrets = MapSecretResolver()
        secrets.put(
            aliceVm,
            Secret(
                aliceVm,
                VerificationMethodType.JSON_WEB_KEY_2020,
                VerificationMaterial(VerificationMaterialFormat.JWK, aliceKp.toJSONString()),
            ),
        )
        secrets.put(
            bobVm,
            Secret(
                bobVm,
                VerificationMethodType.JSON_WEB_KEY_2020,
                VerificationMaterial(VerificationMaterialFormat.JWK, bobKp.toJSONString()),
            ),
        )

        val kms = InMemoryKeyManagementService()
        val packer = DidCommFactory.createPacker(kms, resolveDid, secrets)

        val message = BasicMessageProtocol.createBasicMessage(
            fromDid = aliceDid,
            toDid = bobDid,
            content = "hello didcomm-java",
        )

        val packed = packer.pack(
            message = message,
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = true,
            sign = false,
        )

        val unpacked = packer.unpack(
            packedMessage = packed,
            recipientDid = bobDid,
            recipientKeyId = bobVm,
            senderDid = aliceDid,
        )

        assertEquals(message.id, unpacked.id)
        assertEquals(message.type, unpacked.type)
        assertEquals("hello didcomm-java", unpacked.body["content"]?.jsonPrimitive?.content)
    }
}
