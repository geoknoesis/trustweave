package org.trustweave.credential.didcomm.packing

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import org.didcommx.didcomm.DIDComm
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.model.PackEncryptedParams
import org.didcommx.didcomm.secret.Secret
import org.trustweave.credential.didcomm.DidCommFactory
import org.trustweave.credential.didcomm.crypto.interop.BlockingDidDocResolver
import org.trustweave.credential.didcomm.crypto.interop.DidCommMessageBuilder
import org.trustweave.credential.didcomm.crypto.interop.MapSecretResolver
import org.trustweave.credential.didcomm.exception.DidCommException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that `requireSigned` and the expected-sender binding are enforced against the
 * CRYPTOGRAPHIC sender of an encrypted envelope, never against the attacker-controllable
 * plaintext `from` header:
 *
 * - AuthCrypt (ECDH-1PU) authenticates the sender; it satisfies `requireSigned` and surfaces
 *   the authenticated sender DID.
 * - AnonCrypt (ECDH-ES) can be produced by ANYONE holding only the recipient's public key,
 *   with an arbitrary inner `from`. It must never satisfy `requireSigned` or an expected-sender
 *   claim, no matter what the plaintext `from` says.
 */
class DidCommPackerEncryptedAuthTest {

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
                    publicKeyJwk = mapOf(
                        "kty" to kp.keyType.value,
                        "crv" to kp.curve.name,
                        "x" to kp.x.toString(),
                    ),
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

    private fun packerWith(vararg entries: Pair<String, OctetKeyPair>): DidCommPacker {
        val secrets = MapSecretResolver()
        entries.forEach { (vm, kp) -> secrets.put(vm, secretFor(vm, kp)) }
        return DidCommFactory.createPacker(kms, resolveDid, secrets)
    }

    /** Sender-side packer: alice holds her own key-agreement secret (needed for AuthCrypt). */
    private fun alicePacker() = packerWith(aliceVm to aliceKp)

    /** Recipient-side packer: bob holds only his own secret — like a real recipient. */
    private fun bobPacker() = packerWith(bobVm to bobKp)

    private suspend fun packAuthCrypt(content: String): String =
        alicePacker().pack(
            message = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, content),
            fromDid = aliceDid,
            fromKeyId = aliceVm,
            toDid = bobDid,
            toKeyId = bobVm,
            encrypt = true,
            sign = false,
        )

    /**
     * Forges an AnonCrypt envelope the way a real attacker would: with NO private keys at all,
     * only bob's public DID document, and an inner plaintext `from` claiming to be alice.
     */
    private fun forgeAnonCryptFromAlice(content: String): String {
        val attackerDidComm = DIDComm(
            BlockingDidDocResolver(suspendResolve = resolveDid),
            MapSecretResolver(), // empty: the attacker holds no secrets whatsoever
        )
        val inner = BasicMessageProtocol.createBasicMessage(aliceDid, bobDid, content)
        val didMessage = DidCommMessageBuilder.fromJsonObject(inner.toJsonObject(), aliceDid, bobDid)
        val params = PackEncryptedParams.builder(didMessage, bobDid)
            .forward(false)
            .build() // no .from(...) => AnonCrypt (ECDH-ES), sender NOT authenticated
        return attackerDidComm.packEncrypted(params).packedMessage
    }

    // --- AuthCrypt: authenticated sender satisfies requireSigned ------------------------------

    @Test
    fun authCryptWithExpectedSenderAndRequireSignedIsAccepted() = runBlocking {
        val packed = packAuthCrypt("authenticated hello")

        val result = bobPacker().unpackToResult(
            packedMessage = packed,
            recipientDid = bobDid,
            recipientKeyId = bobVm,
            senderDid = aliceDid,
            requireSigned = true,
        )

        assertEquals("authenticated hello", result.message.body["content"]?.jsonPrimitive?.content)
        assertEquals(aliceDid, result.authenticatedSenderDid)
        // No nested JWS was carried: envelope-level authentication, not signature verification.
        assertTrue(result.verifiedSignerDids.isEmpty())
    }

    @Test
    fun authCryptWithoutExplicitSenderExpectationAuthenticatesViaSkid() = runBlocking {
        val packed = packAuthCrypt("skid-derived sender")

        val result = bobPacker().unpackToResult(
            packedMessage = packed,
            recipientDid = bobDid,
            recipientKeyId = bobVm,
            senderDid = null,
            requireSigned = true,
        )

        assertEquals(aliceDid, result.authenticatedSenderDid)
    }

    // --- AnonCrypt forgery: plaintext `from` must never authenticate --------------------------

    @Test
    fun anonCryptForgedFromWithRequireSignedIsRejected() = runBlocking {
        // Attacker forges an anoncrypt envelope whose inner `from` claims to be alice.
        val forged = forgeAnonCryptFromAlice("forged as alice")

        val ex = assertFailsWith<DidCommException.UnpackingFailed> {
            bobPacker().unpack(
                packedMessage = forged,
                recipientDid = bobDid,
                recipientKeyId = bobVm,
                senderDid = aliceDid,
                requireSigned = true,
            )
        }
        assertTrue(
            ex.reason.contains("anon", ignoreCase = true),
            "Rejection must name anonymous encryption, got: ${ex.reason}",
        )
    }

    @Test
    fun anonCryptForgedFromWithSenderExpectationIsRejectedEvenWithoutRequireSigned() = runBlocking {
        // Root cause fix: the expected-sender binding itself must be cryptographic, so the
        // forgery is rejected even when the caller did not ask for requireSigned.
        val forged = forgeAnonCryptFromAlice("forged as alice")

        val ex = assertFailsWith<DidCommException.UnpackingFailed> {
            bobPacker().unpack(
                packedMessage = forged,
                recipientDid = bobDid,
                recipientKeyId = bobVm,
                senderDid = aliceDid,
                requireSigned = false,
            )
        }
        assertTrue(
            ex.reason.contains("anon", ignoreCase = true),
            "Rejection must name anonymous encryption, got: ${ex.reason}",
        )
    }

    @Test
    fun anonCryptWithRequireSignedAndNoSenderExpectationIsRejected() = runBlocking {
        // Even without an expected sender, requireSigned can never be satisfied anonymously.
        val forged = forgeAnonCryptFromAlice("anonymous but signature required")

        val ex = assertFailsWith<DidCommException.UnpackingFailed> {
            bobPacker().unpack(
                packedMessage = forged,
                recipientDid = bobDid,
                recipientKeyId = bobVm,
                senderDid = null,
                requireSigned = true,
            )
        }
        assertTrue(
            ex.reason.contains("signature required", ignoreCase = true),
            "Unexpected reason: ${ex.reason}",
        )
    }

    @Test
    fun anonCryptWithoutExpectationsUnpacksAnonymously() = runBlocking {
        // Documented semantics: with no sender expectation and no requireSigned, an anoncrypt
        // envelope still unpacks — but it carries NO authenticated sender. The plaintext `from`
        // is a claim only; callers must treat it as unverified.
        val packed = forgeAnonCryptFromAlice("anonymous tip-off")

        val result = bobPacker().unpackToResult(
            packedMessage = packed,
            recipientDid = bobDid,
            recipientKeyId = bobVm,
            senderDid = null,
            requireSigned = false,
        )

        assertEquals("anonymous tip-off", result.message.body["content"]?.jsonPrimitive?.content)
        assertNull(result.authenticatedSenderDid)
        assertTrue(result.verifiedSignerDids.isEmpty())
    }

    // --- AuthCrypt with the wrong expected sender ----------------------------------------------

    @Test
    fun authCryptSenderMismatchAgainstEncryptedFromIsRejected() = runBlocking {
        // Genuine authcrypt from alice, but bob expects charlie: the binding is checked against
        // the cryptographic sender (encryptedFrom), and must fail.
        val packed = packAuthCrypt("from alice, not charlie")

        val ex = assertFailsWith<DidCommException.UnpackingFailed> {
            bobPacker().unpack(
                packedMessage = packed,
                recipientDid = bobDid,
                recipientKeyId = bobVm,
                senderDid = charlieDid,
                requireSigned = false,
            )
        }
        assertTrue(
            ex.reason.contains(charlieDid) && ex.reason.contains(aliceDid),
            "Mismatch must name both DIDs, got: ${ex.reason}",
        )
    }
}
