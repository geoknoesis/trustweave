package org.trustweave.credential.didcomm.packing

import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.didcomm.crypto.DidCommCryptoAdapter
import org.trustweave.credential.didcomm.exception.DidCommException
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.didcomm.protocol.BasicMessageProtocol
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies that signed plain messages are actually verified on unpack:
 * a tampered or mis-signed message must never be returned (fail closed),
 * while unsigned plain messages (which make no claim) still unpack.
 */
class DidCommPackerSignedMessageTest {

    private val aliceDid = "did:key:alice"
    private val aliceVm = "$aliceDid#key-1"
    private val bobDid = "did:key:bob"
    private val bobVm = "$bobDid#key-1"

    private val kms = InMemoryKeyManagementService()
    private val aliceJwk = generateEd25519Jwk(aliceVm)

    // A second, unrelated Ed25519 key used to simulate key substitution.
    private val intruderJwk = generateEd25519Jwk("$aliceDid#intruder")

    private fun generateEd25519Jwk(keyId: String): Map<String, Any?> = runBlocking {
        when (val result = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to keyId))) {
            is GenerateKeyResult.Success -> result.keyHandle.publicKeyJwk
                ?: error("Generated key has no public JWK")
            else -> error("Failed to generate key: $result")
        }
    }

    private fun docFor(did: String, vm: String, jwk: Map<String, Any?>): DidDocument {
        val d = Did(did)
        val vmId = VerificationMethodId.parse(vm)
        return DidDocument(
            id = d,
            verificationMethod = listOf(
                VerificationMethod(
                    id = vmId,
                    type = "JsonWebKey2020",
                    controller = d,
                    publicKeyJwk = jwk,
                ),
            ),
            authentication = listOf(vmId),
        )
    }

    private val resolveDid: suspend (String) -> DidDocument? = { did ->
        if (did == aliceDid) docFor(aliceDid, aliceVm, aliceJwk) else null
    }

    private val signer: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
        when (val result = kms.sign(KeyId(keyId), data)) {
            is SignResult.Success -> result.signature
            is SignResult.Failure -> throw IllegalStateException("Failed to sign: $result")
        }
    }

    private fun packerWith(resolver: suspend (String) -> DidDocument? = resolveDid): DidCommPacker {
        // Plain (unencrypted) messages never touch the crypto provider; use the fail-closed adapter.
        val crypto = DidCommCryptoAdapter(
            kms = kms,
            resolveDid = resolver,
            useDidcommJava = false,
            secretResolver = null,
        )
        return DidCommPacker(crypto, resolver, signer)
    }

    private suspend fun packSigned(packer: DidCommPacker, message: DidCommMessage): String =
        packer.pack(
            message = message,
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = false,
            sign = true,
        )

    @Test
    fun signedPlainMessageRoundTripVerifies() = runBlocking {
        val packer = packerWith()
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "signed hello")
        val packed = packSigned(packer, message)

        // The protected header must use the JOSE algorithm identifier for Ed25519.
        val protected = Json.parseToJsonElement(packed).jsonObject["signatures"]!!
            .jsonArray.first().jsonObject["protected"]!!.jsonPrimitive.content
        val header = Json.parseToJsonElement(
            String(Base64.getUrlDecoder().decode(protected), Charsets.UTF_8),
        ).jsonObject
        assertEquals("EdDSA", header["alg"]!!.jsonPrimitive.content)
        assertEquals(aliceVm, header["kid"]!!.jsonPrimitive.content)

        val unpacked = packer.unpack(packed, bobDid, bobVm, senderDid = aliceDid)
        assertEquals(message.id, unpacked.id)
        assertEquals("signed hello", BasicMessageProtocol.extractContent(unpacked))
    }

    @Test
    fun tamperedSignedMessageIsRejected() = runBlocking {
        val packer = packerWith()
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "original")
        val packed = packSigned(packer, message)

        val packedJson = Json.parseToJsonElement(packed).jsonObject
        val tampered = JsonObject(
            packedJson.toMutableMap().apply {
                put("body", buildJsonObject { put("content", "tampered") })
            },
        )

        assertFailsWith<DidCommException.UnpackingFailed> {
            packer.unpack(
                Json.encodeToString(JsonObject.serializer(), tampered),
                bobDid,
                bobVm,
                senderDid = aliceDid,
            )
        }
        Unit
    }

    @Test
    fun signatureFromWrongKeyIsRejected() = runBlocking {
        val packer = packerWith()
        val packed = packSigned(packer, BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "hi"))

        // The verifier resolves alice's DID to a different (substituted) public key.
        val verifier = packerWith { did ->
            if (did == aliceDid) docFor(aliceDid, aliceVm, intruderJwk) else null
        }
        assertFailsWith<DidCommException.UnpackingFailed> {
            verifier.unpack(packed, bobDid, bobVm, senderDid = aliceDid)
        }
        Unit
    }

    @Test
    fun algNoneIsRejected() = runBlocking {
        val packer = packerWith()
        val packed = packSigned(packer, BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "hi"))

        val packedJson = Json.parseToJsonElement(packed).jsonObject
        val originalSignature = packedJson["signatures"]!!
            .jsonArray.first().jsonObject["signature"]!!.jsonPrimitive.content
        val noneHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"alg":"none","typ":"JWS","kid":"$aliceVm"}""".toByteArray(Charsets.UTF_8),
        )
        val forged = JsonObject(
            packedJson.toMutableMap().apply {
                put(
                    "signatures",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("protected", noneHeader)
                                put("signature", originalSignature)
                            },
                        )
                    },
                )
            },
        )

        assertFailsWith<DidCommException.UnpackingFailed> {
            packer.unpack(
                Json.encodeToString(JsonObject.serializer(), forged),
                bobDid,
                bobVm,
                senderDid = aliceDid,
            )
        }
        Unit
    }

    @Test
    fun missingProtectedHeaderIsRejected() = runBlocking {
        val packer = packerWith()
        val packed = packSigned(packer, BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "hi"))

        val packedJson = Json.parseToJsonElement(packed).jsonObject
        val originalSignature = packedJson["signatures"]!!
            .jsonArray.first().jsonObject["signature"]!!.jsonPrimitive.content
        val forged = JsonObject(
            packedJson.toMutableMap().apply {
                put(
                    "signatures",
                    buildJsonArray {
                        add(buildJsonObject { put("signature", originalSignature) })
                    },
                )
            },
        )

        assertFailsWith<DidCommException.UnpackingFailed> {
            packer.unpack(
                Json.encodeToString(JsonObject.serializer(), forged),
                bobDid,
                bobVm,
                senderDid = aliceDid,
            )
        }
        Unit
    }

    @Test
    fun unresolvableSignerDidIsRejected() = runBlocking {
        val packer = packerWith()
        val packed = packSigned(packer, BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "hi"))

        val verifier = packerWith { null }
        assertFailsWith<DidCommException.UnpackingFailed> {
            verifier.unpack(packed, bobDid, bobVm, senderDid = aliceDid)
        }
        Unit
    }

    @Test
    fun unsignedPlainMessageStillUnpacks() = runBlocking {
        val packer = packerWith()
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "no claim")
        val packed = packer.pack(
            message = message,
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = false,
            sign = false,
        )

        val unpacked = packer.unpack(packed, bobDid, bobVm)
        assertEquals(message.id, unpacked.id)
        assertEquals("no claim", BasicMessageProtocol.extractContent(unpacked))
    }

    // --- requireSigned: signature stripping must be detectable -------------------------------

    private fun stripSignatures(packed: String): String {
        val stripped = JsonObject(
            Json.parseToJsonElement(packed).jsonObject.filterKeys { it != "signatures" },
        )
        return Json.encodeToString(JsonObject.serializer(), stripped)
    }

    @Test
    fun strippedSignaturesWithRequireSignedAreRejected() = runBlocking {
        val packer = packerWith()
        val packed = packSigned(packer, BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "strip me"))

        val ex = assertFailsWith<DidCommException.UnpackingFailed> {
            packer.unpack(
                stripSignatures(packed),
                bobDid,
                bobVm,
                senderDid = aliceDid,
                requireSigned = true,
            )
        }
        assertTrue(ex.reason.contains("requireSigned"), "Unexpected reason: ${ex.reason}")
    }

    @Test
    fun unsignedMessageWithRequireSignedIsRejected() = runBlocking {
        val packer = packerWith()
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "never signed")
        val packed = packer.pack(
            message = message,
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = false,
            sign = false,
        )

        assertFailsWith<DidCommException.UnpackingFailed> {
            packer.unpack(packed, bobDid, bobVm, requireSigned = true)
        }
        Unit
    }

    @Test
    fun strippedSignaturesWithoutRequireSignedStillUnpack() = runBlocking {
        // Default behavior is unchanged: without requireSigned, a stripped message is
        // indistinguishable from a legitimately unsigned one and still unpacks.
        val packer = packerWith()
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "stripped quietly")
        val packed = packSigned(packer, message)

        val unpacked = packer.unpack(stripSignatures(packed), bobDid, bobVm)
        assertEquals(message.id, unpacked.id)
    }

    @Test
    fun signedMessageWithRequireSignedSurfacesVerifiedSigner() = runBlocking {
        val packer = packerWith()
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "prove it")
        val packed = packSigned(packer, message)

        val result = packer.unpackToResult(packed, bobDid, bobVm, senderDid = aliceDid, requireSigned = true)
        assertEquals(message.id, result.message.id)
        assertEquals(listOf(aliceDid), result.verifiedSignerDids)
        assertEquals(aliceDid, result.verifiedSignerDid)
    }

    @Test
    fun unsignedMessageSurfacesNoVerifiedSigner() = runBlocking {
        val packer = packerWith()
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "no claim")
        val packed = packer.pack(
            message = message,
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = false,
            sign = false,
        )

        val result = packer.unpackToResult(packed, bobDid, bobVm)
        assertEquals(message.id, result.message.id)
        assertTrue(result.verifiedSignerDids.isEmpty())
        assertEquals(null, result.verifiedSignerDid)
    }

    // --- JWS header hygiene: 'crit' and 'b64' must be rejected -------------------------------

    private fun b64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * Builds a plain message carrying a JWS that is *correctly signed* by alice over the exact
     * signing input the verifier reconstructs, but with an attacker-chosen protected header.
     * This isolates header-hygiene checks from ordinary signature-verification failures.
     */
    private suspend fun forgeSignedWithHeader(message: DidCommMessage, headerJson: String): String {
        val packer = packerWith()
        val plain = packer.pack(
            message = message,
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = false,
            sign = false,
        )
        val headerBase64 = b64url(headerJson.toByteArray(Charsets.UTF_8))
        val payloadBase64 = b64url(plain.toByteArray(Charsets.UTF_8))
        val signature = signer("$headerBase64.$payloadBase64".toByteArray(Charsets.US_ASCII), aliceVm)
        val signed = JsonObject(
            Json.parseToJsonElement(plain).jsonObject.toMutableMap().apply {
                put(
                    "signatures",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("protected", headerBase64)
                                put("signature", b64url(signature))
                            },
                        )
                    },
                )
            },
        )
        return Json.encodeToString(JsonObject.serializer(), signed)
    }

    @Test
    fun manuallySignedMessageWithCleanHeaderVerifies() = runBlocking {
        // Control for the header-hygiene tests: the forge helper produces an otherwise-valid JWS.
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "control")
        val forged = forgeSignedWithHeader(
            message,
            """{"alg":"EdDSA","typ":"JWS","kid":"$aliceVm"}""",
        )

        val result = packerWith().unpackToResult(forged, bobDid, bobVm, senderDid = aliceDid, requireSigned = true)
        assertEquals(aliceDid, result.verifiedSignerDid)
    }

    @Test
    fun critHeaderIsRejected() = runBlocking {
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "crit")
        val forged = forgeSignedWithHeader(
            message,
            """{"alg":"EdDSA","typ":"JWS","kid":"$aliceVm","crit":["b64"],"b64":true}""",
        )

        val ex = assertFailsWith<DidCommException.UnpackingFailed> {
            packerWith().unpack(forged, bobDid, bobVm, senderDid = aliceDid)
        }
        assertTrue(ex.reason.contains("crit"), "Unexpected reason: ${ex.reason}")
    }

    @Test
    fun b64FalseIsRejected() = runBlocking {
        val message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, "b64")
        val forged = forgeSignedWithHeader(
            message,
            """{"alg":"EdDSA","typ":"JWS","kid":"$aliceVm","b64":false}""",
        )

        val ex = assertFailsWith<DidCommException.UnpackingFailed> {
            packerWith().unpack(forged, bobDid, bobVm, senderDid = aliceDid)
        }
        assertTrue(ex.reason.contains("b64"), "Unexpected reason: ${ex.reason}")
    }
}
