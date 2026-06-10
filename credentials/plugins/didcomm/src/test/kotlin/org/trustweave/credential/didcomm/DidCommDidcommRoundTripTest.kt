package org.trustweave.credential.didcomm

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DidCommDidcommRoundTripTest {

    private val aliceDid = "did:key:alice"
    private val bobDid = "did:key:bob"
    private val charlieDid = "did:key:charlie"
    private val aliceVm = "$aliceDid#agreement-1"
    private val bobVm = "$bobDid#agreement-1"
    private val charlieVm = "$charlieDid#agreement-1"

    private val aliceKp = generateX25519()
    private val bobKp = generateX25519()
    private val charlieKp = generateX25519()

    private val docs = mapOf(
        aliceDid to docFor(aliceDid, aliceVm, aliceKp),
        bobDid to docFor(bobDid, bobVm, bobKp),
        charlieDid to docFor(charlieDid, charlieVm, charlieKp),
    )
    private val resolveDid: suspend (String) -> DidDocument? = { did -> docs[did] }
    private val kms = InMemoryKeyManagementService()

    private fun generateX25519(): OctetKeyPair = OctetKeyPairGenerator(Curve.X25519).generate()

    private fun publicJwkMap(kp: OctetKeyPair): Map<String, Any?> =
        mapOf(
            "kty" to kp.keyType.value,
            "crv" to kp.curve.name,
            "x" to kp.x.toString(),
        )

    private fun docFor(did: String, vm: String, kp: OctetKeyPair): DidDocument {
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

    private fun secretFor(vm: String, kp: OctetKeyPair): Secret =
        Secret(
            vm,
            VerificationMethodType.JSON_WEB_KEY_2020,
            VerificationMaterial(VerificationMaterialFormat.JWK, kp.toJSONString()),
        )

    private fun resolverWith(vararg entries: Pair<String, OctetKeyPair>): MapSecretResolver {
        val secrets = MapSecretResolver()
        entries.forEach { (vm, kp) -> secrets.put(vm, secretFor(vm, kp)) }
        return secrets
    }

    private fun encryptedKeyOf(packed: String): String {
        val json = Json.parseToJsonElement(packed).jsonObject
        return json["recipients"]!!.jsonArray.first()
            .jsonObject["encrypted_key"]!!.jsonPrimitive.content
    }

    @Test
    fun packAndUnpackEncryptedBasicMessage() = runBlocking {
        val secrets = resolverWith(aliceVm to aliceKp, bobVm to bobKp)
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

    @Test
    fun encryptedKeyDependsOnRecipientKeyPairAndIsNeverAllZeros() = runBlocking {
        val secrets = resolverWith(aliceVm to aliceKp, bobVm to bobKp, charlieVm to charlieKp)
        val packer = DidCommFactory.createPacker(kms, resolveDid, secrets)

        val packedForBob = packer.pack(
            message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "key dependence"),
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = true,
            sign = false,
        )
        val packedForCharlie = packer.pack(
            message = BasicMessageProtocol.createBasicMessage(aliceDid, charlieDid, "key dependence"),
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = charlieDid,
            toKeyId = charlieVm,
            encrypt = true,
            sign = false,
        )

        val bobEncryptedKey = encryptedKeyOf(packedForBob)
        val charlieEncryptedKey = encryptedKeyOf(packedForCharlie)

        // The wrapped CEK must depend on the recipient key pair (ECDH-derived KEK).
        assertNotEquals(bobEncryptedKey, charlieEncryptedKey)

        // And must never be the output of a constant (e.g. all-zero) secret/key.
        for (encryptedKey in listOf(bobEncryptedKey, charlieEncryptedKey)) {
            val bytes = Base64.getUrlDecoder().decode(encryptedKey)
            assertTrue(bytes.isNotEmpty(), "encrypted_key must not be empty")
            assertTrue(bytes.any { it != 0.toByte() }, "encrypted_key must not be all zeros")
        }
    }

    @Test
    fun unpackFailsWhenRecipientSecretIsMissing() = runBlocking {
        val senderSecrets = resolverWith(aliceVm to aliceKp, bobVm to bobKp)
        val sender = DidCommFactory.createPacker(kms, resolveDid, senderSecrets)

        val packed = sender.pack(
            message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "secret"),
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = true,
            sign = false,
        )

        // Charlie has no secret for bob's kid: decryption must fail, never succeed silently.
        val charlie = DidCommFactory.createPacker(kms, resolveDid, resolverWith(charlieVm to charlieKp))
        assertFails {
            charlie.unpack(
                packedMessage = packed,
                recipientDid = bobDid,
                recipientKeyId = bobVm,
                senderDid = aliceDid,
            )
        }
        Unit
    }

    @Test
    fun unpackFailsWithWrongPrivateKeyForRecipientKid() = runBlocking {
        val senderSecrets = resolverWith(aliceVm to aliceKp, bobVm to bobKp)
        val sender = DidCommFactory.createPacker(kms, resolveDid, senderSecrets)

        val packed = sender.pack(
            message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "secret"),
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = true,
            sign = false,
        )

        // An attacker who maps bob's kid to a different private key derives a different
        // ECDH shared secret, so authenticated decryption must fail.
        val mallorySecrets = MapSecretResolver()
        mallorySecrets.put(bobVm, secretFor(bobVm, charlieKp))
        val mallory = DidCommFactory.createPacker(kms, resolveDid, mallorySecrets)
        assertFails {
            mallory.unpack(
                packedMessage = packed,
                recipientDid = bobDid,
                recipientKeyId = bobVm,
                senderDid = aliceDid,
            )
        }
        Unit
    }
}
